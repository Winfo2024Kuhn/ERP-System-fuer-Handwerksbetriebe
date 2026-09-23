package org.example.kalkulationsprogramm.repository;

import java.util.Optional;
import org.example.kalkulationsprogramm.domain.EmailTextTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

@Repository
public interface EmailTextTemplateRepository extends JpaRepository<EmailTextTemplate, Long> {
    @Query(value = "SELECT t.* FROM email_text_template t JOIN email_text_template_standard s ON s.template_id = t.id WHERE s.dokument_typ = :dokumentTyp", nativeQuery = true)
    Optional<EmailTextTemplate> findByDokumentTyp(@Param("dokumentTyp") String dokumentTyp);

    List<EmailTextTemplate> findAllByDokumentTypOrderByIdAsc(String dokumentTyp);

    @Modifying
    @Query(value = "INSERT INTO email_text_template_standard (dokument_typ, template_id) SELECT t.dokument_typ, t.id FROM email_text_template t WHERE t.id = :templateId ON DUPLICATE KEY UPDATE template_id = VALUES(template_id)", nativeQuery = true)
    int setStandardTemplate(@Param("templateId") Long templateId);

    @Modifying
    @Query(value = "UPDATE email_text_template SET standard = 0 WHERE dokument_typ = :dokumentTyp", nativeQuery = true)
    int clearStandardFlags(@Param("dokumentTyp") String dokumentTyp);

    @Modifying
    @Query(value = "UPDATE email_text_template SET standard = 1 WHERE id = :templateId", nativeQuery = true)
    int markStandardTemplate(@Param("templateId") Long templateId);

    @Modifying
    @Query(value = "DELETE FROM email_text_template_standard WHERE template_id = :templateId", nativeQuery = true)
    int deleteStandardAssignment(@Param("templateId") Long templateId);
}
