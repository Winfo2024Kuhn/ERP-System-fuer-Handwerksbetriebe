package org.example.kalkulationsprogramm.domain;

/**
 * Typ einer {@link LangzeitkrankmeldungPhase}. Ein Phasenwechsel ist immer
 * das Anlegen einer neuen Phase, nie ein Statuswechsel an der Meldung selbst.
 */
public enum LangzeitkrankmeldungPhaseTyp {
    /** Lohnfortzahlung durch den Betrieb (die ersten sechs Wochen). */
    LOHNFORTZAHLUNG,
    /** Krankengeld der Krankenkasse. */
    KRANKENGELD,
    /** Wiedereingliederung mit reduzierten Stunden pro Tag. */
    WIEDEREINGLIEDERUNG
}
