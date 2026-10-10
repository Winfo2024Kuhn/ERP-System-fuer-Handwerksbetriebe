package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.service.BounceErkennungService;
import org.example.kalkulationsprogramm.service.EmailAttachmentProcessingService;
import org.example.kalkulationsprogramm.service.EmailAutoAssignmentService;
import org.example.kalkulationsprogramm.service.EmailImportService;
import org.example.kalkulationsprogramm.service.EmailThreadService;
import org.example.kalkulationsprogramm.service.OutOfOfficeResponder;
import org.example.kalkulationsprogramm.service.SpamFilterService;
import org.example.kalkulationsprogramm.service.SteuerberaterEmailProcessingService;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import jakarta.persistence.EntityManager;

/**
 * Integrationstest gegen eine echte (H2-)Datenbank: Backfill der Thread-Verknüpfungen und
 * Thread-Kennzahlen für die Liste, so wie sie beim Serverstart bzw. im Posteingang laufen.
 * Alle Personen- und Firmendaten sind Dummy-Daten.
 */
@DataJpaTest
@Import({EmailImportService.class, EmailThreadService.class})
class EmailThreadBackfillIntegrationTest {

    @Autowired private EmailImportService importService;
    @Autowired private EmailThreadService threadService;
    @Autowired private EmailRepository emailRepository;
    @Autowired private EntityManager entityManager;

    @MockBean private EmailAutoAssignmentService emailAutoAssignmentService;
    @MockBean private EmailAttachmentProcessingService emailAttachmentProcessingService;
    @MockBean private SpamFilterService spamFilterService;
    @MockBean private SteuerberaterEmailProcessingService steuerberaterEmailProcessingService;
    @MockBean private SystemSettingsService systemSettingsService;
    @MockBean private OutOfOfficeResponder outOfOfficeResponder;
    @MockBean private BounceErkennungService bounceErkennungService;
    @MockBean private org.example.kalkulationsprogramm.service.mail.PostfachZugangService postfachZugangService;
    @MockBean private org.example.kalkulationsprogramm.service.PostfachService postfachService;

    private final LocalDateTime start = LocalDateTime.of(2026, 9, 1, 8, 0);
    private int naechsteId;

    private Email speichere(EmailDirection richtung, String von, String an, String betreff, int stunden, Email parent) {
        Email email = new Email();
        email.setMessageId("<test-" + (++naechsteId) + "@example.com>");
        email.setDirection(richtung);
        email.setFromAddress(von);
        email.setRecipient(an);
        email.setSubject(betreff);
        email.setSentAt(start.plusHours(stunden));
        email.setParentEmail(parent);
        return emailRepository.save(email);
    }

    @Test
    void backfillTrenntLieferantenantwortenUndHaengtSieAnDieGemeinsameAnfrage() {
        Email anfrage = speichere(EmailDirection.OUT, "info@musterbetrieb.example",
                "angebot@lieferant-a.example, verkauf@lieferant-b.example", "Bitte um Angebot", 0, null);
        Email antwortA = speichere(EmailDirection.IN, "angebot@lieferant-a.example", "info@musterbetrieb.example",
                "AW: Bitte um Angebot", 1, anfrage);
        // Altlast: per Betreff an die Antwort eines anderen Lieferanten gehängt.
        Email antwortB = speichere(EmailDirection.IN, "verkauf@lieferant-b.example", "info@musterbetrieb.example",
                "AW: Bitte um Angebot", 2, antwortA);
        // Ohne Leerzeichen nach "AW:" und noch gar nicht verknüpft.
        Email ohneLeerzeichen = speichere(EmailDirection.IN, "angebot@lieferant-a.example", "info@musterbetrieb.example",
                "AW:Bitte um Angebot", 3, null);
        entityManager.flush();
        entityManager.clear();

        // Der Start-Backfill verknüpft nur, er löst nichts.
        importService.backfillParentEmails();
        entityManager.flush();
        entityManager.clear();
        assertThat(emailRepository.findById(antwortB.getId()).orElseThrow().getParentEmail().getId())
                .isEqualTo(antwortA.getId());

        importService.bereinigeThreadVerknuepfungen(false);
        entityManager.flush();
        entityManager.clear();

        Email b = emailRepository.findById(antwortB.getId()).orElseThrow();
        Email nachtrag = emailRepository.findById(ohneLeerzeichen.getId()).orElseThrow();
        assertThat(b.getParentEmail().getId()).isEqualTo(anfrage.getId());
        assertThat(nachtrag.getParentEmail().getId()).isEqualTo(antwortA.getId());

        // Ein zweiter Lauf ändert nichts mehr.
        var zweiter = importService.bereinigeThreadVerknuepfungen(false);
        assertThat(zweiter.getCleared()).isZero();
        assertThat(zweiter.getRelinked()).isZero();
    }

