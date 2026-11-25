package org.example.kalkulationsprogramm.domain;

/**
 * Herkunft eines Eintrags im FRITZ!Box-Telefonbuch „ERP-Kontakte“: aus welcher
 * Tabelle der Kontakt stammt. Zusammen mit der ID eindeutig.
 */
public enum TelefonbuchQuelle {
    KUNDE,
    LIEFERANT,
    LIEFERANT_ANSPRECHPARTNER,
    MITARBEITER,
    STEUERBERATER,
    STEUERBERATER_ANSPRECHPARTNER
}
