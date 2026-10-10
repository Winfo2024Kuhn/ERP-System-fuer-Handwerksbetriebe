package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import jakarta.persistence.EntityManager;

/** Abfragen der Postfach-Sichtbarkeit: Freigaben und ausgeblendete Mails (H2). */
@DataJpaTest
class PostfachSichtbarkeitRepositoryTest {

    @Autowired private EmailAbsenderRepository postfachRepository;
    @Autowired private EmailPostfachZuordnungRepository zuordnungRepository;
    @Autowired private EmailRepository emailRepository;
    @Autowired private FrontendUserProfileRepository benutzerRepository;
    @Autowired private AbteilungRepository abteilungRepository;
    @Autowired private EntityManager entityManager;

    private EmailAbsender postfach(String adresse, boolean haupt, boolean fuerAlle) {
        EmailAbsender p = new EmailAbsender();
        p.setEmailAdresse(adresse);
        p.setHauptpostfach(haupt);
        p.setSichtbarFuerAlle(fuerAlle);
        return postfachRepository.save(p);
    }

    private FrontendUserProfile benutzer(String name) {
        FrontendUserProfile u = new FrontendUserProfile();
        u.setDisplayName(name);
        u.setUsername(name.toLowerCase().replace(' ', '.'));
        return benutzerRepository.save(u);
    }

    private Abteilung abteilung(String name) {
        Abteilung a = new Abteilung();
        a.setName(name);
        return abteilungRepository.save(a);
    }

    private Email mail(String messageId, EmailAbsender... postfaecher) {
        Email e = new Email();
        e.setMessageId(messageId);
        e.setDirection(EmailDirection.IN);
        e.setSentAt(LocalDateTime.of(2026, 10, 1, 8, 0));
        for (EmailAbsender p : postfaecher) {
            e.ordnePostfachZu(p, "INBOX", null);
        }
        return emailRepository.save(e);
    }

    @Test
    void freigabenFuerBenutzerUndAbteilungen() {
        EmailAbsender max = postfach("max@example.com", false, false);
        EmailAbsender werkstatt = postfach("werkstatt@example.com", false, false);
        postfach("info@example.com", true, true);
        FrontendUserProfile erika = benutzer("Erika Mustermann");
        FrontendUserProfile max2 = benutzer("Max Mustermann");
        Abteilung buero = abteilung("Büro");
        Abteilung werk = abteilung("Werkstatt");
        max.getSichtbarFuerBenutzer().add(erika);
        max.getSichtbarFuerAbteilungen().add(buero);
        werkstatt.getSichtbarFuerAbteilungen().add(buero);
        werkstatt.getSichtbarFuerAbteilungen().add(werk);
        postfachRepository.saveAndFlush(max);
        postfachRepository.saveAndFlush(werkstatt);
        entityManager.clear();

        assertThat(postfachRepository.findIdsFreigegebenFuerBenutzer(erika.getId())).containsExactly(max.getId());
        assertThat(postfachRepository.findIdsFreigegebenFuerBenutzer(max2.getId())).isEmpty();
        assertThat(postfachRepository.findIdsFreigegebenFuerAbteilungen(List.of(buero.getId(), werk.getId())))
                .containsExactlyInAnyOrder(max.getId(), werkstatt.getId());
        assertThat(postfachRepository.findIdsFreigegebenFuerAbteilungen(List.of(werk.getId())))
                .containsExactly(werkstatt.getId());
        assertThat(postfachRepository.findIdsFreigegebenFuerAbteilungen(List.of(Long.MAX_VALUE))).isEmpty();
    }

    @Test
    void freigabenUeberlebenNeuladen() {
        EmailAbsender max = postfach("max@example.com", false, false);
        FrontendUserProfile erika = benutzer("Erika Mustermann");
        Abteilung buero = abteilung("Büro");
        max.getSichtbarFuerBenutzer().add(erika);
        max.getSichtbarFuerAbteilungen().add(buero);
        postfachRepository.saveAndFlush(max);
        entityManager.clear();

        EmailAbsender geladen = postfachRepository.findById(max.getId()).orElseThrow();
        assertThat(geladen.isSichtbarFuerAlle()).isFalse();
        assertThat(geladen.getSichtbarFuerBenutzer()).extracting(FrontendUserProfile::getDisplayName)
                .containsExactly("Erika Mustermann");
        assertThat(geladen.getSichtbarFuerAbteilungen()).extracting(Abteilung::getName).containsExactly("Büro");
    }

    @Test
    void neuesPostfachIstFuerAlleSichtbar() {
        EmailAbsender p = new EmailAbsender();
        p.setEmailAdresse("neu@example.com");
        postfachRepository.saveAndFlush(p);
        entityManager.clear();

        assertThat(postfachRepository.findById(p.getId()).orElseThrow().isSichtbarFuerAlle()).isTrue();
    }

    @Test
    void ausgeblendetSindNurMailsAusschliesslichInFremdenPostfaechern() {
        EmailAbsender info = postfach("info@example.com", true, true);
        EmailAbsender max = postfach("max@example.com", false, false);
        EmailAbsender rechnungen = postfach("rechnungen@example.com", false, false);
        Email nurInfo = mail("<info@example.org>", info);
        Email nurMax = mail("<max@example.org>", max);
        Email infoUndMax = mail("<beide@example.org>", info, max);
        Email maxUndRechnungen = mail("<zwei-fremde@example.org>", max, rechnungen);
        Email ohnePostfach = mail("<alt@example.org>");
        emailRepository.flush();
        entityManager.clear();

        Set<Long> verborgen = zuordnungRepository.findEmailIdsNurInAnderenPostfaechern(Set.of(info.getId()));

        assertThat(verborgen).containsExactlyInAnyOrder(nurMax.getId(), maxUndRechnungen.getId());
        assertThat(verborgen).doesNotContain(nurInfo.getId(), infoUndMax.getId(), ohnePostfach.getId());

        // Wer zusätzlich max@ sieht, sieht auch die Mail aus max@ und rechnungen@ – nichts mehr verborgen.
        assertThat(zuordnungRepository.findEmailIdsNurInAnderenPostfaechern(Set.of(info.getId(), max.getId())))
                .isEmpty();
        // Platzhalter -1: nichts sichtbar außer Mails ohne Postfach.
        assertThat(zuordnungRepository.findEmailIdsNurInAnderenPostfaechern(Set.of(-1L)))
                .containsExactlyInAnyOrder(nurInfo.getId(), nurMax.getId(), infoUndMax.getId(),
                        maxUndRechnungen.getId());
    }
}
