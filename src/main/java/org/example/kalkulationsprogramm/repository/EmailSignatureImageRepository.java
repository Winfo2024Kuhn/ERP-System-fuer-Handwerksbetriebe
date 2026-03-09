package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.EmailSignatureImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailSignatureImageRepository extends JpaRepository<EmailSignatureImage, Long> {
    List<EmailSignatureImage> findBySignatureIdOrderBySortOrderAsc(Long signatureId);
}

