package org.example.kalkulationsprogramm.dto;

import java.util.List;

import lombok.Data;

/**
 * Repräsentiert eine einzelne E-Mail im Konversationsverlauf.
 */
@Data
public class EmailThreadEntryDto {
    private Long id;
    private String subject;
    private String fromAddress;
    private String recipient;
    private String sentAt;          // ISO-8601 String, z.B. "2026-03-10T09:14:00"
    private String direction;       // "IN" oder "OUT"
    private String snippet;         // erste ~120 Zeichen des body für kollabierte Ansicht
    private String htmlBody;        // vollständiger HTML-Body (mit rewritten CID-URLs) für expandierte Ansicht
    private boolean forwarded;      // true wenn die E-Mail eine Weiterleitung ist (Fwd:/WG:)
    private List<AttachmentDto> attachments;

    @Data
    public static class AttachmentDto {
        private Long id;
        private String originalFilename;
        private String mimeType;
        private Long sizeBytes;
        private String contentId;
        private boolean inline;
    }
}
