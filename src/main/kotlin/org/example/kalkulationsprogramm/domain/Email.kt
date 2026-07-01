package org.example.kalkulationsprogramm.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.Lob
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(
    name = "email",
    indexes = [
        Index(name = "idx_email_message_id", columnList = "messageId", unique = true),
        Index(name = "idx_email_sender_domain", columnList = "senderDomain"),
        Index(name = "idx_email_direction", columnList = "direction"),
        Index(name = "idx_email_zuordnung", columnList = "zuordnungTyp"),
        Index(name = "idx_email_projekt", columnList = "projekt_id"),
        Index(name = "idx_email_anfrage", columnList = "anfrage_id"),
        Index(name = "idx_email_lieferant", columnList = "lieferant_id"),
        Index(name = "idx_email_processing", columnList = "processingStatus"),
        Index(name = "idx_email_sent_at", columnList = "sentAt"),
    ],
)
open class Email {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(length = 512, nullable = false, unique = true)
    open var messageId: String? = null

    @Column(length = 255)
    open var fromAddress: String? = null

    @Column(length = 255)
    open var senderDomain: String? = null

    @Column(length = 1000)
    open var recipient: String? = null

    @Column(length = 1000)
    open var cc: String? = null

    @Column(length = 255)
    open var replyToAddress: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    open var authenticationResults: String? = null

    @Column(length = 1000)
    open var subject: String? = null

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    open var body: String? = null

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    open var htmlBody: String? = null

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    open var rawBody: String? = null

    open var sentAt: LocalDateTime? = null
    open var firstViewedAt: LocalDateTime? = null
    open var deletedAt: LocalDateTime? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    open var direction: EmailDirection? = null

    @Column(length = 255)
    open var imapFolder: String? = null

    open var imapUid: Long? = null

    @Column(nullable = false)
    open var isRead: Boolean = false

    @Column(nullable = false)
    open var isStarred: Boolean = false

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    open var zuordnungTyp: EmailZuordnungTyp? = EmailZuordnungTyp.KEINE

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projekt_id")
    open var projekt: Projekt? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anfrage_id")
    open var anfrage: Anfrage? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id")
    open var lieferant: Lieferanten? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "steuerberater_id")
    open var steuerberater: SteuerberaterKontakt? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    open var processingStatus: EmailProcessingStatus? = EmailProcessingStatus.DONE

    open var processedAt: LocalDateTime? = null

    @Column(columnDefinition = "TEXT")
    open var errorMessage: String? = null

    @Column
    open var spamScore: Int? = 0

    @Column(nullable = false)
    open var isSpam: Boolean = false

    @Column(length = 20)
    open var userSpamVerdict: String? = null

    @Column
    open var bayesScore: Double? = null

    @Column
    open var inquiryScore: Int? = null

    @Column(nullable = false)
    open var isPotentialInquiry: Boolean = false

    @Column(nullable = false)
    open var isNewsletter: Boolean = false

    @OneToMany(mappedBy = "email", cascade = [CascadeType.ALL], orphanRemoval = true)
    open var attachments: MutableList<EmailAttachment> = ArrayList()

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_email_id")
    open var parentEmail: Email? = null

    @OneToMany(mappedBy = "parentEmail")
    open var replies: MutableList<Email> = ArrayList()

    open fun extractSenderDomain() {
        val address = fromAddress
        if (address != null && address.contains("@")) {
            senderDomain = address.substring(address.lastIndexOf("@") + 1).lowercase()
        }
    }

    open fun addAttachment(attachment: EmailAttachment) {
        attachments.add(attachment)
        attachment.email = this
    }

    open fun assignToProjekt(projekt: Projekt?) {
        zuordnungTyp = EmailZuordnungTyp.PROJEKT
        this.projekt = projekt
        anfrage = null
        lieferant = null
    }

    open fun assignToAnfrage(anfrage: Anfrage?) {
        zuordnungTyp = EmailZuordnungTyp.ANFRAGE
        this.anfrage = anfrage
        projekt = null
        lieferant = null
    }

    open fun assignToLieferant(lieferant: Lieferanten?) {
        zuordnungTyp = EmailZuordnungTyp.LIEFERANT
        this.lieferant = lieferant
        projekt = null
        anfrage = null
        steuerberater = null
    }

    open fun assignToSteuerberater(steuerberater: SteuerberaterKontakt?) {
        zuordnungTyp = EmailZuordnungTyp.STEUERBERATER
        this.steuerberater = steuerberater
        projekt = null
        anfrage = null
        lieferant = null
    }

    open fun clearAssignment() {
        zuordnungTyp = EmailZuordnungTyp.KEINE
        projekt = null
        anfrage = null
        lieferant = null
        steuerberater = null
    }

    @PrePersist
    protected open fun onCreate() {
        extractSenderDomain()
        if (processingStatus == null) {
            processingStatus = EmailProcessingStatus.DONE
        }
        if (zuordnungTyp == null) {
            zuordnungTyp = EmailZuordnungTyp.KEINE
        }
    }
}
