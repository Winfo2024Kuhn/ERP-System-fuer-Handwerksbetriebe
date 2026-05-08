package org.example.kalkulationsprogramm.domain;

/**
 * Punchout-Protokoll-Variante eines Lieferanten.
 * <p>
 * IDS-Connect 2.5 (ZVSHK) ist der ZVSHK-Standard, den die meisten
 * deutschen Großhändler unterstützen (Berner, Reyher, Sonepar, …).
 * Würth fährt einen älteren {@code ViewIDSCatalogService-IDSInBound}-
 * Endpunkt mit kleingeschriebenen Feldnamen und {@code multipart/
 * form-data} — dafür gibt es das eigene Profil
 * {@link #WUERTH_LEGACY}. OCI 4.0 (SAP) ist ein zukünftiger
 * Erweiterungspunkt für Lieferanten ohne IDS.
 */
public enum IdsProtokoll {
    IDS_CONNECT_2_5,
    WUERTH_LEGACY,
    OCI_4_0
}
