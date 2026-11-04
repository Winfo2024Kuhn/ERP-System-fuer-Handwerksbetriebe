package org.example.kalkulationsprogramm.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.repository.EmailAttachmentRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import jakarta.persistence.EntityManager;

/**
 * Der Suchtext wird beim Speichern gepflegt – für jede neue oder geänderte Mail, egal über welchen
 * Weg (Import, Versand, Archiv). H2 genügt: es geht um die JPA-Callbacks, nicht um SQL.
 */
@DataJpaTest
class EmailSuchtextPflegeJpaTest {

    @Autowired private EmailRepository emailRepository;
    @Autowired private EmailAttachmentRepository attachmentRepository;
    @Autowired private EntityManager em;

    private static int zaehler;

    private static Email mail() {
        Email e = new Email();
        e.setMessageId("<suchtext-" + (++zaehler) + "@example.com>");
        e.setDirection(EmailDirection.IN);
        e.setFromAddress("Max Mustermann <max@example.com>");
        e.setRecipient("info@example.com");
        e.setSubject("Angebot Geländer");
        e.setBody("Hallo, anbei das Aufmaß.");
        e.setSentAt(LocalDateTime.of(2026, 10, 1, 8, 0));
        return e;
    }

    private String suchtextAusDb(Long id) {
        em.flush();
        em.clear();
        return emailRepository.findById(id).orElseThrow().getSuchtext();
    }

    @Test
    @DisplayName("Neue Mail bekommt den Suchtext samt Anhang-Dateinamen")
    void neueMail() {
        Email e = mail();
        EmailAttachment anhang = new EmailAttachment();
        anhang.setOriginalFilename("Skizze_Treppe.pdf");
        e.addAttachment(anhang);

        Long id = emailRepository.save(e).getId();

        assertThat(suchtextAusDb(id)).contains("max mustermann <max@example.com>").contains("angebot gelander")
                .contains("aufmass").contains("skizze_treppe.pdf");
    }

    @Test
    @DisplayName("Anhang, der nach dem Speichern dazukommt (wie beim IMAP-Import), steht danach im Suchtext")
    void anhangNachtraeglich() {
        Email e = emailRepository.save(mail());
        EmailAttachment anhang = new EmailAttachment();
        anhang.setOriginalFilename("Rechnung_4711.pdf");
        e.addAttachment(anhang);
        attachmentRepository.save(anhang);

        assertThat(suchtextAusDb(e.getId())).contains("rechnung_4711.pdf").contains("angebot gelander");
    }

    @Test
    @DisplayName("Geänderter Betreff wird nachgezogen (Anhänge geladen)")
    void geaenderterBetreff() {
        Long id = emailRepository.save(mail()).getId();
        em.flush();
        em.clear();

        Email geladen = emailRepository.findById(id).orElseThrow();
        assertThat(geladen.getAttachments()).isEmpty();
        geladen.setSubject("Rechnung Treppe");
        emailRepository.save(geladen);

        assertThat(suchtextAusDb(id)).contains("rechnung treppe").doesNotContain("angebot gelander");
    }

    @Test
    @DisplayName("Nur Papierkorb/Stern geändert: Suchtext bleibt, Anhänge werden dafür nicht nachgeladen")
    void andereFelderLassenSuchtextStehen() {
        Email e = mail();
        EmailAttachment anhang = new EmailAttachment();
        anhang.setOriginalFilename("Plan.pdf");
        e.addAttachment(anhang);
        Long id = emailRepository.save(e).getId();
        em.flush();
        em.clear();

        Email geladen = emailRepository.findById(id).orElseThrow();
        geladen.setDeletedAt(LocalDateTime.now());
        geladen.setStarred(true);
        emailRepository.save(geladen);
        em.flush();

        assertThat(org.hibernate.Hibernate.isInitialized(geladen.getAttachments())).isFalse();
        assertThat(suchtextAusDb(id)).contains("plan.pdf");
    }

    @Test
    @DisplayName("Text geändert, Anhänge nicht geladen: Suchtext wird geleert, die Nachpflege füllt ihn neu")
    void textGeaendertOhneGeladeneAnhaenge() {
        Email e = mail();
        EmailAttachment anhang = new EmailAttachment();
        anhang.setOriginalFilename("Plan.pdf");
        e.addAttachment(anhang);
        Long id = emailRepository.save(e).getId();
        em.flush();
        em.clear();

        Email geladen = emailRepository.findById(id).orElseThrow();
        geladen.setBody("Neuer Text");
        emailRepository.save(geladen);

        assertThat(suchtextAusDb(id)).isNull();
    }
}
