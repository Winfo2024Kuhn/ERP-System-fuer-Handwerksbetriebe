package org.example.kalkulationsprogramm.dto.Email;

import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.EmailDirection;

/**
 * Eine Zeile im E-Mail-Reiter ({@code GET /api/emails/reiter/{bezug}/{id}}) = ein Verlauf.
 *
 * @param id                angezeigte Mail (neueste passende des Bereichs); öffnet den Verlauf über
 *                          {@code GET /api/emails/{id}/thread}
 * @param bodyHtml          HTML-Teil, sonst Klartext, sonst Rohtext, sonst {@code null} – ungefiltert,
 *                          das Frontend baut daraus nur die Vorschau
 * @param nachrichtenAnzahl Mails im ganzen Verlauf, die der Benutzer öffnen darf (mindestens 1)
 * @param mitAnhang         hat eine Mail des Bereichs im Verlauf einen nicht eingebetteten Anhang
 *                          (beim Lieferanten nur Anhänge sichtbarer Dokumenttypen)?
 * @param zustellStatus     {@code UNZUSTELLBAR}, wenn irgendeine Ausgangsmail des Verlaufs nicht
 *                          ankam; sonst der Status der angezeigten Mail ({@code OFFEN} oder {@code null})
 * @param zustellFehler     Grund dazu (bei {@code UNZUSTELLBAR} der der neuesten nicht angekommenen Mail)
 */
public record EmailReiterEintragDto(Long id, String subject, String fromAddress, String recipient,
        EmailDirection direction, LocalDateTime sentAt, String bodyHtml, int nachrichtenAnzahl, boolean mitAnhang,
        String zustellStatus, String zustellFehler) {
}
