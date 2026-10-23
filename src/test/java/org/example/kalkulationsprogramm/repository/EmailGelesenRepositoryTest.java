package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import jakarta.persistence.EntityManager;

/** „Gelesen“ pro Person: Merker lesen und schreiben (H2; PostgreSQL in DatenbankKompatibilitaetBetriebTest). */
@DataJpaTest
class EmailGelesenRepositoryTest {

    @Autowired private EmailGelesenRepository repository;
    @Autowired private EmailRepository emailRepository;
    @Autowired private FrontendUserProfileRepository benutzerRepository;
    @Autowired private EntityManager entityManager;

    private FrontendUserProfile benutzer(String username) {
        FrontendUserProfile b = new FrontendUserProfile();
        b.setDisplayName("Max Mustermann");
        b.setUsername(username);
        return benutzerRepository.save(b);
    }

    private Email mail(String messageId, EmailDirection richtung) {
        Email e = new Email();
        e.setMessageId(messageId);
        e.setDirection(richtung);
        e.setSentAt(LocalDateTime.of(2026, 10, 12, 8, 0));
        return emailRepository.save(e);
    }

    @Test
    void merkenNurFuerDenBenutzerUndOhneDoppelte() {
        FrontendUserProfile max = benutzer("max");
        FrontendUserProfile erika = benutzer("erika");
        Email a = mail("<a@example.org>", EmailDirection.IN);
        Email b = mail("<b@example.org>", EmailDirection.IN);
        entityManager.flush();

        repository.alsGelesenMerken(max.getId(), List.of(a.getId()), LocalDateTime.now());
        // Zweites Mal (Doppelklick) und gemischt mit neuen Mails: nichts doppelt, keine Ausnahme.
        repository.alsGelesenMerken(max.getId(), List.of(a.getId(), b.getId(), a.getId()), LocalDateTime.now());
        // Nicht vorhandene Mail wird übergangen.
        repository.alsGelesenMerken(max.getId(), List.of(Long.MAX_VALUE), LocalDateTime.now());
        entityManager.clear();

        assertThat(repository.findEmailIdsByBenutzerId(max.getId())).containsExactlyInAnyOrder(a.getId(), b.getId());
        assertThat(repository.findAll()).hasSize(2);
        assertThat(repository.findEmailIdsByBenutzerId(erika.getId())).isEmpty();
        assertThat(repository.geleseneAus(max.getId(), List.of(a.getId(), Long.MAX_VALUE))).containsExactly(a.getId());
        assertThat(repository.geleseneAus(erika.getId(), List.of(a.getId(), b.getId()))).isEmpty();
        assertThat(repository.geleseneAus(max.getId(), List.of())).isEmpty();
    }

    @Test
    void neuerBenutzerBekommtNurEingehendeMails() {
        Email ein = mail("<ein@example.org>", EmailDirection.IN);
        mail("<aus@example.org>", EmailDirection.OUT);
        FrontendUserProfile neu = benutzer("neu");
        entityManager.flush();

        assertThat(repository.alleMailsAlsGelesen(neu.getId(), EmailDirection.IN, LocalDateTime.now())).isEqualTo(1);
        assertThat(repository.alleMailsAlsGelesen(neu.getId(), EmailDirection.IN, LocalDateTime.now())).isZero();
        assertThat(repository.findEmailIdsByBenutzerId(neu.getId())).containsExactly(ein.getId());
    }

    @Test
    void grosseMengenGehenInBloecken() {
        List<Long> ids = new ArrayList<>(LongStream.rangeClosed(1, 2500).boxed().toList());
        ids.add(1L);
        ids.add(null);

        List<List<Long>> bloecke = EmailGelesenRepositoryErweiterungImpl.bloecke(ids);

        assertThat(bloecke).extracting(List::size).containsExactly(1000, 1000, 500);
        assertThat(bloecke.getFirst().getFirst()).isEqualTo(1L);
        assertThat(EmailGelesenRepositoryErweiterungImpl.bloecke(Arrays.asList((Long) null))).isEmpty();
    }
}
