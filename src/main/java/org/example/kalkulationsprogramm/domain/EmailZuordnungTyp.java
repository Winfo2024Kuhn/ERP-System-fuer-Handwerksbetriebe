package org.example.kalkulationsprogramm.domain;

/**
 * Typ der Zuordnung einer Email – der Vorgang, zu dem sie fachlich gehört.
 *
 * <p>Exklusiv: Eine Email gehört zu genau EINEM Vorgang.
 *
 * <p>Ausnahme: Zusätzlich zu PROJEKT oder ANFRAGE kann ein Lieferanten-Verweis gesetzt
 * sein, wenn der Schriftpartner ein Lieferant ist (siehe
 * {@code Email#verknuepfeLieferantZusaetzlich}). Der Typ bleibt dabei PROJEKT bzw.
 * ANFRAGE – die Mail taucht nur zusätzlich auf der Lieferanten-Karte auf.
 */
public enum EmailZuordnungTyp {
    PROJEKT,
    ANFRAGE,
    LIEFERANT,
    STEUERBERATER,
    KEINE
}
