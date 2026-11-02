package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AnfrageNotiz;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/** Webseiten-Anfragen in der Glocke: nur solange wir dem Kunden noch nicht geantwortet haben. */
@DataJpaTest
class AnfrageRepositoryFunnelTest {

    private static final String SYSTEM_TOKEN = "webseite-system";

    @Autowired private AnfrageRepository anfrageRepository;
    @Autowired private MitarbeiterRepository mitarbeiterRepository;
    @Autowired private EmailRepository emailRepository;
    @Autowired private AusgangsGeschaeftsDokumentRepository dokumentRepository;

    private Mitarbeiter system;
    private Mitarbeiter mensch;

    @BeforeEach
    void mitarbeiter() {
        system = mitarbeiter(SYSTEM_TOKEN);
        mensch = mitarbeiter("max-mustermann");
    }

    @Test
    @DisplayName("Ohne Antwort bleibt die Anfrage offen; eigene Mail oder verschicktes Dokument beantworten sie")
    void nurUnbeantworteteFunnelAnfragen() {
        Anfrage ohneAntwort = anfrage("Ohne Antwort", system);
        Anfrage nurBestaetigung = anfrage("Nur Eingangsbestätigung", system);
        mail(nurBestaetigung, EmailDirection.OUT, true);
        Anfrage kundeSchriebNach = anfrage("Kunde schrieb nach", system);
        mail(kundeSchriebNach, EmailDirection.IN, false);
        Anfrage dokumentNurAngelegt = anfrage("Angebot nur angelegt", system);
        dokument(dokumentNurAngelegt, "AN-1", null);
        Anfrage antwortUnzustellbar = anfrage("Antwort unzustellbar", system);
        mail(antwortUnzustellbar, EmailDirection.OUT, false).markiereUnzustellbar("Postfach unbekannt");

        Anfrage perMailBeantwortet = anfrage("Per Mail beantwortet", system);
        mail(perMailBeantwortet, EmailDirection.OUT, false);
        Anfrage angebotVerschickt = anfrage("Angebot verschickt", system);
        dokument(angebotVerschickt, "AN-2", LocalDate.of(2026, 10, 20));
        Anfrage abgeschlossen = anfrage("Abgeschlossen", system);
        abgeschlossen.setAbgeschlossen(true);
        anfrage("Von Hand angelegt", mensch);
        anfrageRepository.flush();

        assertThat(anfrageRepository.findUnbeantworteteFunnelAnfragen(SYSTEM_TOKEN))
                .extracting(Anfrage::getBauvorhaben)
                .containsExactlyInAnyOrder("Ohne Antwort", "Nur Eingangsbestätigung",
                        "Kunde schrieb nach", "Angebot nur angelegt", "Antwort unzustellbar");
        assertThat(anfrageRepository.findUnbeantworteteFunnelAnfragen("'; DROP TABLE anfrage; --")).isEmpty();
    }

    private Mitarbeiter mitarbeiter(String token) {
        Mitarbeiter m = new Mitarbeiter();
        m.setVorname("Max");
        m.setNachname("Mustermann");
        m.setLoginToken(token);
        return mitarbeiterRepository.saveAndFlush(m);
    }

    private Anfrage anfrage(String bauvorhaben, Mitarbeiter verfasser) {
        Anfrage a = new Anfrage();
        a.setBauvorhaben(bauvorhaben);
        AnfrageNotiz notiz = new AnfrageNotiz();
        notiz.setAnfrage(a);
        notiz.setMitarbeiter(verfasser);
        notiz.setNotiz("Anfrage über die Webseite");
        notiz.setErstelltAm(LocalDateTime.of(2026, 10, 1, 8, 0));
        a.getNotizen().add(notiz);
        return anfrageRepository.save(a);
    }

    private Email mail(Anfrage anfrage, EmailDirection richtung, boolean automatischeBestaetigung) {
        Email e = new Email();
        e.setMessageId("<" + anfrage.getBauvorhaben().hashCode() + richtung + "@example.org>");
        e.setSubject("Ihre Anfrage");
        e.setFromAddress("max.mustermann@example.org");
        e.setDirection(richtung);
        e.setAutomatischeBestaetigung(automatischeBestaetigung);
        e.setSentAt(LocalDateTime.of(2026, 10, 2, 8, 0));
        e.assignToAnfrage(anfrage);
        return emailRepository.save(e);
    }

    private void dokument(Anfrage anfrage, String nummer, LocalDate versandDatum) {
        AusgangsGeschaeftsDokument d = new AusgangsGeschaeftsDokument();
        d.setDokumentNummer(nummer);
        d.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
        d.setDatum(LocalDate.of(2026, 10, 2));
        d.setAnfrage(anfrage);
        d.setVersandDatum(versandDatum);
        dokumentRepository.save(d);
    }
}
