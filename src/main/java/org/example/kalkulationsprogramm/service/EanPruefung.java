package org.example.kalkulationsprogramm.service;

/** Prüft Strichcode-Nummern (EAN-8, UPC-A, EAN-13, GTIN-14) auf Länge, Ziffern und Prüfziffer. */
public final class EanPruefung {

    private EanPruefung() {
    }

    /** Gibt die bereinigte Nummer zurück (ohne Leerzeichen) oder {@code null} bei leerer Eingabe. */
    public static String normalisiert(String eingabe) {
        if (eingabe == null) {
            return null;
        }
        String s = eingabe.replaceAll("\\s++", "");
        return s.isEmpty() ? null : s;
    }

    /** {@code null} wenn gültig, sonst eine Meldung für den Anwender. */
    public static String fehler(String ean) {
        if (ean == null) {
            return null;
        }
        if (!ean.matches("[0-9]++")) {
            return "Die EAN darf nur Ziffern enthalten.";
        }
        int n = ean.length();
        if (n != 8 && n != 12 && n != 13 && n != 14) {
            return "Die EAN hat " + n + " Stellen, erlaubt sind 8, 12, 13 oder 14.";
        }
        int summe = 0;
        for (int i = 0; i < n - 1; i++) {
            int ziffer = ean.charAt(n - 2 - i) - '0';
            summe += (i % 2 == 0) ? ziffer * 3 : ziffer;
        }
        int pruef = (10 - summe % 10) % 10;
        return pruef == ean.charAt(n - 1) - '0' ? null : "Die Prüfziffer der EAN stimmt nicht, bitte die Nummer prüfen.";
    }
}
