package org.example.kalkulationsprogramm.domain;

/** Art eines Telefonanrufs aus Sicht des Betriebs. */
public enum TelefonAnrufArt {
    ANGENOMMEN,
    /** Vom Anrufbeantworter entgegengenommen – niemand hat abgehoben. */
    ANRUFBEANTWORTER,
    VERPASST,
    AUSGEHEND,
    ABGEWIESEN
}
