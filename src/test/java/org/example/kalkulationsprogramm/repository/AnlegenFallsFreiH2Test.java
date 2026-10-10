package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.SperrbarerTyp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * Die Release-.exe laeuft auf H2 im MySQL-Modus - dort kommt die MySQL-Variante
 * der "Anlegen, falls frei"-Fragmente an ({@code INSERT IGNORE}).
 */
@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:anlegen;MODE=MySQL;DB_CLOSE_DELAY=-1;"
        + "DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AnlegenFallsFreiH2Test {

    @Autowired
    DatensatzLockRepository locks;

    @Autowired
    WerkstoffRepository werkstoffe;

    @Test
    @DisplayName("Lock: erstes Anlegen klappt, zweites tut nichts - ohne Fehler")
    void lockAnlegen() {
        LocalDateTime zeitpunkt = LocalDateTime.of(2026, 3, 15, 12, 0);

        assertThat(locks.legeAnFallsFrei(SperrbarerTyp.AUSGANG, 42L, 1L, "Max Mustermann", zeitpunkt)).isTrue();
        assertThat(locks.legeAnFallsFrei(SperrbarerTyp.AUSGANG, 42L, 2L, "Erika Mustermann", zeitpunkt)).isFalse();

        assertThat(locks.findGesperrt(SperrbarerTyp.AUSGANG, 42L))
                .hasValueSatisfying(lock -> assertThat(lock.getUserId()).isEqualTo(1L));
    }

    @Test
    @DisplayName("Werkstoff: doppelt anlegen ergibt genau einen Eintrag")
    void werkstoffAnlegen() {
        werkstoffe.legeAnFallsNeu("Mustermann-Stahl");
        werkstoffe.legeAnFallsNeu("Mustermann-Stahl");

        assertThat(werkstoffe.findByNameGesperrt("mustermann-stahl")).isPresent();
        assertThat(werkstoffe.findAll()).filteredOn(w -> "Mustermann-Stahl".equals(w.getName())).hasSize(1);
    }
}
