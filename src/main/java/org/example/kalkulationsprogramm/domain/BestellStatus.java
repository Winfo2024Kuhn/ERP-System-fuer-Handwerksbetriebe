package org.example.kalkulationsprogramm.domain;

/**
 * Lebenszyklus einer {@link Bestellung}.
 *
 * <pre>
 *   ENTWURF ──▶ VERSENDET ──▶ TEIL_GELIEFERT ──▶ GELIEFERT
 *        │          │                │
 *        └──────────┴────────────────┴──▶ STORNIERT
 * </pre>
 *
 * Wichtig fuer IDS / IDS-CONNECT: ENTWURF ist intern („Warenkorb"),
 * erst VERSENDET entspricht einer an den Lieferanten uebertragenen
 * Bestellung. TEIL_GELIEFERT und GELIEFERT werden durch die
 * Wareneingangskontrolle (DIN EN 1090-2) gesetzt.
 */
public enum BestellStatus {
    ENTWURF,
    VERSENDET,
    TEIL_GELIEFERT,
    GELIEFERT,
    STORNIERT
}
