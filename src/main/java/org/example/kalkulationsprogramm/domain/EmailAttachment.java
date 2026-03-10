package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Attachment einer Email.
 * 
 * Ersetzt die alten Tabellen: projekt_email_files, angebot_email_file, lieferant_email_attachments
 */
@Entity
@Table(name = "email_attachment", indexes = {
    @Index(name = "idx_attachment_email", columnList = "email_id"),
    @Index(name = "idx_attachment_ai", columnList = "aiProcessed")
})
@Getter
@Setter
@NoArgsConstructor
public class EmailAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "email_id", nullable = false)
    private Email email;

    // ═══════════════════════════════════════════════════════════════
    // DATEI-INFORMATIONEN
    // ═══════════════════════════════════════════════════════════════

    /**
     * Ursprünglicher Dateiname aus der E-Mail.
     */
    @Column(length = 500)
    private String originalFilename;

    /**
     * Gespeicherter Dateiname (UUID-basiert).
     */
    @Column(length = 500)
    private String storedFilename;

    /**
     * Content-ID für Inline-Bilder (z.B. in Signatur).
     */
    @Column(length = 255)
    private String contentId;

    /**
     * Ist dies ein Inline-Attachment (z.B. Bild in Signatur)?
     */
    private Boolean inlineAttachment = false;

    /**
     * MIME-Typ (z.B. "application/pdf", "image/png").
     */
    @Column(length = 255)
    private String mimeType;

    /**
     * Dateigröße in Bytes.
     */
    private Long sizeBytes;

    // ═══════════════════════════════════════════════════════════════
    // KI-VERARBEITUNG (für Lieferanten-Dokumente)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Wurde die KI-Analyse bereits durchgeführt?
     */
    private Boolean aiProcessed = false;

    /**
     * Wann wurde die KI-Analyse durchgeführt?
     */
    private LocalDateTime aiProcessedAt;

    /**
     * Falls KI eine Rechnung/AB etc. erkannt hat: Referenz auf LieferantDokument.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_dokument_id")
    private LieferantDokument lieferantDokument;

    // ═══════════════════════════════════════════════════════════════
    // HILFSMETHODEN
    // ═══════════════════════════════════════════════════════════════

    /**
     * Prüft ob dies eine PDF-Datei ist.
     */
    public boolean isPdf() {
        return originalFilename != null && originalFilename.toLowerCase().endsWith(".pdf");
    }

    /**
     * Prüft ob dies eine XML-Datei ist.
     */
    public boolean isXml() {
        return originalFilename != null && originalFilename.toLowerCase().endsWith(".xml");
    }

    /**
     * Prüft ob dies ein Bild ist.
     */
    public boolean isImage() {
        if (mimeType != null) {
            return mimeType.startsWith("image/");
        }
        if (originalFilename != null) {
            String lower = originalFilename.toLowerCase();
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg") 
                || lower.endsWith(".png") || lower.endsWith(".gif");
        }
        return false;
    }

    /**
     * Prüft ob dies ein Inline-Attachment ist (z.B. eingebettetes Bild).
     */
    public boolean isInline() {
        return Boolean.TRUE.equals(inlineAttachment);
    }

    /**
     * Markiert dieses Attachment als KI-verarbeitet.
     */
    public void markAsAiProcessed(LieferantDokument dokument) {
        this.aiProcessed = true;
        this.aiProcessedAt = LocalDateTime.now();
        this.lieferantDokument = dokument;
    }
}
