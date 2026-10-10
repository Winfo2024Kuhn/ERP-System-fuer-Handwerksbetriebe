package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailAttachmentRepository extends JpaRepository<EmailAttachment, Long> {

  List<EmailAttachment> findByEmail(Email email);

  List<EmailAttachment> findByEmailId(Long emailId);

  /**
   * Findet alle Attachments die noch nicht KI-verarbeitet wurden.
   */
  @Query("SELECT a FROM EmailAttachment a WHERE a.aiProcessed = false")
  List<EmailAttachment> findUnprocessed();

  /**
   * Findet alle PDF/XML-Attachments die noch nicht KI-verarbeitet wurden.
   * Diese sind Kandidaten für die Rechnungs-/Anfrages-Erkennung.
   */
  @Query("""
      SELECT a FROM EmailAttachment a
      WHERE a.aiProcessed = false
        AND LOWER(a.originalFilename) LIKE '%.pdf'
      """)
  List<EmailAttachment> findUnprocessedDocuments();

  /**
   * Zählt unverarbeitete Attachments.
   */
  long countByAiProcessedFalse();

  /**
   * Findet alle Attachments die auf ein bestimmtes LieferantDokument verweisen.
   */
  List<EmailAttachment> findByLieferantDokumentId(Long lieferantDokumentId);

  /**
   * Schon verarbeitete Anhänge eines Lieferanten mit genau dieser Dateigröße – Kandidaten
   * für „derselbe Inhalt kam schon einmal“ (z. B. die Widerrufsbelehrung in jeder Mail).
   * Ob der Inhalt wirklich gleich ist, prüft der Aufrufer Byte für Byte.
   */
  @Query("""
      SELECT a FROM EmailAttachment a
      WHERE a.email.lieferant.id = :lieferantId
        AND a.sizeBytes = :groesse
        AND a.aiProcessed = true
        AND a.id <> :ohneId
      ORDER BY a.id
      """)
  List<EmailAttachment> findVerarbeiteteMitGleicherGroesse(@Param("lieferantId") Long lieferantId,
      @Param("groesse") Long groesse, @Param("ohneId") Long ohneId);

  /**
   * Gruppen gleich großer Anhänge je Lieferant, die mit einem Dokument dieses Lieferanten
   * verknüpft sind (Basis für das Zusammenführen inhaltsgleicher Dokumente). Zählt nur,
   * wenn Mail und Dokument zum selben Lieferanten gehören – eine umgehängte Mail darf
   * nie Dokumente zweier Lieferanten zusammenbringen.
   * Zeilen: {@code [attachmentId, lieferantId, sizeBytes]}, nach Gruppe und ID sortiert.
   */
  @Query("""
      SELECT a.id, a.lieferantDokument.lieferant.id, a.sizeBytes FROM EmailAttachment a
      WHERE a.sizeBytes IS NOT NULL
        AND a.lieferantDokument.lieferant = a.email.lieferant
        AND EXISTS (SELECT o.id FROM EmailAttachment o
                    WHERE o.lieferantDokument <> a.lieferantDokument
                      AND o.sizeBytes = a.sizeBytes
                      AND o.lieferantDokument.lieferant = a.lieferantDokument.lieferant
                      AND o.email.lieferant = a.lieferantDokument.lieferant)
      ORDER BY a.lieferantDokument.lieferant.id, a.sizeBytes, a.id
      """)
  List<Object[]> findDokumentAnhaengeMitGleicherGroesse();
}
