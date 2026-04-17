package org.example.kalkulationsprogramm.domain;

public enum ArtikelVorschlagTyp {
    /** KI hat keinen Treffer gefunden — neuer Artikel soll angelegt werden. */
    NEU_ANLAGE,
    /** KI hat Treffer gefunden, Konfidenz unter Auto-Match-Schwelle — Nutzer soll bestätigen. */
    MATCH_VORSCHLAG,
    /** Externe Nummer zeigt bereits auf einen anderen Artikel beim selben Lieferanten. */
    KONFLIKT_EXTERNE_NUMMER
}
