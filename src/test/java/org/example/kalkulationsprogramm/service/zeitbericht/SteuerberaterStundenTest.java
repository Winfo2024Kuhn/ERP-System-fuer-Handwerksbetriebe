package org.example.kalkulationsprogramm.service.zeitbericht;

import org.example.kalkulationsprogramm.dto.ZeitkontenmodellDto;
import org.example.kalkulationsprogramm.dto.ZeitkontoVersionDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SteuerberaterStundenTest {

    @Test
    void arbeitsstundenSindSollMinusAllerAbwesenheiten() {
        BigDecimal ergebnis = SteuerberaterStunden.arbeitsstunden(new BigDecimal("176"), new BigDecimal("16"),
                new BigDecimal("8"), new BigDecimal("4.5"), new BigDecimal("2"));
        assertThat(ergebnis).isEqualByComparingTo("145.5");
    }

    @Test
    void arbeitsstundenWerdenNieNegativUndVertragenLeereWerte() {
        assertThat(SteuerberaterStunden.arbeitsstunden(new BigDecimal("8"), new BigDecimal("16"), null, null, null))
                .isEqualByComparingTo("0");
        assertThat(SteuerberaterStunden.arbeitsstunden(null, null, null, null, null)).isEqualByComparingTo("0");
    }

    @Test
    void tagessollIstWochenstundenDurchFuenfImMonatsmittel() {
        // 1.–15.10. 40-Stunden-Woche (8 h), ab 16.10. 30-Stunden-Woche (6 h)
        var vorher = version(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 10, 15), "8");
        var danach = version(LocalDate.of(2026, 10, 16), null, "6");

        BigDecimal tagessoll = SteuerberaterStunden.tagessoll(List.of(vorher, danach), YearMonth.of(2026, 10));

        // (15 × 8 + 16 × 6) / 31 = 6,967… → 7,0
        assertThat(tagessoll).isEqualByComparingTo("7.0");
    }

    @Test
    void ohneGueltigeVersionIstDasTagessollNull() {
        var spaeter = version(LocalDate.of(2027, 1, 1), null, "8");
        assertThat(SteuerberaterStunden.tagessoll(List.of(spaeter), YearMonth.of(2026, 10))).isEqualByComparingTo("0");
        assertThat(SteuerberaterStunden.tagessoll(List.of(), YearMonth.of(2026, 10))).isEqualByComparingTo("0");
    }

    @Test
    void rundetAufEineNachkommastelle() {
        assertThat(SteuerberaterStunden.runde(new BigDecimal("12.349"))).isEqualByComparingTo("12.3");
        assertThat(SteuerberaterStunden.runde(new BigDecimal("12.35"))).isEqualByComparingTo("12.4");
        assertThat(SteuerberaterStunden.runde(null)).isEqualByComparingTo("0.0");
    }

    private static ZeitkontoVersionDto version(LocalDate von, LocalDate bis, String werktag) {
        BigDecimal h = new BigDecimal(werktag);
        var arbeitszeit = new ZeitkontenmodellDto.Arbeitszeit(h, h, h, h, h, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        return new ZeitkontoVersionDto(1L, 0L, 1L, von, bis, null, arbeitszeit);
    }
}
