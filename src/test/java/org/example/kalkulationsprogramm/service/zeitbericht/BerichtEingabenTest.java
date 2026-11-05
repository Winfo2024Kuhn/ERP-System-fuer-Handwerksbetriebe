package org.example.kalkulationsprogramm.service.zeitbericht;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BerichtEingabenTest {

    @Test
    void gueltigerMonatGehtDurch() {
        BerichtEingaben.pruefeMonat(2026, 10);
        BerichtEingaben.pruefeMonat(2000, 1);
        BerichtEingaben.pruefeMonat(2100, 12);
    }

    @Test
    void ungueltigerMonatOderJahrIst400() {
        for (int[] falsch : new int[][] {{2026, 0}, {2026, 13}, {1999, 5}, {2101, 5}, {-1, -1}}) {
            assertThatThrownBy(() -> BerichtEingaben.pruefeMonat(falsch[0], falsch[1]))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    @Test
    void mitarbeiterIdsWerdenEntdoppeltUndDuerfenBeimSteuerberaterFehlen() {
        assertThat(BerichtEingaben.mitarbeiterIds(null, false)).isEmpty();
        assertThat(BerichtEingaben.mitarbeiterIds(List.of(), false)).isEmpty();
        assertThat(BerichtEingaben.mitarbeiterIds(List.of(3L, 1L, 3L), false)).containsExactly(3L, 1L);
    }

    @Test
    void pflichtauswahlOhneMitarbeiterIst400() {
        assertThatThrownBy(() -> BerichtEingaben.mitarbeiterIds(List.of(), true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("mindestens einen Mitarbeiter");
    }

    @Test
    void ungueltigeIdsUndZuVieleMitarbeiterSind400() {
        List<Long> mitNull = new ArrayList<>(Arrays.asList(1L, null));
        for (List<Long> falsch : List.of(List.of(0L), List.of(-1L), mitNull)) {
            assertThatThrownBy(() -> BerichtEingaben.mitarbeiterIds(falsch, false))
                    .isInstanceOf(ResponseStatusException.class);
        }
        List<Long> zuViele = LongStream.rangeClosed(1, 51).boxed().toList();
        assertThatThrownBy(() -> BerichtEingaben.mitarbeiterIds(zuViele, true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("50");
        assertThat(BerichtEingaben.mitarbeiterIds(List.of(Long.MAX_VALUE), true)).containsExactly(Long.MAX_VALUE);
    }

    @Test
    void zeitraumHoechstensEinJahrUndVonVorBis() {
        BerichtEingaben.pruefeZeitraum(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        BerichtEingaben.pruefeZeitraum(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)); // Schaltjahr: 366 Tage
        BerichtEingaben.pruefeZeitraum(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5));

        assertThatThrownBy(() -> BerichtEingaben.pruefeZeitraum(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("vor dem Ende");
        assertThatThrownBy(() -> BerichtEingaben.pruefeZeitraum(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("ein Jahr");
        assertThatThrownBy(() -> BerichtEingaben.pruefeZeitraum(null, LocalDate.of(2026, 1, 2)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> BerichtEingaben.pruefeZeitraum(LocalDate.of(1999, 12, 31), LocalDate.of(2000, 1, 2)))
                .isInstanceOf(ResponseStatusException.class);
    }
}
