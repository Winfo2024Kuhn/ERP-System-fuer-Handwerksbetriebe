package org.example.kalkulationsprogramm.dto.Abwesenheitsnotiz;

import org.example.kalkulationsprogramm.domain.EmailAbsender;

/**
 * Postfach, für das der angemeldete Benutzer eine Abwesenheitsnotiz einstellen darf.
 *
 * @param eigenes {@code true} beim eigenen Postfach des Benutzers
 */
public record AbwesenheitsPostfachDto(Long id, String emailAdresse, String anzeigename, boolean eigenes) {

    public static AbwesenheitsPostfachDto von(EmailAbsender postfach, boolean eigenes) {
        return new AbwesenheitsPostfachDto(postfach.getId(), postfach.getEmailAdresse(),
                postfach.getAnzeigename(), eigenes);
    }
}
