package org.example.kalkulationsprogramm.domain;

/**
 * Wie sicher die automatische Spam-Erkennung ist. Nur {@link #SPAM} setzt {@code isSpam} und
 * verschiebt die Mail damit auch auf dem Mail-Server. {@code null} an der Mail heißt: vor V444
 * bewertet oder von Hand entschieden ({@code userSpamVerdict}).
 */
public enum SpamStufe {

    /** Sehr sicher Spam – wandert in den Spam-Ordner, auch auf dem Server. */
    SPAM,

    /** Verdächtig, aber nicht sicher – bleibt im Posteingang mit Hinweis „Vielleicht Spam“. */
    UNSICHER,

    /** Kein Spam. */
    KEIN_SPAM
}
