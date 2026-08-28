package org.example.kalkulationsprogramm.dto.Beitraege;

/**
 * Vorschlag der KI.
 *
 * @param titel            Ueberschrift des Beitrags
 * @param kurzbeschreibung Anrisstext fuer die Uebersichtsseite
 * @param text             REINER TEXT, kein HTML. Absaetze durch Leerzeilen,
 *                         Aufzaehlungen mit fuehrendem Bindestrich. Die
 *                         Umwandlung in die neun von der Website erlaubten
 *                         Tags macht das Frontend.
 * @param antwort          kurzer Satz fuer das Chatfenster
 */
public record BeitragKiEntwurf(
        String titel,
        String kurzbeschreibung,
        String text,
        String antwort) {
}
