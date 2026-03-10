package org.example.kalkulationsprogramm.dto.Email;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Unified Email Response DTO für das neue Email-System.
 */
@Data
public class UnifiedEmailDto {
    private Long id;
    private String messageId;

    // Absender/Empfänger
    private String fromAddress;
    private String senderDomain;
    private String recipient;
    private String cc;

    // Inhalt
    private String subject;
    private String body;
    private String htmlBody;

    // Zeitstempel
    private LocalDateTime sentAt;
    private LocalDateTime firstViewedAt;

    @JsonProperty("isRead")
    private boolean isRead; // Computed: firstViewedAt != null

    // Metadaten
    private String direction; // IN, OUT
    private String zuordnungTyp; // KEINE, PROJEKT, ANGEBOT, LIEFERANT

    // Zuordnungs-Info
    private Long projektId;
    private String projektName;
    private Long angebotId;
    private String angebotName;
    private Long lieferantId;
    private String lieferantName;

    // Anhänge
    private List<AttachmentDto> attachments;
    private boolean hasAttachments;

    @Data
    public static class AttachmentDto {
        private Long id;
        private String originalFilename;
        private String mimeType;
        private Long fileSize;
        private String contentId;
    }
}
