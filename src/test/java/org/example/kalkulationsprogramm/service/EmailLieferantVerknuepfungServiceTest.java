package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests fuer die zusaetzliche Lieferanten-Verknuepfung beim Versand.
 *
 * <p>Fachlicher Hintergrund: Eine E-Mail gehoert zu genau einem Vorgang
 * (Projekt ODER Anfrage). Geht sie an einen Lieferanten, soll sie trotzdem auch
 * auf dessen Karte auftauchen – der Lieferanten-Verweis kommt deshalb zusaetzlich
 * dazu, ohne die Haupt-Zuordnung zu ueberschreiben.
 *
 * <p>Alle Daten sind Dummy-Daten (DSGVO).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EmailLieferantVerknuepfungService")
class EmailLieferantVerknuepfungServiceTest {

    private static final long LIEFERANT_ID = 5L;
    private static final String LIEFERANT_ADRESSE = "info@musterlieferant.example";

    @Mock
    private LieferantEmailResolver lieferantEmailResolver;

    @Mock
    private LieferantenRepository lieferantenRepository;

    @InjectMocks
    private EmailLieferantVerknuepfungService service;

    private Lieferanten lieferant;

    @BeforeEach
    void setUp() {
        lieferant = new Lieferanten();
        lieferant.setId(LIEFERANT_ID);
        lieferant.setLieferantenname("Muster Lieferant GmbH");

        when(lieferantEmailResolver.resolve(anyCollection())).thenAnswer(aufruf -> {
            Collection<?> adressen = aufruf.getArgument(0);
            return adressen.stream().anyMatch(a -> LIEFERANT_ADRESSE.equalsIgnoreCase(String.valueOf(a)))
                    ? Optional.of(LIEFERANT_ID)
                    : Optional.empty();
        });
        when(lieferantenRepository.findById(LIEFERANT_ID)).thenReturn(Optional.of(lieferant));
    }

    @Test
    @DisplayName("Projekt-Mail an einen Lieferanten behaelt die Projekt-Zuordnung und bekommt den Lieferanten dazu")
    void projektMailAnLieferant_behaeltProjektUndVerknuepftLieferant() {
        Projekt projekt = new Projekt();
        projekt.setId(11L);
        Email email = new Email();
        email.assignToProjekt(projekt);

        service.verknuepfeAusEmpfaenger(email, "\"Muster Lieferant\" <" + LIEFERANT_ADRESSE + ">", null);

        assertThat(email.getZuordnungTyp()).isEqualTo(EmailZuordnungTyp.PROJEKT);
        assertThat(email.getProjekt()).isSameAs(projekt);
        assertThat(email.getLieferant()).isSameAs(lieferant);
    }

    @Test
    @DisplayName("Anfrage-Mail an einen Lieferanten behaelt die Anfrage-Zuordnung")
    void anfrageMailAnLieferant_behaeltAnfrage() {
        Anfrage anfrage = new Anfrage();
        anfrage.setId(22L);
        Email email = new Email();
        email.assignToAnfrage(anfrage);

        service.verknuepfeAusEmpfaenger(email, LIEFERANT_ADRESSE);

        assertThat(email.getZuordnungTyp()).isEqualTo(EmailZuordnungTyp.ANFRAGE);
        assertThat(email.getAnfrage()).isSameAs(anfrage);
        assertThat(email.getLieferant()).isSameAs(lieferant);
    }

    @Test
    @DisplayName("Treffer im CC-Feld reicht ebenfalls")
    void trefferImCc_wirdVerknuepft() {
        Projekt projekt = new Projekt();
        projekt.setId(11L);
        Email email = new Email();
        email.assignToProjekt(projekt);

        service.verknuepfeAusEmpfaenger(email, "max.mustermann@example.com", LIEFERANT_ADRESSE);

        assertThat(email.getLieferant()).isSameAs(lieferant);
    }

    @Test
    @DisplayName("Ohne Vorgang wird der Lieferant zur regulaeren Zuordnung")
    void ohneVorgang_lieferantWirdZuordnung() {
        Email email = new Email();

        service.verknuepfeAusEmpfaenger(email, "sammel@example.com, " + LIEFERANT_ADRESSE);

        assertThat(email.getZuordnungTyp()).isEqualTo(EmailZuordnungTyp.LIEFERANT);
        assertThat(email.getLieferant()).isSameAs(lieferant);
    }

    @Test
    @DisplayName("Unbekannter Empfaenger aendert nichts")
    void unbekannterEmpfaenger_bleibtOhneLieferant() {
        Projekt projekt = new Projekt();
        projekt.setId(11L);
        Email email = new Email();
        email.assignToProjekt(projekt);

        service.verknuepfeAusEmpfaenger(email, "max.mustermann@example.com");

        assertThat(email.getZuordnungTyp()).isEqualTo(EmailZuordnungTyp.PROJEKT);
        assertThat(email.getLieferant()).isNull();
    }

    @Test
    @DisplayName("Bereits gesetzter Lieferant wird nicht ueberschrieben")
    void bestehenderLieferant_bleibtUnveraendert() {
        Lieferanten anderer = new Lieferanten();
        anderer.setId(99L);
        Email email = new Email();
        email.assignToLieferant(anderer);

        service.verknuepfeAusEmpfaenger(email, LIEFERANT_ADRESSE);

        assertThat(email.getLieferant()).isSameAs(anderer);
        verify(lieferantEmailResolver, never()).resolve(anyCollection());
    }

    @Test
    @DisplayName("Leere oder fehlende Adressfelder loesen keinen Lookup aus")
    void leereAdressfelder_keinLookup() {
        Email email = new Email();

        service.verknuepfeAusEmpfaenger(email);
        service.verknuepfeAusEmpfaenger(email, (String) null);
        service.verknuepfeAusEmpfaenger(email, "   ");
        service.verknuepfeAusEmpfaenger(email, "kein Text mit Adresse");

        assertThat(email.getLieferant()).isNull();
        verify(lieferantEmailResolver, never()).resolve(anyCollection());
    }

    @Test
    @DisplayName("extractEmailAddresses liest alle Adressen aus einem To-Feld")
    void extractEmailAddresses_liestAlleAdressen() {
        assertThat(EmailLieferantVerknuepfungService.extractEmailAddresses(
                "\"Max Mustermann\" <max@example.com>, erika@example.com; dritte@example.org"))
                .containsExactly("max@example.com", "erika@example.com", "dritte@example.org");
        assertThat(EmailLieferantVerknuepfungService.extractEmailAddresses(null)).isEmpty();
        assertThat(EmailLieferantVerknuepfungService.extractEmailAddresses("   ")).isEmpty();
        assertThat(EmailLieferantVerknuepfungService.extractEmailAddresses("kein Text mit Adresse")).isEmpty();
    }

    @Test
    @DisplayName("Ueberlange Eingabe wird gekappt und laeuft nicht in Backtracking")
    void ueberlangeEingabe_wirdGekappt() {
        String boesartig = "a".repeat(20_000) + "!";
        List<String> treffer = EmailLieferantVerknuepfungService.extractEmailAddresses(boesartig);
        assertThat(treffer).isEmpty();
    }
}
