package org.example.kalkulationsprogramm.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Zentrale Email-Entity für alle eingehenden und ausgehenden E-Mails.
 * 
 * Ersetzt die alten Tabellen: projekt_email, anfrage_email, lieferant_email
 * 
 * Zuordnung ist exklusiv: Eine Email gehört zu genau EINER Entität (Projekt, Anfrage oder Lieferant).
 */
@Entity
@Table(name = "email", indexes = {
    @Index(name = "idx_email_message_id", columnList = "messageId", unique = true),
    @Index(name = "idx_email_sender_domain", columnList = "senderDomain"),
    @Index(name = "idx_email_direction", columnList = "direction"),
    @Index(name = "idx_email_zuordnung", columnList = "zuordnungTyp"),
    @Index(name = "idx_email_projekt", columnList = "projekt_id"),
    @Index(name = "idx_email_anfrage", columnList = "anfrage_id"),
    @Index(name = "idx_email_lieferant", columnList = "lieferant_id"),
    @Index(name = "idx_email_processing", columnList = "processingStatus"),
    @Index(name = "idx_email_sent_at", columnList = "sentAt")
})
@Getter
@Setter
@NoArgsConstructor
public class Email {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ═══════════════════════════════════════════════════════════════
    // IDENTIFIKATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * IMAP Message-ID (eindeutig).
     */
    @Column(length = 512, nullable = false, unique = true)
    private String messageId;

    // ═══════════════════════════════════════════════════════════════
    // ABSENDER / EMPFÄNGER
    // ═══════════════════════════════════════════════════════════════

    @Column(length = 255)
    private String fromAddress;

    /**
     * Extrahierte Domain aus fromAddress (z.B. "wuerth.com").
     */
    @Column(length = 255)
    private String senderDomain;

    @Column(length = 1000)
    private String recipient;

    @Column(length = 1000)
    private String cc;

    // ═══════════════════════════════════════════════════════════════
    // INHALT
    // ═══════════════════════════════════════════════════════════════

    @Column(length = 1000)
    private String subject;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String body;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String htmlBody;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String rawBody;

    // ═══════════════════════════════════════════════════════════════
    // ZEITSTEMPEL
    // ═══════════════════════════════════════════════════════════════

    private LocalDateTime sentAt;

    /**
     * Wann wurde die E-Mail im EmailCenter erstmals angesehen?
     * Für "Neue Mails" Kennzeichnung.
     */
    private LocalDateTime firstViewedAt;
    
    /**
     * Zeitstempel der Löschung (Papierkorb).
     * Wenn gesetzt, ist die Email im Papierkorb.
     */
    private LocalDateTime deletedAt;

    // ═══════════════════════════════════════════════════════════════
    // IMAP-METADATEN
    // ═══════════════════════════════════════════════════════════════

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailDirection direction;

    /**
     * IMAP-Ordner aus dem die E-Mail stammt.
     */
    @Column(length = 255)
    private String imapFolder;

    /**
     * IMAP UID für direkten Zugriff.
     */
    private Long imapUid;

    @Column(nullable = false)
    private boolean isRead = false;

