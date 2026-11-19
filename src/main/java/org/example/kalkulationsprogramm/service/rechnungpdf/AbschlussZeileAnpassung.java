package org.example.kalkulationsprogramm.service.rechnungpdf;

import org.example.kalkulationsprogramm.util.DruckTextBegrenzung;

/**
 * Vom Handwerker im Editor angepasste Zeile im Abschnitt „Abrechnungsstand“ des
 * Rechnungs-PDFs: die Zeile „diese Rechnung“ bzw. die Differenzzeile „Zusätzliche Leistungen“ /
 * „Nicht angefallen“. Dient zugleich als Teil der JSON-Anfrage an den PDF-Generator
 * ({@code abrechnungsverlauf.dieseRechnungZeile} / {@code .differenzZeile}) und als Teil des
 * PDF-DTOs.
 *
 * <p>Jedes Feld ist optional: Leere Texte bedeuten „automatischer Text wie bisher“,
 * {@code ausblenden == null} bedeutet „drucken“. Ausblenden entfernt nur die Zeile; alle
 * Betraege (Rest, Zahlbetrag, Steuer, Balken) bleiben unveraendert.
 *
 * <p>Die Texte werden beim Anlegen bereinigt (Steuerzeichen zu Leerzeichen, getrimmt) und
 * gekuerzt (Bezeichnung {@value #BEZEICHNUNG_MAX_LAENGE}, Unterzeile
 * {@value #UNTERZEILE_MAX_LAENGE} Zeichen, mit „…“). Sie bleiben Klartext.
 *
 * @param bezeichnung eigene Bezeichnung statt des automatischen Textes
 * @param unterzeile  eigene Unterzeile statt der automatischen
 * @param ausblenden  {@code true}: Zeile nicht drucken
 */
public record AbschlussZeileAnpassung(String bezeichnung, String unterzeile, Boolean ausblenden) {

    public static final int BEZEICHNUNG_MAX_LAENGE = 80;
    public static final int UNTERZEILE_MAX_LAENGE = 200;

    public AbschlussZeileAnpassung {
        bezeichnung = DruckTextBegrenzung.begrenze(bezeichnung, BEZEICHNUNG_MAX_LAENGE);
        unterzeile = DruckTextBegrenzung.begrenze(unterzeile, UNTERZEILE_MAX_LAENGE);
    }

    /** Zeile soll nicht gedruckt werden; ohne Anpassung immer {@code false}. */
    public static boolean ausgeblendet(AbschlussZeileAnpassung anpassung) {
        return anpassung != null && Boolean.TRUE.equals(anpassung.ausblenden());
    }

    /** Eigene Bezeichnung, sonst der automatische Text. */
    public static String bezeichnungOder(AbschlussZeileAnpassung anpassung, String automatisch) {
        return anpassung != null && anpassung.bezeichnung() != null ? anpassung.bezeichnung() : automatisch;
    }

    /** Eigene Unterzeile, sonst die automatische (darf {@code null} sein). */
    public static String unterzeileOder(AbschlussZeileAnpassung anpassung, String automatisch) {
        return anpassung != null && anpassung.unterzeile() != null ? anpassung.unterzeile() : automatisch;
    }
}
