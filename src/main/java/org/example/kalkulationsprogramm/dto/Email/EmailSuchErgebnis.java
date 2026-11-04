package org.example.kalkulationsprogramm.dto.Email;

import java.util.List;


/**
 * Eine Seite der E-Mail-Suche ({@code GET /api/emails/suche}). Ohne Gesamtzahl: {@code hatMehr}
 * sagt, ob es eine weitere Seite gibt (keine teure Zählabfrage).
 *
 * @param eintraege     Treffer wie in den Ordnerlisten, {@code folder} gesetzt
 * @param hatMehr       gibt es nach dieser Seite noch Treffer?
 * @param seite         Seite ab 0 (nach Begrenzung)
 * @param seitenGroesse Einträge je Seite (nach Begrenzung, 1 bis 50)
 */
public record EmailSuchErgebnis(List<UnifiedEmailDto> eintraege, boolean hatMehr, int seite, int seitenGroesse) {
}
