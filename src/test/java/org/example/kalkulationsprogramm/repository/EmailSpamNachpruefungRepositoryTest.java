package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.SteuerberaterKontakt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import jakarta.persistence.EntityManager;

/**
 * Die nachträgliche Spam-Prüfung ({@code findUnanalyzedForSpam}) darf nur Mails liefern,
 * die noch nie bewertet wurden und über die niemand entschieden hat. Spam wird auch auf
 * dem Mailserver verschoben, eine falsche Neubewertung kostet also echte Post.
 * Nur Dummy-Daten.
 */
@DataJpaTest
class EmailSpamNachpruefungRepositoryTest {

    @Autowired private EmailRepository emailRepository;
    @Autowired private EntityManager entityManager;

    private Email eingang(String messageId) {
        Email e = new Email();
        e.setMessageId(messageId);
        e.setFromAddress("max.mustermann@example.com");
        e.setDirection(EmailDirection.IN);
        e.setSentAt(LocalDateTime.of(2026, 10, 31, 8, 0));
        // „Nie bewertet“ = kein Score (Altbestand); neue Mails starten mit 0.
        e.setSpamScore(null);
        return e;
    }

    @Test
    void liefertNurNieBewerteteMailsOhneEntscheidung() {
        Email offen = emailRepository.save(eingang("<offen@example.org>"));

        Email bewertet = eingang("<bewertet@example.org>");
        bewertet.setSpamScore(12);
        emailRepository.save(bewertet);

        Email keinSpamVonHand = eingang("<ham@example.org>");
        keinSpamVonHand.setUserSpamVerdict("HAM_IMPLICIT");
        emailRepository.save(keinSpamVonHand);

        Email inNewsletterVerschoben = eingang("<newsletter@example.org>");
        inNewsletterVerschoben.setNewsletter(true);
        emailRepository.save(inNewsletterVerschoben);

        Email inSpamVerschoben = eingang("<spam@example.org>");
        inSpamVerschoben.setSpam(true);
        emailRepository.save(inSpamVerschoben);

        SteuerberaterKontakt kanzlei = new SteuerberaterKontakt();
        kanzlei.setName("Mustermann Steuerberatung");
        kanzlei.setEmail("kanzlei@example.com");
        entityManager.persist(kanzlei);
        Email lohnabrechnung = eingang("<lohn@example.org>");
        lohnabrechnung.assignToSteuerberater(kanzlei);
        emailRepository.save(lohnabrechnung);

        Email ausgang = eingang("<ausgang@example.org>");
        ausgang.setDirection(EmailDirection.OUT);
        emailRepository.save(ausgang);
        entityManager.flush();

        assertThat(emailRepository.findUnanalyzedForSpam())
                .extracting(Email::getId)
                .containsExactly(offen.getId());
    }
}
