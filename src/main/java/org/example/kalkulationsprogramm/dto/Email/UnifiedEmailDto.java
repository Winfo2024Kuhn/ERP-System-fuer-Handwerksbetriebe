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
    /** Reply-To-Kopfzeile des Absenders; Antworten gehen dorthin statt an fromAddress. */
    private String replyToAddress;

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
    /** Auftragsnummer des zugeordneten Projekts – für den Link „Projekt 2026-041 · …“ im E-Mail-Center. */
    private String projektAuftragsnummer;
    private Long anfrageId;
    private String anfrageName;
    private Long lieferantId;
    private String lieferantName;
    /** Name des erkannten Kunden (per fromAddress/recipient gegen Kunde.kundenEmails gematcht). */
    private Long kundeId;
    private String kundeName;

    // Ordner-Zuordnung (computed)
    private String folder;

    // Spam
    private Integer spamScore;

    // Thread-Informationen
    /** ID der übergeordneten E-Mail; null für Thread-Wurzeln (Root-Emails). */
    private Long parentEmailId;
    /**
     * Wurzel des gesamten Verlaufs. Die Liste fasst danach zusammen – auch wenn
     * Zwischenglieder (z. B. die eigene Antwort im Ordner "Gesendet") nicht geladen sind.
     */
    private Long threadRootId;
    /** Anzahl weiterer Nachrichten im gesamten Verlauf (Verlaufsgröße - 1). 0 = Einzelmail. */
    private int replyCount;
    /**
     * Jüngster sentAt-Wert über alle E-Mails im gesamten Thread (Root + Kinder/Kindeskinder).
     * Damit kann das Frontend Threads nach letzter Aktivität sortieren, auch wenn nur die
     * Wurzel oder nur einzelne Mitglieder in der Liste geladen sind (z.B. Gesendet-Ordner,
     * der nur OUT-Mails enthält, aber wo eingehende Antworten den Thread nach oben holen sollen).
     */
    private LocalDateTime threadLastActivityAt;

    // Anhänge
    private List<AttachmentDto> attachments;
    private boolean hasAttachments;

    /**
     * Zustell-Ergebnis der Ausgangsmail ("OFFEN" / "UNZUSTELLBAR"). Nur bei
     * {@code direction == OUT} aussagekraeftig; bei "UNZUSTELLBAR" blendet das
     * Frontend eine Warnung ein.
     */
    private String zustellStatus;

    /** Klartext-Grund der Ablehnung, z.B. "unknown user". */
    private String zustellFehler;

    @Data
    public static class AttachmentDto {
        private Long id;
        private String originalFilename;
        private String mimeType;
        private Long fileSize;
        private String contentId;
        private boolean inline;
    }
}
