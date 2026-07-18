package org.example.kalkulationsprogramm.repository

import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import java.time.LocalDateTime
import java.util.Optional
import org.example.kalkulationsprogramm.domain.Anfrage
import org.example.kalkulationsprogramm.domain.Email
import org.example.kalkulationsprogramm.domain.EmailDirection
import org.example.kalkulationsprogramm.domain.EmailProcessingStatus
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp
import org.example.kalkulationsprogramm.domain.Lieferanten
import org.example.kalkulationsprogramm.domain.Projekt
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface EmailRepository : JpaRepository<Email, Long> {

    @Modifying
    @Query("UPDATE Email e SET e.parentEmail = null WHERE e.parentEmail.id = :parentId")
    fun detachRepliesFromParent(@Param("parentId") parentId: Long): Int

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT e FROM Email e WHERE e.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Optional<Email>

    fun findByDeletedAtIsNotNullOrderByDeletedAtDesc(): List<Email>
    fun countByDeletedAtIsNotNullAndIsReadFalse(): Long
    fun findByMessageId(messageId: String): Optional<Email>
    fun existsByMessageId(messageId: String): Boolean
    fun findByDirection(direction: EmailDirection): List<Email>
    fun findByDirectionOrderBySentAtDesc(direction: EmailDirection): List<Email>
    fun findBySenderDomain(senderDomain: String): List<Email>
    fun findByFromAddressIgnoreCase(fromAddress: String): List<Email>

    @Query("SELECT DISTINCT e FROM Email e LEFT JOIN FETCH e.attachments WHERE e.projekt = :projekt ORDER BY e.sentAt DESC")
    fun findByProjektOrderBySentAtDesc(@Param("projekt") projekt: Projekt): List<Email>

    fun findByAnfrageOrderBySentAtDesc(anfrage: Anfrage): List<Email>
    fun findByLieferantOrderBySentAtDesc(lieferant: Lieferanten): List<Email>
    fun findByProjektInOrderBySentAtDesc(projekte: Collection<Projekt>): List<Email>
    fun findByAnfrageInOrderBySentAtDesc(anfragen: Collection<Anfrage>): List<Email>
    fun findByLieferantIdOrderBySentAtDesc(lieferantId: Long): List<Email>

    @Query("SELECT DISTINCT e FROM Email e LEFT JOIN FETCH e.attachments WHERE e.lieferant.id = :lieferantId ORDER BY e.sentAt DESC")
    fun findByLieferantIdWithAttachments(@Param("lieferantId") lieferantId: Long): List<Email>

    fun countByLieferantId(lieferantId: Long): Long
    fun findFirstByLieferantIdOrderBySentAtDesc(lieferantId: Long): Optional<Email>
    fun findByZuordnungTypOrderBySentAtDesc(typ: EmailZuordnungTyp): List<Email>

    @Query(
        value = """
      SELECT DISTINCT e.* FROM email e
      WHERE e.zuordnung_typ = 'KEINE'
        AND e.direction = 'IN'
        AND e.projekt_id IS NULL
        AND e.anfrage_id IS NULL
        AND e.lieferant_id IS NULL
        AND e.deleted_at IS NULL
        AND (e.is_spam IS NULL OR e.is_spam = FALSE)
        AND LOWER(COALESCE(e.subject, '')) NOT LIKE '%newsletter%'
        AND LOWER(COALESCE(e.body, '')) NOT LIKE '%newsletter%'
        AND (
            EXISTS (
                SELECT 1 FROM projekt_kunden_emails pke
                JOIN projekt p ON pke.projekt_id = p.id
                WHERE (LOWER(pke.email) = LOWER(e.from_address) OR LOWER(e.from_address) LIKE CONCAT('%<', LOWER(pke.email), '>%'))
                AND (
                    (DATE(e.sent_at) BETWEEN DATE_SUB(p.anlegedatum, INTERVAL 1 MONTH) AND DATE_ADD(COALESCE(p.abschlussdatum, p.anlegedatum), INTERVAL 1 MONTH))
                )
            )
            OR EXISTS (
                SELECT 1 FROM anfrage_kunden_emails ake
                JOIN anfrage a ON ake.anfrage_id = a.id
                WHERE (LOWER(ake.email) = LOWER(e.from_address) OR LOWER(e.from_address) LIKE CONCAT('%<', LOWER(ake.email), '>%'))
                AND (
                    DATE(e.sent_at) BETWEEN DATE_SUB(a.anlegedatum, INTERVAL 1 MONTH) AND DATE_ADD(COALESCE(a.anlegedatum, CURRENT_DATE()), INTERVAL 1 MONTH)
                )
            )
            OR EXISTS (
                SELECT 1 FROM kunden_emails ke
                JOIN kunde k ON ke.kunden_id = k.id
                JOIN projekt p ON p.kunden_id = k.id
                WHERE (LOWER(ke.email) = LOWER(e.from_address) OR LOWER(e.from_address) LIKE CONCAT('%<', LOWER(ke.email), '>%'))
                AND (
                    (DATE(e.sent_at) BETWEEN DATE_SUB(p.anlegedatum, INTERVAL 1 MONTH) AND DATE_ADD(COALESCE(p.abschlussdatum, p.anlegedatum), INTERVAL 1 MONTH))
                )
            )
        )
      ORDER BY e.sent_at DESC
      """,
        nativeQuery = true,
    )
    fun findUnassigned(): List<Email>

    @Query(
        value = """
      SELECT COUNT(DISTINCT e.id) FROM email e
      WHERE e.zuordnung_typ = 'KEINE'
        AND e.direction = 'IN'
        AND e.projekt_id IS NULL
        AND e.anfrage_id IS NULL
        AND e.lieferant_id IS NULL
        AND e.deleted_at IS NULL
        AND e.is_read = false
        AND (e.is_spam IS NULL OR e.is_spam = FALSE)
        AND LOWER(COALESCE(e.subject, '')) NOT LIKE '%newsletter%'
        AND LOWER(COALESCE(e.body, '')) NOT LIKE '%newsletter%'
        AND (
            EXISTS (
                SELECT 1 FROM projekt_kunden_emails pke
                JOIN projekt p ON pke.projekt_id = p.id
                WHERE (LOWER(pke.email) = LOWER(e.from_address) OR LOWER(e.from_address) LIKE CONCAT('%<', LOWER(pke.email), '>%'))
                AND DATE(e.sent_at) BETWEEN DATE_SUB(p.anlegedatum, INTERVAL 1 MONTH) AND DATE_ADD(COALESCE(p.abschlussdatum, p.anlegedatum), INTERVAL 1 MONTH)
            )
            OR EXISTS (
                SELECT 1 FROM anfrage_kunden_emails ake
                JOIN anfrage a ON ake.anfrage_id = a.id
                WHERE (LOWER(ake.email) = LOWER(e.from_address) OR LOWER(e.from_address) LIKE CONCAT('%<', LOWER(ake.email), '>%'))
                AND DATE(e.sent_at) BETWEEN DATE_SUB(a.anlegedatum, INTERVAL 1 MONTH) AND DATE_ADD(COALESCE(a.anlegedatum, CURRENT_DATE()), INTERVAL 1 MONTH)
            )
            OR EXISTS (
                SELECT 1 FROM kunden_emails ke
                JOIN kunde k ON ke.kunden_id = k.id
                JOIN projekt p ON p.kunden_id = k.id
                WHERE (LOWER(ke.email) = LOWER(e.from_address) OR LOWER(e.from_address) LIKE CONCAT('%<', LOWER(ke.email), '>%'))
                AND DATE(e.sent_at) BETWEEN DATE_SUB(p.anlegedatum, INTERVAL 1 MONTH) AND DATE_ADD(COALESCE(p.abschlussdatum, p.anlegedatum), INTERVAL 1 MONTH)
            )
        )
      """,
        nativeQuery = true,
    )
    fun countUnassigned(): Long

    fun findByProcessingStatus(status: EmailProcessingStatus): List<Email>

    @Query(
        """
      SELECT e FROM Email e
      WHERE e.processingStatus = 'QUEUED'
      ORDER BY e.id ASC
      """,
    )
    fun findQueuedEmails(): List<Email>

    fun countByZuordnungTyp(typ: EmailZuordnungTyp): Long
    fun countByProcessingStatus(status: EmailProcessingStatus): Long

    @Query("SELECT COUNT(e) FROM Email e WHERE e.firstViewedAt IS NULL")
    fun countUnread(): Long

    @Query(
        value = """
      SELECT * FROM email e
      WHERE e.zuordnung_typ = 'KEINE'
        AND e.direction = 'IN'
        AND e.deleted_at IS NULL
        AND (
          LOWER(e.sender_domain) = LOWER(:domain)
          OR (e.sender_domain IS NULL AND LOWER(e.from_address) LIKE LOWER(CONCAT('%@', :domain)))
        )
      ORDER BY e.sent_at DESC
      """,
        nativeQuery = true,
    )
    fun findUnassignedByDomain(@Param("domain") domain: String): List<Email>

    @Query(
        """
      SELECT e FROM Email e
      WHERE (LOWER(e.fromAddress) LIKE LOWER(CONCAT('%', :address, '%'))
          OR LOWER(e.recipient) LIKE LOWER(CONCAT('%', :address, '%')))
        AND e.zuordnungTyp = 'KEINE'
      ORDER BY e.sentAt DESC
      """,
    )
    fun findUnassignedByAddress(@Param("address") address: String): List<Email>

    @Query(
        value = """
      SELECT * FROM email e
      WHERE e.is_potential_inquiry = true
        AND e.is_spam = false
        AND e.deleted_at IS NULL
        AND e.direction = 'IN'
        AND e.zuordnung_typ = 'KEINE'
        AND e.projekt_id IS NULL
        AND e.anfrage_id IS NULL
        AND e.lieferant_id IS NULL
      ORDER BY e.sent_at DESC
      """,
        nativeQuery = true,
    )
    fun findPotentialInquiries(): List<Email>

    @Query(
        value = """
      SELECT COUNT(*) FROM email e
      WHERE e.is_potential_inquiry = true
        AND e.is_spam = false
        AND e.deleted_at IS NULL
        AND e.is_read = false
        AND e.direction = 'IN'
        AND e.zuordnung_typ = 'KEINE'
        AND e.projekt_id IS NULL
        AND e.anfrage_id IS NULL
        AND e.lieferant_id IS NULL
      """,
        nativeQuery = true,
    )
    fun countPotentialInquiries(): Long

    @Query(
        """
      SELECT e FROM Email e
      WHERE e.inquiryScore IS NULL
        AND e.deletedAt IS NULL
        AND e.direction = org.example.kalkulationsprogramm.domain.EmailDirection.IN
        AND e.projekt IS NULL
        AND e.anfrage IS NULL
        AND e.lieferant IS NULL
      ORDER BY e.sentAt DESC
      """,
    )
    fun findUnanalyzedForInquiry(): List<Email>

    @Query(
        value = """
      SELECT * FROM email e
      WHERE e.is_potential_inquiry = true
        AND (
          LOWER(e.sender_domain) = LOWER(:domain)
          OR (e.sender_domain IS NULL AND LOWER(e.from_address) LIKE LOWER(CONCAT('%@', :domain)))
        )
      """,
        nativeQuery = true,
    )
    fun findInquiriesByDomain(@Param("domain") domain: String): List<Email>

    @Query("SELECT e FROM Email e WHERE e.parentEmail IS NULL ORDER BY e.sentAt ASC")
    fun findEmailsWithoutParent(): List<Email>

    @Query("SELECT e FROM Email e WHERE e.messageId IN :messageIds ORDER BY e.sentAt DESC")
    fun findByMessageIdIn(@Param("messageIds") messageIds: Collection<String>): List<Email>

    @Query(
        "SELECT e FROM Email e WHERE e.sentAt IS NOT NULL AND e.sentAt < :before" +
            " AND e.subject IS NOT NULL ORDER BY e.sentAt DESC, e.id DESC",
    )
    fun findRecentBefore(@Param("before") before: LocalDateTime, pageable: Pageable): List<Email>

    fun countByDirection(direction: EmailDirection): Long
    fun countByDirectionAndIsReadFalse(direction: EmailDirection): Long
    fun countByDirectionAndZuordnungTypIsNull(direction: EmailDirection): Long

    @Query("SELECT e FROM Email e WHERE e.isSpam = true AND e.deletedAt IS NULL ORDER BY e.sentAt DESC")
    fun findSpam(): List<Email>

    @Query("SELECT COUNT(e) FROM Email e WHERE e.isSpam = true AND e.deletedAt IS NULL")
    fun countSpam(): Long

    @Query("SELECT e FROM Email e WHERE e.isNewsletter = true AND e.isSpam = false AND e.deletedAt IS NULL ORDER BY e.sentAt DESC")
    fun findNewsletter(): List<Email>

    @Query("SELECT COUNT(e) FROM Email e WHERE e.isNewsletter = true AND e.isSpam = false AND e.deletedAt IS NULL")
    fun countNewsletter(): Long

    @Query(
        """
      SELECT e FROM Email e
      WHERE e.spamScore IS NULL
        AND e.deletedAt IS NULL
        AND e.direction = org.example.kalkulationsprogramm.domain.EmailDirection.IN
        AND e.projekt IS NULL
        AND e.anfrage IS NULL
        AND e.lieferant IS NULL
      ORDER BY e.sentAt DESC
      """,
    )
    fun findUnanalyzedForSpam(): List<Email>

    @Query(
        "SELECT e FROM Email e " +
            "WHERE e.direction = 'IN' " +
            "AND e.zuordnungTyp = 'KEINE' " +
            "AND e.deletedAt IS NULL " +
            "AND e.isSpam = false " +
            "AND e.isNewsletter = false " +
            "AND e.isPotentialInquiry = false " +
            "AND e.projekt IS NULL " +
            "AND e.anfrage IS NULL " +
            "AND e.lieferant IS NULL " +
            "ORDER BY e.sentAt DESC",
    )
    fun findInboxFiltered(): List<Email>

    @Query(
        "SELECT e FROM Email e " +
            "WHERE e.direction = 'IN' " +
            "AND e.zuordnungTyp = 'KEINE' " +
            "AND e.deletedAt IS NULL " +
            "AND e.isSpam = false " +
            "AND e.isNewsletter = false " +
            "AND e.userSpamVerdict IS NULL " +
            "AND e.sentAt < :cutoff " +
            "AND e.projekt IS NULL " +
            "AND e.anfrage IS NULL " +
            "AND e.lieferant IS NULL",
    )
    fun findLongLivedInboxEmailsWithoutVerdict(@Param("cutoff") cutoff: LocalDateTime): List<Email>

    @Query(
        "SELECT COUNT(e) FROM Email e " +
            "WHERE e.direction = 'IN' " +
            "AND e.zuordnungTyp = 'KEINE' " +
            "AND e.deletedAt IS NULL " +
            "AND e.isSpam = false " +
            "AND e.isNewsletter = false " +
            "AND e.isPotentialInquiry = false " +
            "AND e.isRead = false " +
            "AND e.projekt IS NULL " +
            "AND e.anfrage IS NULL " +
            "AND e.lieferant IS NULL",
    )
    fun countInboxFilteredUnread(): Long

    @Query("SELECT e FROM Email e WHERE e.zuordnungTyp = 'PROJEKT' AND e.direction = 'IN' AND e.deletedAt IS NULL ORDER BY e.sentAt DESC")
    fun findProjectEmails(): List<Email>

    @Query("SELECT COUNT(e) FROM Email e WHERE e.zuordnungTyp = 'PROJEKT' AND e.direction = 'IN' AND e.deletedAt IS NULL AND e.isRead = false")
    fun countProjectEmailsUnread(): Long

    @Query("SELECT e FROM Email e WHERE e.zuordnungTyp = 'ANFRAGE' AND e.direction = 'IN' AND e.deletedAt IS NULL ORDER BY e.sentAt DESC")
    fun findAnfrageEmails(): List<Email>

    @Query("SELECT COUNT(e) FROM Email e WHERE e.zuordnungTyp = 'ANFRAGE' AND e.direction = 'IN' AND e.deletedAt IS NULL AND e.isRead = false")
    fun countAnfrageEmailsUnread(): Long

    @Query("SELECT e FROM Email e WHERE e.zuordnungTyp = 'LIEFERANT' AND e.direction = 'IN' AND e.deletedAt IS NULL ORDER BY e.sentAt DESC")
    fun findLieferantEmails(): List<Email>

    @Query(
        """
        SELECT e FROM Email e
        WHERE e.deletedAt IS NULL AND (
            LOWER(COALESCE(e.fromAddress, '')) LIKE LOWER(CONCAT('%', :domainFragment, '%')) OR
            LOWER(COALESCE(e.recipient, '')) LIKE LOWER(CONCAT('%', :domainFragment, '%')) OR
            LOWER(COALESCE(e.cc, '')) LIKE LOWER(CONCAT('%', :domainFragment, '%'))
        )
        ORDER BY e.sentAt DESC
        """,
    )
    fun findTaxAdvisorCandidates(@Param("domainFragment") domainFragment: String): List<Email>

    @Query("SELECT COUNT(e) FROM Email e WHERE e.zuordnungTyp = 'LIEFERANT' AND e.direction = 'IN' AND e.deletedAt IS NULL AND e.isRead = false")
    fun countLieferantEmailsUnread(): Long

    @Query("SELECT COUNT(e) FROM Email e WHERE e.isNewsletter = true AND e.isSpam = false AND e.deletedAt IS NULL AND e.isRead = false")
    fun countNewsletterUnread(): Long

    @Query("SELECT COUNT(e) FROM Email e WHERE e.isSpam = true AND e.deletedAt IS NULL AND e.isRead = false")
    fun countSpamUnread(): Long

    @Query("SELECT e FROM Email e WHERE e.isStarred = true AND e.deletedAt IS NULL ORDER BY e.sentAt DESC")
    fun findStarred(): List<Email>

    @Query("SELECT COUNT(e) FROM Email e WHERE e.isStarred = true AND e.deletedAt IS NULL AND e.isRead = false")
    fun countStarredUnread(): Long

    @Query(
        value = """
      SELECT * FROM email e
      WHERE (LOWER(e.subject) LIKE LOWER(CONCAT('%', :query, '%'))
          OR LOWER(e.from_address) LIKE LOWER(CONCAT('%', :query, '%'))
          OR LOWER(e.recipient) LIKE LOWER(CONCAT('%', :query, '%'))
          OR LOWER(e.body) LIKE LOWER(CONCAT('%', :query, '%')))
      ORDER BY e.sent_at DESC
      """,
        nativeQuery = true,
    )
    fun searchGlobal(@Param("query") query: String): List<Email>
}
