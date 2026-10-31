package org.example.kalkulationsprogramm.domain;

/**
 * Wie weit der Spam-Abgleich eine Mail in einem Postfach auf dem Mail-Server bewegt hat.
 * {@code null} an der Zuordnung heißt: liegt dort, wo der Import sie gefunden hat.
 */
public enum ServerSpamStatus {

    /** Vom ERP in den Spam-Ordner des Servers verschoben – „Kein Spam“ holt sie zurück. */
    VERSCHOBEN,

    /** Auf dem Server nicht (mehr) gefunden, z. B. am Handy gelöscht – wird nicht erneut versucht. */
    NICHT_GEFUNDEN,

    /** Der Server hat das Verschieben mehrfach abgelehnt – wird nicht erneut versucht. */
    FEHLGESCHLAGEN
}