    // ═══════════════════════════════════════════════════════════════
    // ZUORDNUNG (exklusiv: nur 1 darf gesetzt sein!)
    // ═══════════════════════════════════════════════════════════════

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailZuordnungTyp zuordnungTyp = EmailZuordnungTyp.KEINE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projekt_id")
    private Projekt projekt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anfrage_id")
    private Anfrage anfrage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id")
    private Lieferanten lieferant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "steuerberater_id")
    private SteuerberaterKontakt steuerberater;

    // ═══════════════════════════════════════════════════════════════
    // QUEUE-STATUS
    // ═══════════════════════════════════════════════════════════════

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailProcessingStatus processingStatus = EmailProcessingStatus.DONE;

    private LocalDateTime processedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    // ═══════════════════════════════════════════════════════════════
    // SPAM-FILTER
    // ═══════════════════════════════════════════════════════════════

    /**
     * Spam-Score von 0-100. Höhere Werte = wahrscheinlicher Spam.
     */
    @Column
    private Integer spamScore = 0;

    /**
     * Wurde die Email als Spam markiert?
     */
    @Column(nullable = false)
    private boolean isSpam = false;

    /**
     * User-Feedback für Supervised Learning: 'SPAM', 'HAM', oder null (kein Feedback).
     */
    @Column(length = 20)
    private String userSpamVerdict;

    /**
     * Bayesian Spam-Wahrscheinlichkeit (0.0 - 1.0) vom Naive-Bayes-Modell.
     */
    @Column
    private Double bayesScore;

    // ═══════════════════════════════════════════════════════════════
    // ANFRAGEN-ERKENNUNG (Inquiry Detection)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Anfrage-Score von 0-100. Höhere Werte = wahrscheinlicher eine echte Anfrage.
     */
    @Column
    private Integer inquiryScore;

    /**
     * Ist dies eine qualifizierte Anfrage (Score >= Threshold)?
     */
    @Column(nullable = false)
    private boolean isPotentialInquiry = false;

    // ═══════════════════════════════════════════════════════════════
    // NEWSLETTER / BULK MAIL
    // ═══════════════════════════════════════════════════════════════

    /**
     * Markiert als Newsletter (automatisierte Massenmail).
     * Basierend auf List-Unsubscribe Header oder Keywords.
     */
    @Column(nullable = false)
    private boolean isNewsletter = false;

    // ═══════════════════════════════════════════════════════════════
    // ATTACHMENTS (1:n)
    // ═══════════════════════════════════════════════════════════════

    @OneToMany(mappedBy = "email", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmailAttachment> attachments = new ArrayList<>();

    // ═══════════════════════════════════════════════════════════════
    // ANTWORT / WEITERLEITUNG VERKNÜPFUNG
    // ═══════════════════════════════════════════════════════════════

    /**
     * Referenz zur Original-Email, falls dies eine Antwort oder Weiterleitung ist.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_email_id")
    private Email parentEmail;

    /**
     * Antworten auf diese Email.
     */
    @OneToMany(mappedBy = "parentEmail")
    private List<Email> replies = new ArrayList<>();

    // ═══════════════════════════════════════════════════════════════
    // HILFSMETHODEN
    // ═══════════════════════════════════════════════════════════════

    /**
     * Extrahiert und setzt die senderDomain aus fromAddress.
     */
    public void extractSenderDomain() {
        if (fromAddress != null && fromAddress.contains("@")) {
            this.senderDomain = fromAddress.substring(fromAddress.lastIndexOf("@") + 1).toLowerCase();
        }
    }

    /**
     * Fügt ein Attachment hinzu und setzt die Rückbeziehung.
     */
    public void addAttachment(EmailAttachment attachment) {
        attachments.add(attachment);
        attachment.setEmail(this);
    }

    /**
     * Ordnet diese Email einem Projekt zu.
     */
    public void assignToProjekt(Projekt projekt) {
        this.zuordnungTyp = EmailZuordnungTyp.PROJEKT;
        this.projekt = projekt;
        this.anfrage = null;
        this.lieferant = null;
    }

    /**
     * Ordnet diese Email einem Anfrage zu.
     */
    public void assignToAnfrage(Anfrage anfrage) {
        this.zuordnungTyp = EmailZuordnungTyp.ANFRAGE;
        this.anfrage = anfrage;
        this.projekt = null;
        this.lieferant = null;
    }

    /**
     * Ordnet diese Email einem Lieferanten zu.
     */
    public void assignToLieferant(Lieferanten lieferant) {
        this.zuordnungTyp = EmailZuordnungTyp.LIEFERANT;
        this.lieferant = lieferant;
        this.projekt = null;
        this.anfrage = null;
        this.steuerberater = null;
    }

    /**
     * Ordnet diese Email einem Steuerberater zu (für BWA-Importe).
     */
    public void assignToSteuerberater(SteuerberaterKontakt steuerberater) {
        this.zuordnungTyp = EmailZuordnungTyp.STEUERBERATER;
        this.steuerberater = steuerberater;
        this.projekt = null;
        this.anfrage = null;
        this.lieferant = null;
    }

    /**
     * Entfernt die Zuordnung (setzt zurück auf KEINE).
     */
    public void clearAssignment() {
        this.zuordnungTyp = EmailZuordnungTyp.KEINE;
        this.projekt = null;
        this.anfrage = null;
        this.lieferant = null;
        this.steuerberater = null;
    }

    @PrePersist
    protected void onCreate() {
        extractSenderDomain();
        if (processingStatus == null) {
            processingStatus = EmailProcessingStatus.DONE;
        }
        if (zuordnungTyp == null) {
            zuordnungTyp = EmailZuordnungTyp.KEINE;
        }
    }
}
