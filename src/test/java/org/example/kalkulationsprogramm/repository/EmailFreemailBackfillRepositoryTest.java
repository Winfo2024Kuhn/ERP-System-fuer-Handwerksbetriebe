package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import jakarta.persistence.EntityManager;

/**
 * Lieferanten-Backfill bei Freemail-Adressen (Issue #266): Die Abfragen nach exakter
 * Absender-Adresse dürfen keine fremden Mails derselben Freemail-Domain liefern.
 * Nur Dummy-Daten.
 */
@DataJpaTest
class EmailFreemailBackfillRepositoryTest {

    @Autowired private EmailRepository emailRepository;
    @Autowired private EntityManager entityManager;

    private Email mail(String messageId, String absender, EmailDirection richtung) {
        Email e = new Email();
        e.setMessageId(messageId);
        e.setFromAddress(absender);
        e.setDirection(richtung);
        e.setSentAt(LocalDateTime.of(2026, 10, 12, 8, 0));
        return emailRepository.save(e);
    }

    @Test
    void exakteAdresseLiefertKeineFremdenMailsDerselbenDomain() {
        Email eigene = mail("<eigene@example.org>", "Max.Mustermann@gmail.com", EmailDirection.IN);
        mail("<fremd@example.org>", "erika.musterfrau@gmail.com", EmailDirection.IN);
        mail("<aehnlich@example.org>", "xmax.mustermann@gmail.com", EmailDirection.IN);
        mail("<ausgang@example.org>", "max.mustermann@gmail.com", EmailDirection.OUT);
        entityManager.flush();

        assertThat(emailRepository.findUnassignedEingangVonAdresse("max.mustermann@gmail.com"))
                .extracting(Email::getId)
                .containsExactly(eigene.getId());
        // Zum Vergleich: die Domain-Suche hätte alle drei eingehenden Gmail-Mails getroffen.
        assertThat(emailRepository.findUnassignedByDomain("gmail.com")).hasSize(3);
    }

    @Test
    void absenderMitAnzeigenameTrifftNurDieExakteAdresse() {
        Email mitName = mail("<n1@example.org>", "\"Max Mustermann\" <Max@GMX.de>", EmailDirection.IN);
        mail("<n2@example.org>", "Max Mustermann <xy@gmx.de>", EmailDirection.IN);
        mail("<n3@example.org>", "Erika Musterfrau <erika@gmx.de>", EmailDirection.IN);
        Email unterstrich = mail("<n4@example.org>", "Max <max_m@gmx.de>", EmailDirection.IN);
        mail("<n5@example.org>", "Max <maxXm@gmx.de>", EmailDirection.IN);
        entityManager.flush();

        assertThat(emailRepository.findUnassignedEingangVonAdresse("max@gmx.de"))
                .extracting(Email::getId)
                .containsExactly(mitName.getId());
        assertThat(emailRepository.findUnassignedEingangVonAdresse("x@gmx.de")).isEmpty();
        // Unterstrich ist im LIKE-Muster maskiert: kein Treffer auf maxXm@gmx.de.
        assertThat(emailRepository.findUnassignedEingangVonAdresse(" Max_M@gmx.de "))
                .extracting(Email::getId)
                .containsExactly(unterstrich.getId());
    }

    @Test
    void anfrageFlagNurFuerExakteAdresse() {
        Email eigene = mail("<a1@example.org>", "max.mustermann@gmail.com", EmailDirection.IN);
        eigene.setPotentialInquiry(true);
        Email fremd = mail("<a2@example.org>", "erika.musterfrau@gmail.com", EmailDirection.IN);
        fremd.setPotentialInquiry(true);
        Email mitName = mail("<a3@example.org>", "Max Mustermann <max.mustermann@gmail.com>", EmailDirection.IN);
        mitName.setPotentialInquiry(true);
        entityManager.flush();

        assertThat(emailRepository.findInquiriesVonAdresse("max.mustermann@gmail.com"))
                .extracting(Email::getId)
                .containsExactlyInAnyOrder(eigene.getId(), mitName.getId());
    }
}
