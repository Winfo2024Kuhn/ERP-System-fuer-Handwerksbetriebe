package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.EmailBezug;
import org.example.kalkulationsprogramm.domain.EmailDirection;

/**
 * Eine Seite des E-Mail-Reiters, fertig für {@link EmailReiterRepositoryErweiterung}.
 *
 * @param bezug                Projekt, Anfrage, Kunde oder Lieferant
 * @param bezugId              ID des Datensatzes
 * @param woerter              Suchwörter als fertige LIKE-Muster ({@code %wort%}, Platzhalter mit
 *                             {@code \} maskiert); alle müssen passen (UND), leer = keine Suche
 * @param richtung             nur Eingang bzw. Ausgang; {@code null} = beide
 * @param sichtbarePostfachIds Postfächer, die der Benutzer sieht; {@code null} = alles (Admin)
 * @param offset               erster Verlauf (Seite × Seitengröße)
 * @param limit                höchstens so viele Verläufe
 */
public record EmailReiterAbfrage(EmailBezug bezug, Long bezugId, List<String> woerter, EmailDirection richtung,
        Set<Long> sichtbarePostfachIds, int offset, int limit) {

    public EmailReiterAbfrage {
        woerter = woerter == null ? List.of() : List.copyOf(woerter);
        sichtbarePostfachIds = sichtbarePostfachIds == null ? null : Set.copyOf(sichtbarePostfachIds);
    }

    /** Alle Verläufe des Bereichs ohne Suche, Richtung und Postfach-Grenze (Zähler der Reiter). */
    public static EmailReiterAbfrage ohneFilter(EmailBezug bezug, Long bezugId) {
        return new EmailReiterAbfrage(bezug, bezugId, List.of(), null, null, 0, 1);
    }
}
