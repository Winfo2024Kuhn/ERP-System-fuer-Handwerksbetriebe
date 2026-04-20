package org.example.kalkulationsprogramm.dto.Email;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

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

    @JsonProperty("isStarred")
    private boolean isStarred;

    // Metadaten
    private String direction; // IN, OUT
    private String zuordnungTyp; // KEINE, PROJEKT, ANFRAGE, LIEFERANT

    // Zuordnungs-Info
    private Long projektId;
    private String projektName;
    private Long anfrageId;
    private String anfrageName;
    private Long lieferantId;
    private String lieferantName;

    /**
     * Optionale Ruecklink-Info auf eine Preisanfrage-Lieferanten-Antwort.
     * Wird gesetzt, wenn die E-Mail ueber parentEmail/Token einem
     * {@code PreisanfrageLieferant} zugeordnet wurde. Ermoeglicht dem
     * EmailCenter ein Badge "Preisanfrage PA-YYYY-NNN" + Quick-Action
     * "Preise eintragen".
     */
    private PreisanfrageLieferantRef preisanfrageLieferantRef;

    // Ordner-Zuordnung (computed)
    private String folder;

    // Spam
    private Integer spamScore;

    // Thread-Informationen
    /** ID der übergeordneten E-Mail; null für Thread-Wurzeln (Root-Emails). */
    private Long parentEmailId;
    /** Anzahl direkter Antworten auf diese E-Mail. 0 = keine Antworten vorhanden. */
    private int replyCount;

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
        private boolean inline;
    }

    /**
     * Leichte Referenz auf eine Preisanfrage-Lieferant-Antwort.
     * Nur die IDs und die menschenlesbare Nummer werden exponiert, keine
     * Entity-Felder. Ermoeglicht dem Frontend direkt zu navigieren.
     */
    @Data
    public static class PreisanfrageLieferantRef {
        private Long preisanfrageId;
        private String preisanfrageNummer;
        private Long palId;
        private Long lieferantId;
        private String lieferantenname;
    }
}
