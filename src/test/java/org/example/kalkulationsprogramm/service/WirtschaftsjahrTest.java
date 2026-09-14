package org.example.kalkulationsprogramm.service;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class WirtschaftsjahrTest {
    @ParameterizedTest
    @CsvSource({"2026-08,1,2026-01-01", "2026-08,7,2026-07-01",
            "2026-03,7,2025-07-01", "2026-01,1,2026-01-01",
            "2026-08,0,2026-01-01", "2026-08,13,2026-01-01"})
    void bestimmtBeginn(String monat, int beginn, String erwartet) {
        assertThat(Wirtschaftsjahr.beginn(YearMonth.parse(monat), beginn))
                .isEqualTo(LocalDate.parse(erwartet));
    }
}
