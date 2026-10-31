package org.example.kalkulationsprogramm.domain.ids;

/**
 * Wie das ERP mit dem Shop eines Lieferanten spricht. Jeder Wert hat genau einen
 * {@link org.example.kalkulationsprogramm.service.ids.IdsProtokollAdapter}; ein neuer
 * Großhändler mit eigener Spielart braucht nur einen neuen Wert und einen neuen Adapter.
 */
public enum IdsProtokoll {
    /** IDS-Connect 2.5 (ITEK/ZVSHK) mit {@code action=WKE} und {@code version=2.5}, z. B. Berner, Sonepar. */
    IDS_CONNECT_2_5,
    /**
     * Wie IDS-Connect 2.5, schickt beim Öffnen aber nur die Zugangsfelder {@code kndnr}, {@code name_kunde},
     * {@code pw_kunde} und {@code hookurl} – so steht es im IDS-Zugangsblatt von Würth.
     */
    IDS_NUR_ZUGANGSFELDER
}
