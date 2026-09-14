package org.example.kalkulationsprogramm.service;

import java.time.LocalDate;
import java.time.YearMonth;

public final class Wirtschaftsjahr {
    private Wirtschaftsjahr() { }

    /** Beginn des Wirtschaftsjahres, das den Exportmonat enthält. */
    public static LocalDate beginn(YearMonth exportMonat, int beginnMonat) {
        int monat = beginnMonat >= 1 && beginnMonat <= 12 ? beginnMonat : 1;
        int jahr = exportMonat.getYear() - (exportMonat.getMonthValue() < monat ? 1 : 0);
        return LocalDate.of(jahr, monat, 1);
    }
}
