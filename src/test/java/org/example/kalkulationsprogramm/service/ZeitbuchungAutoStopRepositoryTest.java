package org.example.kalkulationsprogramm.service;

import jakarta.persistence.EntityManager;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
class ZeitbuchungAutoStopRepositoryTest {
    @Autowired EntityManager em;
    @Autowired ZeitbuchungRepository repository;

    @Test
    void offeneBuchungenOhneAktivesZeitkontoMitGeladenemMitarbeiter() {
        Mitarbeiter m = new Mitarbeiter();
        m.setVorname("Max");
        m.setNachname("Mustermann");
        m.setAktiv(false);
        m.setFuehrtZeitkonto(false);
        em.persist(m);
        Zeitbuchung offen = new Zeitbuchung();
        offen.setMitarbeiter(m);
        offen.setStartZeit(LocalDateTime.of(2026, 1, 5, 8, 0));
        em.persist(offen);
        Zeitbuchung beendet = new Zeitbuchung();
        beendet.setMitarbeiter(m);
        beendet.setStartZeit(LocalDateTime.of(2026, 1, 4, 8, 0));
        beendet.setEndeZeit(LocalDateTime.of(2026, 1, 4, 16, 0));
        em.persist(beendet);
        em.flush();
        em.clear();

        var ergebnis = repository.findByEndeZeitIsNull();

        assertThat(ergebnis).extracting(Zeitbuchung::getId).containsExactly(offen.getId());
        assertThat(em.getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(ergebnis.getFirst(), "mitarbeiter")).isTrue();
        em.clear();
        assertThat(ergebnis.getFirst().getMitarbeiter().getArt()).isEqualTo(m.getArt());
        assertThat(ergebnis.getFirst().getMitarbeiter().getFuehrtZeitkonto()).isFalse();
    }
}
