package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.BenachrichtigungErledigt;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

/** Erledigte Glocken-Einträge und die Ordner Spam/Newsletter (ohne zugeordnete Mails). */
@DataJpaTest
class BenachrichtigungUndMailOrdnerRepositoryTest {

    @Autowired private BenachrichtigungErledigtRepository erledigtRepository;
    @Autowired private FrontendUserProfileRepository benutzerRepository;
    @Autowired private EmailRepository emailRepository;

    private FrontendUserProfile benutzer(String kuerzel) {
        FrontendUserProfile b = new FrontendUserProfile();
        b.setDisplayName("Max Mustermann " + kuerzel);
        b.setShortCode(kuerzel);
        return benutzerRepository.save(b);
    }

    private BenachrichtigungErledigt erledigt(FrontendUserProfile benutzer, String schluessel, LocalDateTime am) {
        BenachrichtigungErledigt e = new BenachrichtigungErledigt();
        e.setBenutzer(benutzer);
        e.setSchluessel(schluessel);
        e.setErledigtAm(am);
        return erledigtRepository.saveAndFlush(e);
    }

    @Test
    void erledigteEintraegeJeBenutzerUndAlteWerdenGeloescht() {
        FrontendUserProfile max = benutzer("MM");
        FrontendUserProfile erika = benutzer("EM");
        LocalDateTime jetzt = LocalDateTime.of(2026, 10, 11, 9, 0);
        erledigt(max, "TERMIN:1", jetzt);
        erledigt(max, "LIEFERSCHEIN:2", jetzt.minusDays(50));
        erledigt(erika, "TERMIN:1", jetzt);

        assertThat(erledigtRepository.findByBenutzerId(max.getId()))
                .extracting(BenachrichtigungErledigt::getSchluessel)
                .containsExactlyInAnyOrder("TERMIN:1", "LIEFERSCHEIN:2");
        assertThat(erledigtRepository.findByBenutzerIdAndSchluesselIn(max.getId(), List.of("TERMIN:1", "TERMIN:9")))
                .singleElement().satisfies(e -> assertThat(e.getSchluessel()).isEqualTo("TERMIN:1"));

        assertThat(erledigtRepository.deleteAelterAls(jetzt.minusDays(45))).isEqualTo(1);
        assertThat(erledigtRepository.findByBenutzerId(max.getId())).hasSize(1);
        assertThat(erledigtRepository.findByBenutzerId(erika.getId())).hasSize(1);
    }

    @Test
    void schluesselJeBenutzerNurEinmal() {
        FrontendUserProfile max = benutzer("MX");
        erledigt(max, "TERMIN:1", LocalDateTime.now());
        assertThatThrownBy(() -> erledigt(max, "TERMIN:1", LocalDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Email mail(String id, boolean spam, boolean newsletter, EmailZuordnungTyp zuordnung, boolean gelesen) {
        Email e = new Email();
        e.setMessageId("<" + id + "@example.org>");
        e.setSubject(id);
        e.setFromAddress("kunde@example.org");
        e.setDirection(EmailDirection.IN);
        e.setSpam(spam);
        e.setNewsletter(newsletter);
        e.setZuordnungTyp(zuordnung);
        e.setRead(gelesen);
        e.setSentAt(LocalDateTime.of(2026, 10, 1, 8, 0));
        return emailRepository.save(e);
    }

    @Test
    void spamUndNewsletterOrdnerOhneZugeordneteMails() {
        mail("spam-frei", true, false, EmailZuordnungTyp.KEINE, false);
        mail("spam-gelesen", true, false, EmailZuordnungTyp.KEINE, true);
        mail("spam-projekt", true, false, EmailZuordnungTyp.PROJEKT, false);
        mail("news-frei", false, true, EmailZuordnungTyp.KEINE, false);
        mail("news-lieferant", false, true, EmailZuordnungTyp.LIEFERANT, false);
        emailRepository.flush();

        assertThat(emailRepository.findSpamOrdner()).extracting(Email::getSubject)
                .containsExactlyInAnyOrder("spam-frei", "spam-gelesen");
        assertThat(emailRepository.findNewsletterOrdner()).extracting(Email::getSubject).containsExactly("news-frei");
        // Die Bereinigung sieht weiter alle Spam-Mails und entscheidet selbst.
        assertThat(emailRepository.findSpam()).hasSize(3);
    }
}
