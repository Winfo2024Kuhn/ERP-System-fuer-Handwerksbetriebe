package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EmailSignatureRepository extends JpaRepository<EmailSignature, Long> {
    @Query("SELECT DISTINCT s FROM EmailSignature s LEFT JOIN FETCH s.images ORDER BY s.updatedAt DESC")
    List<EmailSignature> findAllByOrderByUpdatedAtDesc();
}

