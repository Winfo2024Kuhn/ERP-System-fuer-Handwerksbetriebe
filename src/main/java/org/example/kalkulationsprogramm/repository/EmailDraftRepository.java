package org.example.kalkulationsprogramm.repository;

import java.util.Collection;
import java.util.List;

import org.example.kalkulationsprogramm.domain.EmailDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EmailDraftRepository extends JpaRepository<EmailDraft, Long> {

    @Query("SELECT d FROM EmailDraft d ORDER BY d.updatedAt DESC")
    List<EmailDraft> findAllByOrderByUpdatedAtDesc();

    /** Findet alle Entwürfe die an E-Mails im Thread gekoppelt sind */
    List<EmailDraft> findByReplyEmailIdIn(Collection<Long> emailIds);
}
