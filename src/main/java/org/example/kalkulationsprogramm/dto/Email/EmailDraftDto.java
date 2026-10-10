package org.example.kalkulationsprogramm.dto.Email;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Draft content and attachment metadata. Binary content is downloaded separately.
 *
 * @param postfachId    gewähltes Absender-Postfach einer neuen Mail ({@code null} = Vorbelegung)
 * @param einzelversand Sammel-Mail: jeder Empfänger bekommt eine eigene Mail
 */
public record EmailDraftDto(Long id, String recipient, String cc, String subject, String body,
        String fromAddress, Long replyEmailId, Long projektId, Long anfrageId,
        Boolean geschaeftsdokument, Long postfachId, Boolean einzelversand,
        LocalDateTime createdAt, LocalDateTime updatedAt,
        List<Attachment> attachments) {

    /** Entwurf ohne Postfach-Angaben (wie vor den Postfächern). */
    public EmailDraftDto(Long id, String recipient, String cc, String subject, String body,
            String fromAddress, Long replyEmailId, Long projektId, Long anfrageId,
            Boolean geschaeftsdokument, LocalDateTime createdAt, LocalDateTime updatedAt,
            List<Attachment> attachments) {
        this(id, recipient, cc, subject, body, fromAddress, replyEmailId, projektId, anfrageId,
                geschaeftsdokument, null, null, createdAt, updatedAt, attachments);
    }

    public record Attachment(Long id, String filename, String contentType, long size) { }
}
