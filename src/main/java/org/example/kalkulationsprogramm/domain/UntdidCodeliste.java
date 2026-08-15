package org.example.kalkulationsprogramm.domain;

/**
 * Zentrale Codeliste fuer UNTDID 1001 (Document Name Code), den TypeCode aus
 * ZUGFeRD-/XRechnung-XML.
 *
 * <p>Bildet nur die Codes ab, die im Alltag eines Handwerksbetriebs tatsaechlich
 * vorkommen:
 * <ul>
 * <li>{@code 380, 384, 389, 385, 386, 326, 875, 876, 877} -> {@link LieferantDokumentTyp#RECHNUNG}
 * (Rechnung, Korrektur, Eigenrechnung, Sammelrechnung, Vorauszahlung, Teilrechnung,
 * Bau-Abschlagsrechnungen)</li>
 * <li>{@code 381} -> {@link LieferantDokumentTyp#GUTSCHRIFT}</li>
 * <li>{@code 310} -> {@link LieferantDokumentTyp#ANGEBOT}</li>
 * <li>{@code 231} -> {@link LieferantDokumentTyp#AUFTRAGSBESTAETIGUNG}</li>
 * <li>{@code 351, 261, 270} -> {@link LieferantDokumentTyp#LIEFERSCHEIN} (351 = Despatch
 * advice, also Lieferavis - nicht Angebot, auch wenn das frueher im Code falsch
 * zugeordnet war)</li>
 * </ul>
 *
 * <p>Alle anderen Codes ergeben bewusst {@code null} statt eines geratenen Typs:
 * Diese Zuordnung wirkt auf die automatische Preisuebernahme aus Lieferanten-Dokumenten,
 * und ein falsch geratener Typ wuerde daraus falsche Kalkulationsdaten machen. Lieber kein
 * Ergebnis als ein falsches.
 */
public final class UntdidCodeliste {

    private UntdidCodeliste() {
        // reine Codeliste, keine Bean
    }

    /**
     * Ordnet einen UNTDID-1001-TypeCode dem passenden {@link LieferantDokumentTyp} zu.
     *
     * @param typeCode der TypeCode aus dem ZUGFeRD-/XRechnung-XML, darf {@code null} sein
     * @return der zugeordnete Dokumenttyp, oder {@code null} bei unbekanntem oder fehlendem Code
     */
    public static LieferantDokumentTyp typFuer(String typeCode) {
        if (typeCode == null) {
            return null;
        }
        return switch (typeCode.trim()) {
            // 380 Rechnung, 384 korrigiert, 389 Eigenrechnung, 385 Sammelrechnung,
            // 386 Vorauszahlung, 326 Teilrechnung, 875-877 Bau-Abschlagsrechnungen
            case "380", "384", "389", "385", "386", "326", "875", "876", "877" ->
                    LieferantDokumentTyp.RECHNUNG;
            case "381" -> LieferantDokumentTyp.GUTSCHRIFT;
            case "310" -> LieferantDokumentTyp.ANGEBOT;
            case "231" -> LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG;
            case "351", "261", "270" -> LieferantDokumentTyp.LIEFERSCHEIN;
            default -> null;
        };
    }
}
