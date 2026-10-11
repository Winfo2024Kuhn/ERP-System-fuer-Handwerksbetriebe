package org.example.kalkulationsprogramm.dto.Postfach;

/**
 * Eintrag der Auswahl „Senden von“ beim Schreiben einer neuen Mail.
 *
 * <p>Die Liste dient im E-Mail-Center zugleich als Liste der eigenen Adressen (wer bei
 * „Allen antworten“ nicht mit angeschrieben wird). Deshalb steht auch das Postfach für
 * Rechnungen &amp; Mahnungen darin – mit {@code waehlbar = false}: Darüber gehen nur Dokumente
 * aus dem Dokument-Editor raus.</p>
 */
public record AbsenderPostfachDto(
        Long id,
        String emailAdresse,
        String anzeigename,
        boolean eigenes,
        boolean hauptpostfach,
        boolean waehlbar) {
}
