package org.example.kalkulationsprogramm.service;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Abrechnungsmonat eines Steuerberater-Dokuments (Lohnabrechnung, BWA), wenn die KI
 * ihn nicht liefert: aus dem Dateinamen gelesen, sonst der Vormonat.
 */
record SteuerberaterPeriode(int jahr, int monat) {

    /** 2025-01, 2025_01, 202501 */
    private static final Pattern JAHR_MONAT = Pattern.compile("(20\\d{2})[_-]?(0[1-9]|1[0-2])");

    /** 01-2025, 01_2025 */
    private static final Pattern MONAT_JAHR = Pattern.compile("(0[1-9]|1[0-2])[_-](20\\d{2})");

    /**
     * Liest Jahr und Monat aus dem Dateinamen ("2025-01", "01-2025", …).
     * Ohne Treffer gilt der Vormonat (die Lohnabrechnung kommt meist für den Vormonat),
     * ohne Dateinamen der laufende Monat.
     */
    static SteuerberaterPeriode ausDateiname(String filename) {
        LocalDate heute = LocalDate.now();
        if (filename == null) {
            return new SteuerberaterPeriode(heute.getYear(), heute.getMonthValue());
        }
        Matcher jahrMonat = JAHR_MONAT.matcher(filename);
        if (jahrMonat.find()) {
            return new SteuerberaterPeriode(Integer.parseInt(jahrMonat.group(1)), Integer.parseInt(jahrMonat.group(2)));
        }
        Matcher monatJahr = MONAT_JAHR.matcher(filename);
        if (monatJahr.find()) {
            return new SteuerberaterPeriode(Integer.parseInt(monatJahr.group(2)), Integer.parseInt(monatJahr.group(1)));
        }
        LocalDate vormonat = heute.minusMonths(1);
        return new SteuerberaterPeriode(vormonat.getYear(), vormonat.getMonthValue());
    }
}
