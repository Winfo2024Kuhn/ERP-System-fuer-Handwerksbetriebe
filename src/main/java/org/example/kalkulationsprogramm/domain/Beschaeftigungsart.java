package org.example.kalkulationsprogramm.domain;

/**
 * Beschaeftigungsart eines Mitarbeiters. Steuert, welche SV-Saetze in der
 * Lohnberechnung greifen. Werte exakt wie in der Spalte
 * mitarbeiter.beschaeftigungsart (siehe V297__mitarbeiter_sv_felder.sql).
 */
public enum Beschaeftigungsart {
    /** Voller AG-/AN-Anteil KV/PV/RV/AV. */
    REGULAER("Regulaer sozialversicherungspflichtig"),
    /** Pauschale AG-Abgaben (Minijob bis 520 EUR/Monat), kein AN-Anteil. */
    MINIJOB("Minijob (geringfuegig beschaeftigt)"),
    /** Fremdgeschaeftsfuehrer, voll versicherungspflichtig. */
    GF_SV_PFLICHTIG("Geschaeftsfuehrer (sozialversicherungspflichtig)"),
    /** Beherrschender Gesellschafter-GF, keine SV-Pflicht. */
    GF_SV_FREI("Geschaeftsfuehrer (sozialversicherungsfrei)");

    private final String bezeichnung;

    Beschaeftigungsart(String bezeichnung) {
        this.bezeichnung = bezeichnung;
    }

    public String getBezeichnung() {
        return bezeichnung;
    }
}
