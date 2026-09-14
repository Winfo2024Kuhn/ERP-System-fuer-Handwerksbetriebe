package org.example.kalkulationsprogramm.dto.Email;

import java.time.LocalDateTime;
import java.util.List;

/** Draft content and attachment metadata. Binary content is downloaded separately. */
public record EmailDraftDto(Long id, String recipient, String cc, String subject, String body,
        String fromAddress, Long replyEmailId, Long projektId, Long anfrageId,
        Boolean geschaeftsdokument, LocalDateTime createdAt, LocalDateTime updatedAt,
        List<Attachment> attachments) {
    public record Attachment(Long id, String filename, String contentType, long size) { }
}
