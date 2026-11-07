package org.example.kalkulationsprogramm.service.kassenbuchpdf;

import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKategorie;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Texte und Beträge, wie sie im Kassenbuch-PDF stehen (deutsche Zahlen, Gegenkonto,
 * Verwendungszweck, Kategorie). Gemeinsam für Journal, Belegbilder und Kopf des PDFs.
 */
public final class KassenbuchTexte {

    private KassenbuchTexte() {
    }

    /** Deutsche Formatierung mit zwei Nachkommastellen, z. B. „1.234,56“; {@code null} = 0,00. */
    public static String formatEuro(BigDecimal v) {
        BigDecimal x = v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
        return String.format(Locale.GERMAN, "%,.2f", x);
    }

    static String formatProzent(BigDecimal satz) {
        return satz.stripTrailingZeros().toPlainString().replace('.', ',') + " %";
    }

    public static BigDecimal nullSafe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    static String kuerze(String s, int max) {
        if (s == null || s.isBlank()) return "–";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    /** Erste und letzte Stellen eines Hashes -- lang genug zum Wiedererkennen, kurz genug fuers Auge. */
    public static String kurz(String hash) {
        if (hash == null || hash.length() < 16) return nullToEmpty(hash);
        return hash.substring(0, 8) + "…" + hash.substring(hash.length() - 8);
    }

    public static String joinNonEmpty(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(p.trim());
        }
        return sb.toString();
    }

    static String verwendungszweck(Beleg b) {
        if (b.getBeschreibung() != null && !b.getBeschreibung().isBlank()) {
            return b.getBeschreibung().trim();
        }
        if (b.getLieferant() != null && b.getLieferant().getLieferantenname() != null) {
            return b.getLieferant().getLieferantenname();
        }
        return kategorieLabel(b.getBelegKategorie());
    }

    /**
     * Gegenkonto in der Form "4930 Bürobedarf". Ohne Kontierung steht dort
     * ein deutlicher Platzhalter -- der Steuerberater sieht dann sofort,
     * wo er noch nacharbeiten muss.
     */
    static String gegenkonto(Beleg b) {
        if (b.getSachkonto() == null) return "noch offen";
        String nummer = b.getSachkonto().getNummer();
        String bez = b.getSachkonto().getBezeichnung();
        return joinNonEmpty(" ", nummer, kuerze(bez, 18));
    }

    /**
     * Steuerbetrag der Buchung. Bevorzugt aus der Differenz brutto minus
     * netto -- das ist der Wert, den der Buchhalter tatsaechlich geprueft
     * hat. Fehlt der Nettobetrag, wird aus dem Steuersatz herausgerechnet.
     */
    static BigDecimal mwstBetrag(Beleg b) {
        BigDecimal brutto = b.getBetragBrutto();
        if (brutto == null) return null;
        BigDecimal netto = b.getBetragNetto();
        if (netto != null) {
            return brutto.subtract(netto).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal satz = b.getMwstSatz();
        if (satz == null || satz.signum() <= 0) return null;
        BigDecimal faktor = BigDecimal.ONE.add(satz.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        BigDecimal errechnetesNetto = brutto.divide(faktor, 2, RoundingMode.HALF_UP);
        return brutto.subtract(errechnetesNetto).setScale(2, RoundingMode.HALF_UP);
    }

    static String kategorieLabel(BelegKategorie k) {
        if (k == null) return "–";
        return switch (k) {
            case UNZUGEORDNET    -> "Unzugeordnet";
            case KASSE_EINNAHME  -> "Kasse · Einnahme";
            case KASSE_AUSGABE   -> "Kasse · Ausgabe";
            case PRIVATENTNAHME  -> "Privatentnahme";
            case PRIVATEINLAGE   -> "Privateinlage";
            case BANK            -> "Bank";
            case KREDITKARTE     -> "Kreditkarte";
            case SONSTIGER_BELEG -> "Sonstiger Beleg";
        };
    }
}
