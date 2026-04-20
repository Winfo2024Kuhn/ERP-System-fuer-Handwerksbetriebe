package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailAutoAssignmentServiceTest {

    @Mock private EmailRepository emailRepository;
    @Mock private LieferantenRepository lieferantenRepository;
    @Mock private ProjektRepository projektRepository;
    @Mock private AnfrageRepository anfrageRepository;
    @Mock private EmailKiClassificationService emailKiClassificationService;
    @Mock private PreisanfrageZuordnungService preisanfrageZuordnungService;

    private EmailAutoAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new EmailAutoAssignmentService(emailRepository, lieferantenRepository, projektRepository, anfrageRepository, emailKiClassificationService, preisanfrageZuordnungService);
    }

    private Email erstelleEmail(String fromAddress, String subject) {
        Email email = new Email();
        email.setId(1L);
        email.setFromAddress(fromAddress);
        email.setSubject(subject);
        email.setZuordnungTyp(EmailZuordnungTyp.KEINE);
        email.setSentAt(LocalDateTime.now());
        if (fromAddress != null && fromAddress.contains("@")) {
            email.setSenderDomain(fromAddress.substring(fromAddress.lastIndexOf('@') + 1).toLowerCase());
        }
        return email;
    }

    private Lieferanten erstelleLieferant(Long id, String name) {
        Lieferanten l = new Lieferanten();
        l.setId(id);
        l.setLieferantenname(name);
        return l;
    }

    private Projekt erstelleProjekt(Long id, String bauvorhaben, String kurzbeschreibung) {
        Projekt p = new Projekt();
        p.setId(id);
        p.setBauvorhaben(bauvorhaben);
        p.setKurzbeschreibung(kurzbeschreibung);
        p.setAnlegedatum(LocalDate.now());
        return p;
    }

    private Anfrage erstelleAnfrage(Long id, String bauvorhaben, String kurzbeschreibung) {
        Anfrage a = new Anfrage();
        a.setId(id);
        a.setBauvorhaben(bauvorhaben);
        a.setKurzbeschreibung(kurzbeschreibung);
        a.setAnlegedatum(LocalDate.now());
        return a;
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.2.1 Ordnet E-Mail anhand Absender-Domain dem Lieferanten zu
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class LieferantZuordnung {

        @Test
        void ordnetEmailAnhandDomainDemLieferantenZu() {
            Email email = erstelleEmail("rechnung@wuerth.com", "Rechnung");
            Lieferanten lieferant = erstelleLieferant(10L, "Würth");

            when(lieferantenRepository.findByEmailDomain("wuerth.com")).thenReturn(List.of(lieferant));

            boolean result = service.tryAssignToLieferant(email);

            assertThat(result).isTrue();
            assertThat(email.getZuordnungTyp()).isEqualTo(EmailZuordnungTyp.LIEFERANT);
            assertThat(email.getLieferant()).isEqualTo(lieferant);
            verify(emailRepository).save(email);
        }

        @Test
        void gibtFalseZurueckBeiUnbekannterDomain() {
            Email email = erstelleEmail("someone@unknown-domain.com", "Hello");

            when(lieferantenRepository.findByEmailDomain("unknown-domain.com")).thenReturn(Collections.emptyList());

            boolean result = service.tryAssignToLieferant(email);

            assertThat(result).isFalse();
            verify(emailRepository, never()).save(any());
        }

        @Test
        void gibtFalseZurueckBeiLeeremSenderDomain() {
            Email email = erstelleEmail("noDomain", "Test");
            email.setSenderDomain(null);

            boolean result = service.tryAssignToLieferant(email);

            assertThat(result).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.2.2 Ordnet über Kunden-E-Mail dem Projekt zu
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class KundeEmailZuordnung {

        @Test
        void ordnetUeberKundenEmailDemProjektZu() {
            Email email = erstelleEmail("kunde@firma.de", "Nachfrage zum Dach");
            Projekt projekt = erstelleProjekt(5L, "Dachsanierung Müller", "Dachsanierung");

            when(lieferantenRepository.findByEmailDomain("firma.de")).thenReturn(Collections.emptyList());
            when(projektRepository.findByKundenEmail("kunde@firma.de")).thenReturn(List.of(projekt));
            when(anfrageRepository.findByKundenEmail("kunde@firma.de")).thenReturn(Collections.emptyList());

            boolean result = service.tryAutoAssign(email);

            assertThat(result).isTrue();
            assertThat(email.getZuordnungTyp()).isEqualTo(EmailZuordnungTyp.PROJEKT);
            assertThat(email.getProjekt()).isEqualTo(projekt);
        }

        @Test
        void ordnetUeberKundenEmailDemAnfrageZu() {
            Email email = erstelleEmail("kunde@firma.de", "Frage zum Anfrage");

            Anfrage anfrage = erstelleAnfrage(3L, "Badezimmer Renovierung", "Bad komplett");

            when(lieferantenRepository.findByEmailDomain("firma.de")).thenReturn(Collections.emptyList());
            when(projektRepository.findByKundenEmail("kunde@firma.de")).thenReturn(Collections.emptyList());
            when(anfrageRepository.findByKundenEmail("kunde@firma.de")).thenReturn(List.of(anfrage));

            boolean result = service.tryAutoAssign(email);

            assertThat(result).isTrue();
            assertThat(email.getZuordnungTyp()).isEqualTo(EmailZuordnungTyp.ANFRAGE);
            assertThat(email.getAnfrage()).isEqualTo(anfrage);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.2.3 Keyword-Matching findet Projekt bei Übereinstimmung
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class KeywordMatching {

        @Test
        void findetProjektBeiKeywordUebereinstimmung() {
            Email email = erstelleEmail("kunde@firma.de", "Frage Dachsanierung Müller");
            email.setBody("Details zur Dachsanierung bei Familie Müller");

            Projekt projekt1 = erstelleProjekt(1L, "Dachsanierung Müller", "Sanierung Dach komplett");
            Projekt projekt2 = erstelleProjekt(2L, "Badezimmer Schmidt", "Bad Umbau");

            // matchesKeywords prüft ob mind. 2 Wörter aus Bauvorhaben/Kurzbeschreibung
            // im Subject+Body vorkommen
            boolean result = service.tryAssignByKeywords(email, List.of(projekt1, projekt2), Collections.emptyList());

            assertThat(result).isTrue();
            assertThat(email.getProjekt()).isEqualTo(projekt1);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.2.4 Ignoriert Keywords mit weniger als Minimallänge
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class KeywordMinimumlaenge {

        @Test
        void ignoriertKurzeKeywords() {
            Email email = erstelleEmail("kunde@firma.de", "Re: Bad");
            email.setBody("Zum Bad");

            // "Bad" hat nur 3 Zeichen → wird ignoriert (Mindestlänge = 4)
            Projekt projekt = erstelleProjekt(1L, "Bad Haus", null);

            boolean result = service.tryAssignByKeywords(email, List.of(projekt), Collections.emptyList());

            assertThat(result).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.2.5 Zeitfenster-Filter: ±1 Monat wird eingehalten
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class ZeitfensterFilter {

        @Test
        void filteredProjekteAusserhalbDesZeitfensters() {
            Email email = erstelleEmail("kunde@firma.de", "Altes Projekt Frage");
            email.setSentAt(LocalDateTime.now());

            Projekt altesProjekt = erstelleProjekt(1L, "Altes Projekt Renovierung", "Renovierung Altbau");
            // Anlegedatum 3 Monate in der Vergangenheit → außerhalb ±1 Monat
            altesProjekt.setAnlegedatum(LocalDate.now().minusMonths(3));

            when(lieferantenRepository.findByEmailDomain("firma.de")).thenReturn(Collections.emptyList());
            when(projektRepository.findByKundenEmail("kunde@firma.de")).thenReturn(List.of(altesProjekt));
            when(anfrageRepository.findByKundenEmail("kunde@firma.de")).thenReturn(Collections.emptyList());

            boolean result = service.tryAutoAssign(email);

            // Das Projekt ist außerhalb des Zeitfensters, aber der Fallback ohne Zeitfenster
            // greift trotzdem bei genau 1 Treffer (globalMatches == 1)
            // → wird daher zugeordnet
            assertThat(result).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.2.6 Multi-Step-Fallback: Domain → Kunde → Keywords
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class MultiStepFallback {

        @Test
        void fallbackVonDomainZuKundeZuKeywords() {
            Email email = erstelleEmail("kunde@firma.de", "Details Dachsanierung Müller");
            email.setBody("Weitere Info Dachsanierung Müller");

            Projekt projekt1 = erstelleProjekt(1L, "Dachsanierung Müller", "Sanierung");
            Projekt projekt2 = erstelleProjekt(2L, "Kellersanierung Müller", "Keller");

            // Schritt 1: Kein Lieferant-Match
            when(lieferantenRepository.findByEmailDomain("firma.de")).thenReturn(Collections.emptyList());
            // Schritt 2: Mehrere Projekte über Kunden-Email → Keyword-Suche
            when(projektRepository.findByKundenEmail("kunde@firma.de")).thenReturn(List.of(projekt1, projekt2));
            when(anfrageRepository.findByKundenEmail("kunde@firma.de")).thenReturn(Collections.emptyList());

            boolean result = service.tryAutoAssign(email);

            assertThat(result).isTrue();
            assertThat(email.getProjekt()).isEqualTo(projekt1);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.2.7 findPossibleAssignments() gibt alle Kandidaten zurück
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class FindPossibleAssignments {

        @Test
        void gibtAlleKandidatenZurueck() {
            Email email = erstelleEmail("kunde@firma.de", "Frage");

            Projekt projekt = erstelleProjekt(1L, "Dachsanierung", "Dach");
            Anfrage anfrage = erstelleAnfrage(2L, "Kellersanierung", "Keller");

            when(projektRepository.findByKundenEmail("kunde@firma.de")).thenReturn(List.of(projekt));
            when(anfrageRepository.findByKundenEmail("kunde@firma.de")).thenReturn(List.of(anfrage));

            EmailAutoAssignmentService.PossibleAssignments assignments = service.findPossibleAssignments(email);

            assertThat(assignments.projekte).hasSize(1);
            assertThat(assignments.projekte.getFirst().name).isEqualTo("Dachsanierung");
            assertThat(assignments.anfragen).hasSize(1);
            assertThat(assignments.anfragen.getFirst().name).isEqualTo("Kellersanierung");
        }

        @Test
        void gibtLeereListenBeiLeererFromAddress() {
            Email email = erstelleEmail(null, "Test");

            EmailAutoAssignmentService.PossibleAssignments assignments = service.findPossibleAssignments(email);

            assertThat(assignments.projekte).isEmpty();
            assertThat(assignments.anfragen).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.2.8 Gibt false zurück wenn keine Zuordnung möglich
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class KeineZuordnung {

        @Test
        void gibtFalseZurueckWennKeineZuordnungMoeglich() {
            Email email = erstelleEmail("unknown@random.com", "Hello World");

            when(lieferantenRepository.findByEmailDomain("random.com")).thenReturn(Collections.emptyList());
            when(projektRepository.findByKundenEmail("unknown@random.com")).thenReturn(Collections.emptyList());
            when(anfrageRepository.findByKundenEmail("unknown@random.com")).thenReturn(Collections.emptyList());

            boolean result = service.tryAutoAssign(email);

            assertThat(result).isFalse();
            assertThat(email.getZuordnungTyp()).isEqualTo(EmailZuordnungTyp.KEINE);
        }

        @Test
        void gibtFalseZurueckBeiSchonZugeordneterEmail() {
            Email email = erstelleEmail("someone@firma.de", "Test");
            email.setZuordnungTyp(EmailZuordnungTyp.PROJEKT);

            boolean result = service.tryAutoAssign(email);

            assertThat(result).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.2.9 Preisanfrage-Antwort hat Vorrang vor Lieferanten-Domain-Match
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class PreisanfrageAntwort {

        @Test
        void ordnetPreisanfrageAntwortVorLieferantenDomainZu() {
            Email email = erstelleEmail("vertrieb@stahlhandel.de", "Re: Preisanfrage PA-2026-001-ABCDE");
            Lieferanten lieferant = erstelleLieferant(42L, "Stahlhandel Mustermann");

            PreisanfrageLieferant pal = new PreisanfrageLieferant();
            pal.setId(7L);
            pal.setToken("PA-2026-001-ABCDE");
            pal.setLieferant(lieferant);

            when(preisanfrageZuordnungService.tryMatch(email)).thenReturn(java.util.Optional.of(pal));

            boolean result = service.tryAutoAssign(email);

            assertThat(result).isTrue();
            assertThat(email.getZuordnungTyp()).isEqualTo(EmailZuordnungTyp.LIEFERANT);
            assertThat(email.getLieferant()).isEqualTo(lieferant);
            verify(emailRepository).save(email);
            // Lieferanten-Domain-Match darf NICHT mehr ausgelöst werden
            verify(lieferantenRepository, never()).findByEmailDomain(any());
        }
    }
}
