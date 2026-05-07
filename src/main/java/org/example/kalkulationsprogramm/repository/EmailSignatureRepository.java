package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface EmailSignatureRepository extends JpaRepository<EmailSignature, Long> {
    List<EmailSignature> findAllByOrderByUpdatedAtDesc();

    Optional<EmailSignature> findFirstByIsSystemDefaultTrue();

    @Modifying
    @Query("UPDATE EmailSignature s SET s.isSystemDefault = false WHERE s.isSystemDefault = true AND s.id <> :keepId")
    int clearSystemDefaultExcept(@Param("keepId") Long keepId);
}

