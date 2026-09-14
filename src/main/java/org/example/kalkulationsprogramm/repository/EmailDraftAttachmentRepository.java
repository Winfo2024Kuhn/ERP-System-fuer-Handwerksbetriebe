package org.example.kalkulationsprogramm.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.example.kalkulationsprogramm.domain.EmailDraftAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailDraftAttachmentRepository extends JpaRepository<EmailDraftAttachment, Long> {
    interface Metadata {
        Long getId();
        Long getDraftId();
        String getFilename();
        String getContentType();
        long getSize();
    }

    // Select scalar metadata explicitly: @Basic(LAZY) alone cannot defer a byte[] without enhancement.
    @Query("SELECT a.id AS id, a.draft.id AS draftId, a.filename AS filename, "
            + "a.contentType AS contentType, a.size AS size FROM EmailDraftAttachment a "
            + "WHERE a.draft.id IN :draftIds ORDER BY a.id")
    List<Metadata> findMetadata(@Param("draftIds") Collection<Long> draftIds);

    Optional<EmailDraftAttachment> findByIdAndDraftId(Long id, Long draftId);
}
