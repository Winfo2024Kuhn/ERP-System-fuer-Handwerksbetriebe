package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailPostfachZuordnung;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import jakarta.persistence.EntityManager;

/** Abfragen rund um Postfächer: Umzug alter Mails, Benutzer-Zuordnung, Haken. */
@DataJpaTest
class EmailPostfachRepositoryTest {

    @Autowired private EmailAbsenderRepository postfachRepository;
    @Autowired private EmailPostfachZuordnungRepository zuordnungRepository;
    @Autowired private EmailRepository emailRepository;
    @Autowired private FrontendUserProfileRepository benutzerRepository;
    @Autowired private EntityManager entityManager;

    private EmailAbsender postfach(String adresse, boolean haupt, boolean rechnungen) {
        EmailAbsender p = new EmailAbsender();
        p.setEmailAdresse(adresse);
        p.setHauptpostfach(haupt);
        p.setFuerGeschaeftsdokumente(rechnungen);
        return postfachRepository.save(p);
    }

    private Email mail(String messageId, String ordner, Long uid) {
        Email e = new Email();
        e.setMessageId(messageId);
        e.setDirection(EmailDirection.IN);
        e.setSentAt(LocalDateTime.of(2026, 10, 1, 8, 0));
        e.setImapFolder(ordner);
        e.setImapUid(uid);
        return emailRepository.save(e);
    }

    @Test
    void alteMailsWandernInsHauptpostfachUndZweiterLaufAendertNichts() {
        EmailAbsender info = postfach("info@example.com", true, false);
        EmailAbsender max = postfach("max@example.com", false, false);
        Email alt1 = mail("<alt-1@example.org>", "INBOX", 11L);
        mail("<alt-2@example.org>", "INBOX.Sent", 12L);
        Email schonZugeordnet = mail("<neu@example.org>", "INBOX", 13L);
        schonZugeordnet.ordnePostfachZu(max, "INBOX", 13L);
        emailRepository.saveAndFlush(schonZugeordnet);

        int ersterLauf = zuordnungRepository.ordneOhnePostfachZu(info.getId());
        int zweiterLauf = zuordnungRepository.ordneOhnePostfachZu(info.getId());
        entityManager.clear();

        assertThat(ersterLauf).isEqualTo(2);
        assertThat(zweiterLauf).isZero();
        List<EmailPostfachZuordnung> z = zuordnungRepository.findByEmailId(alt1.getId());
        assertThat(z).singleElement().satisfies(zu -> {
            assertThat(zu.getPostfach().getId()).isEqualTo(info.getId());
            assertThat(zu.getImapOrdner()).isEqualTo("INBOX");
            assertThat(zu.getImapUid()).isEqualTo(11L);
        });
        assertThat(zuordnungRepository.findByEmailId(schonZugeordnet.getId()))
                .extracting(zu -> zu.getPostfach().getId()).containsExactly(max.getId());
        assertThat(zuordnungRepository.existsByPostfachId(max.getId())).isTrue();
    }

    @Test
    void ausgangsmailsKommenInDasPostfachIhresAbsenders() {
        EmailAbsender info = postfach("info@example.com", true, false);
        EmailAbsender rechnungen = postfach("rechnungen@example.com", false, true);
        Email rechnung = mail("<rechnung@example.org>", null, null);
        rechnung.setDirection(EmailDirection.OUT);
        rechnung.setFromAddress(" Rechnungen@Example.com");
        emailRepository.save(rechnung);
        Email fremd = mail("<fremd@example.org>", null, null);
        fremd.setDirection(EmailDirection.OUT);
        fremd.setFromAddress("unbekannt@example.org");
        emailRepository.save(fremd);
        Email eingang = mail("<eingang@example.org>", "INBOX", 1L);
        eingang.setFromAddress("rechnungen@example.com");
        emailRepository.saveAndFlush(eingang);

        int nachAbsender = zuordnungRepository.ordneAusgangsmailsNachAbsenderZu();
        int rest = zuordnungRepository.ordneOhnePostfachZu(info.getId());
        entityManager.clear();

        assertThat(nachAbsender).isEqualTo(1);
        assertThat(rest).isEqualTo(2);
        assertThat(zuordnungRepository.findByEmailId(rechnung.getId()))
                .extracting(z -> z.getPostfach().getId()).containsExactly(rechnungen.getId());
        assertThat(zuordnungRepository.findByEmailId(fremd.getId()))
                .extracting(z -> z.getPostfach().getId()).containsExactly(info.getId());
        assertThat(zuordnungRepository.findByEmailId(eingang.getId()))
                .extracting(z -> z.getPostfach().getId()).containsExactly(info.getId());
        assertThat(zuordnungRepository.ordneAusgangsmailsNachAbsenderZu()).isZero();
    }

    @Test
    void hakenUndBenutzerAbfragen() {
        EmailAbsender info = postfach("info@example.com", true, false);
        EmailAbsender rechnungen = postfach("rechnungen@example.com", false, true);
        EmailAbsender aus = postfach("alt@example.com", false, true);
        aus.setAktiv(false);
        postfachRepository.save(aus);
        FrontendUserProfile benutzer = new FrontendUserProfile();
        benutzer.setDisplayName("Max Mustermann");
        benutzer.setShortCode("MM");
        benutzer.setEmailAbsender(rechnungen);
        benutzerRepository.save(benutzer);

        assertThat(postfachRepository.findFirstByHauptpostfachTrueOrderByIdAsc()).contains(info);
        assertThat(postfachRepository.findFirstByFuerGeschaeftsdokumenteTrueAndAktivTrueOrderByIdAsc()).contains(rechnungen);
        assertThat(postfachRepository.findByFuerGeschaeftsdokumenteTrue()).hasSize(2);
        assertThat(postfachRepository.findByHauptpostfachTrue()).containsExactly(info);
        assertThat(benutzerRepository.findByEmailAbsenderId(rechnungen.getId())).hasSize(1);
        assertThat(benutzerRepository.findByEmailAbsenderIsNotNull()).hasSize(1);
    }
}
