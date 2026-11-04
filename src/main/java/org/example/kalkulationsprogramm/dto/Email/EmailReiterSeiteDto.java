package org.example.kalkulationsprogramm.dto.Email;

import java.util.List;

/**
 * Eine Seite des E-Mail-Reiters ({@code GET /api/emails/reiter/{bezug}/{id}}).
 *
 * @param eintraege     Verläufe dieser Seite, neueste zuerst
 * @param gesamt        Verläufe passend zu Suche und Richtung (für „25 von 140 angezeigt“)
 * @param seite         Seite ab 0 (nach Begrenzung)
 * @param seitenGroesse Verläufe je Seite (nach Begrenzung, 1 bis 50)
 */
public record EmailReiterSeiteDto(List<EmailReiterEintragDto> eintraege, long gesamt, int seite,
        int seitenGroesse) {
}
