package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.BelegQuelle;
import org.example.kalkulationsprogramm.domain.KasseEinstellung;
import org.example.kalkulationsprogramm.domain.Sachkonto;
import org.example.kalkulationsprogramm.domain.SachkontoTyp;

/**
 * Doppik-Variante A: das Beleg-Modell behaelt genau EIN {@code Sachkonto},
 * die Gegen-Seite wird deterministisch aus {@code BelegKategorie + Sachkonto}
 * abgeleitet. So bleibt der Datenstand schlank, der Kassenbuch-PDF-Export kann
 * trotzdem klassisch Soll/Haben darstellen, wie es der Steuerberater erwartet.
 *
 * <p>Mapping (Issue #61):</p>
 * <table>
 *   <tr><th>Kategorie</th><th>Soll</th><th>Haben</th></tr>
 *   <tr><td>KASSE_EINNAHME + Ertrag-Konto</td><td>Kasse</td><td>Sachkonto</td></tr>
 *   <tr><td>KASSE_EINNAHME ohne Sachkonto</td><td>Kasse</td><td>Bank</td></tr>
 *   <tr><td>KASSE_AUSGABE + Aufwand-Konto</td><td>Sachkonto</td><td>Kasse</td></tr>
 *   <tr><td>KASSE_AUSGABE ohne Sachkonto</td><td>?</td><td>Kasse</td></tr>
 *   <tr><td>PRIVATEINLAGE</td><td>Kasse</td><td>Privateinlage</td></tr>
 *   <tr><td>PRIVATENTNAHME</td><td>Privatentnahme</td><td>Kasse</td></tr>
 * </table>
 *
 * <p>Robust: wenn ein Sachkonto fehlt, steht "?" statt einer NPE. Der Buchhalter
 * sieht im PDF sofort, wo nachgepflegt werden muss.</p>
 */
public final class BuchungssatzAbleitung {

    /** Label fuer die Kasse-Seite. Nicht "1600 Kasse" — der Steuerberater
     *  bekommt ohnehin den eigenen Kontenrahmen; im Kassenbuch zaehlt Klarheit. */
    public static final String KASSE = "Kasse";
    public static final String BANK = "Bank";
    public static final String PRIVATEINLAGE = "Privateinlage";
    public static final String PRIVATENTNAHME = "Privatentnahme";
    public static final String UNKLAR = "?";

    private BuchungssatzAbleitung() {
        // Utility-Klasse
    }

    /**
     * Liefert Soll/Haben-Labels fuer einen Beleg. Belege ohne Kassen-Bewegung
     * (BANK, KREDITKARTE, SONSTIGER_BELEG, UNZUGEORDNET) bekommen {@link #UNKLAR}
     * auf beiden Seiten — der Aufrufer entscheidet, ob er sie ueberhaupt
     * rendert.
     */
    public static Buchungssatz ableiten(Beleg beleg) {
        if (beleg == null || beleg.getBelegKategorie() == null) {
            return new Buchungssatz(UNKLAR, UNKLAR);
        }
        BelegKategorie kategorie = beleg.getBelegKategorie();
        String sachkontoLabel = sachkontoLabel(beleg.getSachkonto());

        return switch (kategorie) {
            case KASSE_EINNAHME -> new Buchungssatz(
                    KASSE,
                    beleg.getSachkonto() != null ? sachkontoLabel : BANK);
            case KASSE_AUSGABE -> new Buchungssatz(
                    beleg.getSachkonto() != null ? sachkontoLabel : UNKLAR,
                    KASSE);
            case PRIVATEINLAGE -> new Buchungssatz(
                    KASSE,
                    beleg.getSachkonto() != null ? sachkontoLabel : PRIVATEINLAGE);
            case PRIVATENTNAHME -> new Buchungssatz(
                    beleg.getSachkonto() != null ? sachkontoLabel : PRIVATENTNAHME,
                    KASSE);
            // Nicht-Kassen-Belege landen ueblicherweise nicht im Kassenbuch-Bereich
            // des PDFs — falls doch, kommt UNKLAR sichtbar an.
            case BANK, KREDITKARTE, SONSTIGER_BELEG, UNZUGEORDNET ->
                    new Buchungssatz(UNKLAR, UNKLAR);
        };
    }

    /** Kontonummern statt Labels für den DATEV-Buchungsstapel. */
    public static Konten ableitenKonten(Beleg beleg, KasseEinstellung einstellung) {
        if (beleg == null || beleg.getBelegKategorie() == null) return new Konten(null, null);
        String kasse = kontoOderStandard(einstellung == null ? null : einstellung.getKassenkontoNummer(), "1000");
        String bank = kontoOderStandard(einstellung == null ? null : einstellung.getBankkontoNummer(), "1200");
        String konto = beleg.getSachkonto() == null ? null : kontoOderStandard(beleg.getSachkonto().getNummer(), null);
        boolean transfer = istDatevTransfer(beleg);
        boolean storno = beleg.getStornoFuerBelegId() != null;
        return switch (beleg.getBelegKategorie()) {
            case KASSE_EINNAHME -> new Konten(kasse, transfer ? bank : konto);
            case KASSE_AUSGABE -> new Konten(transfer ? bank : konto, kasse);
            case PRIVATEINLAGE -> new Konten(kasse, kontoOderStandard(konto, storno ? "1800" : "1810"));
            case PRIVATENTNAHME -> new Konten(kontoOderStandard(konto, storno ? "1810" : "1800"), kasse);
            default -> new Konten(null, null);
        };
    }

    static boolean istDatevTransfer(Beleg beleg) {
        if (beleg.getQuelle() != BelegQuelle.TRANSFER) return false;
        // V375 setzte auch alte Gegenbuchungen (istUmbuchung) auf TRANSFER.
        // Ein übernommenes Aufwand-/Ertragskonto belegt ihren echten Ursprung.
        Sachkonto konto = beleg.getSachkonto();
        return beleg.getStornoFuerBelegId() == null || konto == null
                || (konto.getKontoTyp() != SachkontoTyp.AUFWAND && konto.getKontoTyp() != SachkontoTyp.ERTRAG);
    }

    private static String kontoOderStandard(String konto, String standard) {
        return konto == null || konto.isBlank() ? standard : konto.trim();
    }

    public record Konten(String sollKontoNr, String habenKontoNr) { }

    private static String sachkontoLabel(Sachkonto sk) {
        if (sk == null) return UNKLAR;
        String nr = sk.getNummer();
        String bz = sk.getBezeichnung();
        if (nr != null && !nr.isBlank() && bz != null && !bz.isBlank()) {
            return nr + " " + bz;
        }
        if (bz != null && !bz.isBlank()) return bz;
        if (nr != null && !nr.isBlank()) return nr;
        return UNKLAR;
    }

    /**
     * Einfaches Ergebnis-Record. Beide Seiten sind nie {@code null} —
     * fehlende Information wird als {@link #UNKLAR} ("?") signalisiert.
     */
    public record Buchungssatz(String soll, String haben) { }
}
