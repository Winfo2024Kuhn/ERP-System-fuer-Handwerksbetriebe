package org.example.kalkulationsprogramm.dto.Postfach;

/** Eintrag der Auswahl „Senden von“ beim Schreiben einer neuen Mail. */
public record AbsenderPostfachDto(
        Long id,
        String emailAdresse,
        String anzeigename,
        boolean eigenes,
        boolean hauptpostfach) {
}
