package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.example.kalkulationsprogramm.domain.SteuerberaterKontakt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import jakarta.persistence.EntityManager;

/**
 * Erkennung über die weiteren E-Mail-Adressen einer Kanzlei (H2; die JPQL-Abfrage
 * läuft zusätzlich in {@code DatenbankKompatibilitaetTest} gegen PostgreSQL).
 */
@DataJpaTest
class SteuerberaterKontaktRepositoryTest {

    @Autowired private SteuerberaterKontaktRepository repository;
    @Autowired private EntityManager entityManager;

    private SteuerberaterKontakt kanzlei(String name, String hauptEmail, boolean aktiv, String... weitere) {
        SteuerberaterKontakt k = new SteuerberaterKontakt();
        k.setName(name);
        k.setEmail(hauptEmail);
        k.setAktiv(aktiv);
        k.setAutoProcessEmails(false);
        k.setWeitereEmails(new java.util.HashSet<>(Set.of(weitere)));
        return repository.save(k);
    }

    @Test
    void findetAktiveKanzleiUeberWeitereEmailOhneRuecksichtAufGrossKlein() {
        SteuerberaterKontakt mustermann = kanzlei("Steuerbüro Mustermann", "steuerbuero.mustermann@gmx.de", true,
                "Lohn.Mustermann@gmx.de", "bwa.mustermann@gmx.de");
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findeAktiveMitWeitererEmail("lohn.mustermann@GMX.de"))
                .extracting(SteuerberaterKontakt::getId)
                .containsExactly(mustermann.getId());
    }

    @Test
    void ignoriertInaktiveKanzleienUndFremdeAdressen() {
        kanzlei("Steuerbüro Alt", "alt@example.com", false, "lohn.mustermann@gmx.de");
        kanzlei("Steuerbüro Mustermann", "steuerbuero.mustermann@gmx.de", true, "lohn.mustermann@gmx.de");
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findeAktiveMitWeitererEmail("lohn.mustermann@gmx.de"))
                .extracting(SteuerberaterKontakt::getName)
                .containsExactly("Steuerbüro Mustermann");
        assertThat(repository.findeAktiveMitWeitererEmail("max.mustermann@gmx.de")).isEmpty();
        assertThat(repository.findeAktiveMitWeitererEmail("'; DROP TABLE steuerberater_kontakt; --")).isEmpty();
    }
}
