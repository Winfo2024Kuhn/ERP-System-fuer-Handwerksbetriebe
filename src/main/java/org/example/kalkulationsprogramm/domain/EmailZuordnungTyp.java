package org.example.kalkulationsprogramm.domain;

/**
 * Typ der Zuordnung einer Email.
 * Exklusiv: Eine Email kann nur zu EINER Entität gehören.
 */
public enum EmailZuordnungTyp {
    PROJEKT,
    ANGEBOT,
    LIEFERANT,
    STEUERBERATER,
    KEINE
}
