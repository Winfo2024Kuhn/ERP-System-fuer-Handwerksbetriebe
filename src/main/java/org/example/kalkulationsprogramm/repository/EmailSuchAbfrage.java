package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.EmailOrdner;

/**
 * Eine Seite der E-Mail-Suche, fertig für {@link EmailSucheRepositoryErweiterung#sucheIds}.
 *
 * @param woerter               Suchwörter als fertige LIKE-Muster ({@code %wort%}, Platzhalter mit
 *                              {@code \} maskiert); alle müssen passen (UND)
 * @param ordner                Ordner der Suche, {@link EmailOrdner#ALLE} = alle Mails
 * @param sichtbarePostfachIds  Postfächer, die der Benutzer sieht; {@code null} = alles (Admin)
 * @param steuerberaterDomains  Domains für {@link EmailOrdner#TAX_ADVISORS} (klein geschrieben)
 * @param offset                erste Zeile (Seite × Seitengröße)
 * @param limit                 höchstens so viele IDs
 */
public record EmailSuchAbfrage(List<String> woerter, EmailOrdner ordner, Set<Long> sichtbarePostfachIds,
        Set<String> steuerberaterDomains, int offset, int limit) {

    public EmailSuchAbfrage {
        woerter = woerter == null ? List.of() : List.copyOf(woerter);
        sichtbarePostfachIds = sichtbarePostfachIds == null ? null : Set.copyOf(sichtbarePostfachIds);
        steuerberaterDomains = steuerberaterDomains == null ? Set.of() : Set.copyOf(steuerberaterDomains);
    }
}
