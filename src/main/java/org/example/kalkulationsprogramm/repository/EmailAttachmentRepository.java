package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
   * Diese sind Kandidaten für die Rechnungs-/Angebots-Erkennung.
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
}
