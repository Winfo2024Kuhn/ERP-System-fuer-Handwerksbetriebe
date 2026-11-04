package org.example.kalkulationsprogramm.repository;

/**
 * Eine Zeile des E-Mail-Reiters: ein Verlauf.
 *
 * @param wurzelId oberste Mail der Kette über {@code parent_email_id} (bei einem Kreis die
 *                 kleinste ID im Kreis)
 * @param emailId  angezeigte Mail: die neueste passende Mail des Bereichs in diesem Verlauf
 */
public record EmailVerlaufZeile(Long wurzelId, Long emailId) {
}