    @Test
    void kennzahlenSindFuerAlleMitgliederGleichAuchUeberLazyLoading() {
        Email anfrage = speichere(EmailDirection.IN, "max@example.com", "info@musterbetrieb.example", "Anfrage Terrasse", 0, null);
        Email antwort = speichere(EmailDirection.OUT, "info@musterbetrieb.example", "max@example.com", "AW: Anfrage Terrasse", 1, anfrage);
        Email nachfrage = speichere(EmailDirection.IN, "max@example.com", "info@musterbetrieb.example", "Re: Anfrage Terrasse", 2, antwort);
        entityManager.flush();
        entityManager.clear();

        for (Long id : new Long[] {anfrage.getId(), antwort.getId(), nachfrage.getId()}) {
            var kennzahlen = threadService.kennzahlenFuer(emailRepository.findById(id).orElseThrow());
            assertThat(kennzahlen.rootId()).isEqualTo(anfrage.getId());
            assertThat(kennzahlen.anzahl()).isEqualTo(3);
            assertThat(kennzahlen.letzteAktivitaet()).isEqualTo(start.plusHours(2));
        }

        var bezug = threadService.antwortBezugFuer(emailRepository.findById(nachfrage.getId()).orElseThrow());
        assertThat(bezug.inReplyTo()).isEqualTo(nachfrage.getMessageId());
        assertThat(bezug.references()).containsExactly(anfrage.getMessageId(), antwort.getMessageId());
    }

    @Test
    void eigeneAdressenKommenAusGesendetenMails() {
        speichere(EmailDirection.OUT, "Info@Musterbetrieb.example", "max@example.com", "Angebot", 0, null);
        speichere(EmailDirection.IN, "max@example.com", "info@musterbetrieb.example", "AW: Angebot", 1, null);

        assertThat(emailRepository.findDistinctFromAddressesByDirection(EmailDirection.OUT))
                .containsExactly("info@musterbetrieb.example");
    }

    @Test
    void backfillLaesstReplyToAntwortenUndKollegenantwortenVerknuepft() {
        Email formular = speichere(EmailDirection.IN, "noreply@formular.example", "info@musterbetrieb.example",
                "Anfrage Geländer", 0, null);
        formular.setReplyToAddress("max@kunde.example");
        Email unsereAntwort = speichere(EmailDirection.OUT, "info@musterbetrieb.example", "max@kunde.example",
                "AW: Anfrage Geländer", 1, formular);
        Email angebot = speichere(EmailDirection.OUT, "info@musterbetrieb.example", "info@kunde.example",
                "Angebot Treppe", 2, null);
        Email kollege = speichere(EmailDirection.IN, "erika@kunde.example", "info@musterbetrieb.example",
                "AW: Angebot Treppe", 3, angebot);
        entityManager.flush();
        entityManager.clear();

        importService.bereinigeThreadVerknuepfungen(false);
        entityManager.flush();
        entityManager.clear();

        assertThat(emailRepository.findById(unsereAntwort.getId()).orElseThrow().getParentEmail().getId())
                .isEqualTo(formular.getId());
        assertThat(emailRepository.findById(kollege.getId()).orElseThrow().getParentEmail().getId())
                .isEqualTo(angebot.getId());
    }
}
