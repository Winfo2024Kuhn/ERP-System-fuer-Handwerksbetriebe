package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Lieferant per Name ohne Gross-/Kleinschreibung. Auf PostgreSQL koennen zwei
 * Schreibweisen nebeneinander stehen (der Unique-Index unterscheidet sie) - dann
 * darf die Suche nicht an "mehreren Treffern" scheitern.
 */
class LieferantenRepositoryNameTest {

    private static Lieferanten lieferant(long id, String name) {
        Lieferanten l = new Lieferanten();
        l.setId(id);
        l.setLieferantenname(name);
        return l;
    }

    private static LieferantenRepository mitTreffern(String gesucht, Lieferanten... treffer) {
        LieferantenRepository repository = Mockito.mock(LieferantenRepository.class, Mockito.CALLS_REAL_METHODS);
        Mockito.doReturn(List.of(treffer)).when(repository).findAllByLieferantennameIgnoreCaseOrderByIdAsc(gesucht);
        return repository;
    }

    @Test
    @DisplayName("Exakte Schreibweise gewinnt, sonst der aelteste Eintrag")
    void exaktVorAeltestem() {
        LieferantenRepository repository = mitTreffern("WÜRTH", lieferant(1, "Würth"), lieferant(2, "WÜRTH"));
        assertThat(repository.findByLieferantennameIgnoreCase("WÜRTH")).map(Lieferanten::getId).contains(2L);

        LieferantenRepository nurAndere = mitTreffern("würth", lieferant(1, "Würth"), lieferant(2, "WÜRTH"));
        assertThat(nurAndere.findByLieferantennameIgnoreCase("würth")).map(Lieferanten::getId).contains(1L);
    }

    @Test
    @DisplayName("Leerzeichen am Rand zaehlen nicht, kein Name: kein Treffer")
    void randUndLeer() {
        LieferantenRepository repository = mitTreffern("Würth", lieferant(1, "Würth"));
        assertThat(repository.findByLieferantennameIgnoreCase(" Würth ")).map(Lieferanten::getId).contains(1L);
        assertThat(repository.findByLieferantennameIgnoreCase(null)).isEmpty();
        assertThat(mitTreffern("Neu").findByLieferantennameIgnoreCase("Neu")).isEmpty();
    }
}
