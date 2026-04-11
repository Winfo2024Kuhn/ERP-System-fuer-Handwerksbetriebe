package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailProcessingStatus;
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailRepository extends JpaRepository<Email, Long> {

  List<Email> findByDeletedAtIsNotNullOrderByDeletedAtDesc();

  long countByDeletedAtIsNotNullAndIsReadFalse();

  // ═══════════════════════════════════════════════════════════════
  // SUCHE
  // ═══════════════════════════════════════════════════════════════

  Optional<Email> findByMessageId(String messageId);

  boolean existsByMessageId(String messageId);

  List<Email> findByDirection(EmailDirection direction);

  List<Email> findByDirectionOrderBySentAtDesc(EmailDirection direction);

  List<Email> findBySenderDomain(String senderDomain);

  List<Email> findByFromAddressIgnoreCase(String fromAddress);

  // ═══════════════════════════════════════════════════════════════
  // ZUORDNUNG
  // ═══════════════════════════════════════════════════════════════

  @Query("SELECT DISTINCT e FROM Email e LEFT JOIN FETCH e.attachments WHERE e.projekt = :projekt ORDER BY e.sentAt DESC")
  List<Email> findByProjektOrderBySentAtDesc(@Param("projekt") Projekt projekt);

  List<Email> findByAnfrageOrderBySentAtDesc(Anfrage anfrage);

  List<Email> findByLieferantOrderBySentAtDesc(Lieferanten lieferant);

  List<Email> findByProjektInOrderBySentAtDesc(java.util.Collection<Projekt> projekte);

  List<Email> findByAnfrageInOrderBySentAtDesc(java.util.Collection<Anfrage> anfragen);

  // ID-based methods for better performance/flexibility
  List<Email> findByLieferantIdOrderBySentAtDesc(Long lieferantId);

  /**
   * Findet alle Emails eines Lieferanten mit vorgeladenen Attachments.
   * Wichtig für asynchrone Kontexte (z.B. EmailBackfillEventListener) um
   * LazyInitializationException zu vermeiden.
   */
  @Query("SELECT DISTINCT e FROM Email e LEFT JOIN FETCH e.attachments WHERE e.lieferant.id = :lieferantId ORDER BY e.sentAt DESC")
  List<Email> findByLieferantIdWithAttachments(@Param("lieferantId") Long lieferantId);

  long countByLieferantId(Long lieferantId);

  Optional<Email> findFirstByLieferantIdOrderBySentAtDesc(Long lieferantId);

  List<Email> findByZuordnungTypOrderBySentAtDesc(EmailZuordnungTyp typ);

  /**
   * Findet alle unzugeordneten Emails von BEKANNTEN Kunden
   * (deren Absender-Adresse EXAKT in kunden_emails, anfrage_kunden_emails oder
   * projekt_kunden_emails vorkommt).
   * Filtert Newsletter-Mails heraus.
   * Zusätzlich: Email-Sendedatum muss innerhalb von ±1 Monat zum Anlegedatum oder
   * Abschlussdatum
   * eines passenden Projekts oder Anfrages liegen (Karenzzeit).
   */
  @Query(value = """
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
            -- Projekt-Kunden-Emails mit ±1 Monat Karenzzeit
            EXISTS (
                SELECT 1 FROM projekt_kunden_emails pke
                JOIN projekt p ON pke.projekt_id = p.id
                WHERE (LOWER(pke.email) = LOWER(e.from_address) OR LOWER(e.from_address) LIKE CONCAT('%<', LOWER(pke.email), '>%'))
                AND (
                    (DATE(e.sent_at) BETWEEN DATE_SUB(p.anlegedatum, INTERVAL 1 MONTH) AND DATE_ADD(COALESCE(p.abschlussdatum, p.anlegedatum), INTERVAL 1 MONTH))
                )
            )
            OR
            -- Anfrage-Kunden-Emails mit ±1 Monat Karenzzeit
            EXISTS (
                SELECT 1 FROM anfrage_kunden_emails ake
                JOIN anfrage a ON ake.anfrage_id = a.id
                WHERE (LOWER(ake.email) = LOWER(e.from_address) OR LOWER(e.from_address) LIKE CONCAT('%<', LOWER(ake.email), '>%'))
                AND (
                    DATE(e.sent_at) BETWEEN DATE_SUB(a.anlegedatum, INTERVAL 1 MONTH) AND DATE_ADD(COALESCE(a.anlegedatum, CURRENT_DATE()), INTERVAL 1 MONTH)
                )
            )
            OR
            -- Kunden-Emails (über Projekt des Kunden) mit ±1 Monat Karenzzeit
            EXISTS (
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
      """, nativeQuery = true)
  List<Email> findUnassigned();

  /**
   * Zählt alle unzugeordneten Emails von BEKANNTEN Kunden.
   * Mit ±1 Monat Karenzzeit bezogen auf Projekt/Anfrage-Datumsangaben.
   */
  @Query(value = """
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
      """, nativeQuery = true)
  long countUnassigned();

  // ═══════════════════════════════════════════════════════════════
  // QUEUE
  // ═══════════════════════════════════════════════════════════════

  List<Email> findByProcessingStatus(EmailProcessingStatus status);

  @Query(value = """
      SELECT e FROM Email e
      WHERE e.processingStatus = 'QUEUED'
      ORDER BY e.id ASC
      """)
  List<Email> findQueuedEmails();

  // ═══════════════════════════════════════════════════════════════
  // STATISTIKEN
  // ═══════════════════════════════════════════════════════════════

  long countByZuordnungTyp(EmailZuordnungTyp typ);

  long countByProcessingStatus(EmailProcessingStatus status);

  @Query("SELECT COUNT(e) FROM Email e WHERE e.firstViewedAt IS NULL")
  long countUnread();

  // ═══════════════════════════════════════════════════════════════
  // DOMAIN-BASIERTE SUCHE (für Lieferanten-Backfill)
  // ═══════════════════════════════════════════════════════════════

  /**
   * Findet unzugeordnete Emails von einer bestimmten Domain.
   * Prüft sowohl senderDomain als auch extrahiert Domain aus fromAddress als
   * Fallback.
   */
  @Query(value = """
      SELECT * FROM email e
      WHERE e.zuordnung_typ = 'KEINE'
        AND e.direction = 'IN'
        AND e.deleted_at IS NULL
        AND (
          LOWER(e.sender_domain) = LOWER(:domain)
          OR (e.sender_domain IS NULL AND LOWER(e.from_address) LIKE LOWER(CONCAT('%@', :domain)))
        )
      ORDER BY e.sent_at DESC
      """, nativeQuery = true)
  List<Email> findUnassignedByDomain(@Param("domain") String domain);

  @Query("""
      SELECT e FROM Email e
      WHERE (LOWER(e.fromAddress) LIKE LOWER(CONCAT('%', :address, '%'))
          OR LOWER(e.recipient) LIKE LOWER(CONCAT('%', :address, '%')))
        AND e.zuordnungTyp = 'KEINE'
      ORDER BY e.sentAt DESC
      """)
  List<Email> findUnassignedByAddress(@Param("address") String address);

  // ═══════════════════════════════════════════════════════════════
  // ANFRAGEN-Ordner (Score-basiert)
  // ═══════════════════════════════════════════════════════════════

  /**
   * Findet Emails die als potenzielle Anfragen markiert wurden (nach
   * Score-Analyse).
   * Nur nicht-zugeordnete, nicht-spam, nicht-gelöschte eingehende Emails.
   */
  @Query(value = """
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
      """, nativeQuery = true)
  List<Email> findPotentialInquiries();

  /**
   * Zählt potenzielle Anfragen für den Ordner-Badge.
   */
  @Query(value = """
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
      """, nativeQuery = true)
  long countPotentialInquiries();

  /**
   * Findet Emails die noch nicht auf Anfrage-Score analysiert wurden.
   * Für rückwirkende Analyse.
   */
  @Query("""
      SELECT e FROM Email e
      WHERE e.inquiryScore IS NULL
        AND e.deletedAt IS NULL
        AND e.direction = org.example.kalkulationsprogramm.domain.EmailDirection.IN
        AND e.projekt IS NULL
        AND e.anfrage IS NULL
        AND e.lieferant IS NULL
      ORDER BY e.sentAt DESC
      """)
  List<Email> findUnanalyzedForInquiry();

  /**
   * Findet Emails die als Anfrage markiert sind und von einer bestimmten Domain
   * stammen.
   * Wird verwendet um Anfragen-Flag zu entfernen wenn Lieferant angelegt wird.
   */
  @Query(value = """
      SELECT * FROM email e
      WHERE e.is_potential_inquiry = true
        AND (
          LOWER(e.sender_domain) = LOWER(:domain)
          OR (e.sender_domain IS NULL AND LOWER(e.from_address) LIKE LOWER(CONCAT('%@', :domain)))
        )
      """, nativeQuery = true)
  List<Email> findInquiriesByDomain(@Param("domain") String domain);

  // ═══════════════════════════════════════════════════════════════
  // THREAD/PARENT BACKFILL
  // ═══════════════════════════════════════════════════════════════

  /**
   * Findet alle Emails ohne parent, sortiert nach Datum (älteste zuerst für
   * korrektes Threading).
   */
  @Query("SELECT e FROM Email e WHERE e.parentEmail IS NULL ORDER BY e.sentAt ASC")
  List<Email> findEmailsWithoutParent();

  /**
   * Sucht eine Email anhand von Message-IDs aus In-Reply-To / References.
   */
  @Query("SELECT e FROM Email e WHERE e.messageId IN :messageIds ORDER BY e.sentAt DESC")
  List<Email> findByMessageIdIn(@Param("messageIds") java.util.Collection<String> messageIds);

  long countByDirection(EmailDirection direction);

  long countByDirectionAndIsReadFalse(EmailDirection direction);

  long countByDirectionAndZuordnungTypIsNull(EmailDirection direction);

  // ═══════════════════════════════════════════════════════════════
  // SPAM
  // ═══════════════════════════════════════════════════════════════

  /**
   * Findet alle Spam-Emails.
   */
  @Query("SELECT e FROM Email e WHERE e.isSpam = true AND e.deletedAt IS NULL ORDER BY e.sentAt DESC")
  List<Email> findSpam();

  /**
   * Zählt alle Spam-Emails.
   */
  /**
   * Zählt alle Spam-Emails.
   */
  @Query("SELECT COUNT(e) FROM Email e WHERE e.isSpam = true AND e.deletedAt IS NULL")
  long countSpam();

  // ═══════════════════════════════════════════════════════════════
  // NEWSLETTER
  // ═══════════════════════════════════════════════════════════════

  /**
   * Findet alle Newsletter.
   */
  @Query("SELECT e FROM Email e WHERE e.isNewsletter = true AND e.isSpam = false AND e.deletedAt IS NULL ORDER BY e.sentAt DESC")
  List<Email> findNewsletter();

  /**
   * Zählt alle Newsletter.
   */
  @Query("SELECT COUNT(e) FROM Email e WHERE e.isNewsletter = true AND e.isSpam = false AND e.deletedAt IS NULL")
  long countNewsletter();

  /**
   * Findet alle Emails die noch nicht auf Spam geprüft wurden.
   * Nur nicht-zugeordnete, nicht-gelöschte eingehende Emails.
   */
  @Query("""
      SELECT e FROM Email e
      WHERE e.spamScore IS NULL
        AND e.deletedAt IS NULL
        AND e.direction = org.example.kalkulationsprogramm.domain.EmailDirection.IN
        AND e.projekt IS NULL
        AND e.anfrage IS NULL
        AND e.lieferant IS NULL
      ORDER BY e.sentAt DESC
      """)
  List<Email> findUnanalyzedForSpam();

  // ═══════════════════════════════════════════════════════════════
  // FILTERED FOLDER QUERIES (Inbox without assignments etc.)
  // ═══════════════════════════════════════════════════════════════

  /**
   * Finds emails for "Posteingang" (filtered):
   * INBOUND, No Assignment, Not Deleted, Not Spam, Not Newsletter.
   * EXCLUDES emails that appear in "Anfragen" (isPotentialInquiry=true).
   * Note: "Nicht zugeordnet" emails (known customers) still appear here
   * because they are a subset, not a separate folder.
   */
  @Query("SELECT e FROM Email e " +
      "WHERE e.direction = 'IN' " +
      "AND e.zuordnungTyp = 'KEINE' " +
      "AND e.deletedAt IS NULL " +
      "AND e.isSpam = false " +
      "AND e.isNewsletter = false " +
      "AND e.isPotentialInquiry = false " +
      "AND e.projekt IS NULL " +
      "AND e.anfrage IS NULL " +
      "AND e.lieferant IS NULL " +
      "ORDER BY e.sentAt DESC")
  List<Email> findInboxFiltered();

  /**
   * Counts unread emails for "Posteingang" (filtered).
   * EXCLUDES emails that appear in "Anfragen".
   */
  @Query("SELECT COUNT(e) FROM Email e " +
      "WHERE e.direction = 'IN' " +
      "AND e.zuordnungTyp = 'KEINE' " +
      "AND e.deletedAt IS NULL " +
      "AND e.isSpam = false " +
      "AND e.isNewsletter = false " +
      "AND e.isPotentialInquiry = false " +
      "AND e.isRead = false " +
      "AND e.projekt IS NULL " +
      "AND e.anfrage IS NULL " +
      "AND e.lieferant IS NULL")
  long countInboxFilteredUnread();

  // ═══════════════════════════════════════════════════════════════
  // NEW FOLDERS: PROJECT, OFFER, SUPPLIER
  // ═══════════════════════════════════════════════════════════════

  @Query("SELECT e FROM Email e WHERE e.zuordnungTyp = 'PROJEKT' AND e.deletedAt IS NULL ORDER BY e.sentAt DESC")
  List<Email> findProjectEmails();

  @Query("SELECT COUNT(e) FROM Email e WHERE e.zuordnungTyp = 'PROJEKT' AND e.deletedAt IS NULL AND e.isRead = false")
  long countProjectEmailsUnread();

  @Query("SELECT e FROM Email e WHERE e.zuordnungTyp = 'ANFRAGE' AND e.deletedAt IS NULL ORDER BY e.sentAt DESC")
  List<Email> findAnfrageEmails();

  @Query("SELECT COUNT(e) FROM Email e WHERE e.zuordnungTyp = 'ANFRAGE' AND e.deletedAt IS NULL AND e.isRead = false")
  long countAnfrageEmailsUnread();

  @Query("SELECT e FROM Email e WHERE e.zuordnungTyp = 'LIEFERANT' AND e.deletedAt IS NULL ORDER BY e.sentAt DESC")
  List<Email> findLieferantEmails();

  @Query("SELECT COUNT(e) FROM Email e WHERE e.zuordnungTyp = 'LIEFERANT' AND e.deletedAt IS NULL AND e.isRead = false")
  long countLieferantEmailsUnread();

  // Updates for existing counts to use "Unread" logic if needed by user
  // requirements
  @Query("SELECT COUNT(e) FROM Email e WHERE e.isNewsletter = true AND e.isSpam = false AND e.deletedAt IS NULL AND e.isRead = false")
  long countNewsletterUnread();

  @Query("SELECT COUNT(e) FROM Email e WHERE e.isSpam = true AND e.deletedAt IS NULL AND e.isRead = false")
  long countSpamUnread();

  // ═══════════════════════════════════════════════════════════════
  // STARRED / MARKIERT
  // ═══════════════════════════════════════════════════════════════

  @Query("SELECT e FROM Email e WHERE e.isStarred = true AND e.deletedAt IS NULL ORDER BY e.sentAt DESC")
  List<Email> findStarred();

  @Query("SELECT COUNT(e) FROM Email e WHERE e.isStarred = true AND e.deletedAt IS NULL AND e.isRead = false")
  long countStarredUnread();

  // ═══════════════════════════════════════════════════════════════
  // GLOBALE SUCHE
  // ═══════════════════════════════════════════════════════════════

  /**
   * Durchsucht alle Emails nach Betreff, Absender oder Empfänger.
   * Gibt maximal 50 Ergebnisse zurück, sortiert nach Datum absteigend.
   */
  @Query(value = """
      SELECT * FROM email e
      WHERE (LOWER(e.subject) LIKE LOWER(CONCAT('%', :query, '%'))
          OR LOWER(e.from_address) LIKE LOWER(CONCAT('%', :query, '%'))
          OR LOWER(e.recipient) LIKE LOWER(CONCAT('%', :query, '%'))
          OR LOWER(e.body) LIKE LOWER(CONCAT('%', :query, '%')))
      ORDER BY e.sent_at DESC
      """, nativeQuery = true)
  List<Email> searchGlobal(@Param("query") String query);
}
