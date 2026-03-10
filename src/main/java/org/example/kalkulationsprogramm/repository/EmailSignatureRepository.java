package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmailSignatureRepository extends JpaRepository<EmailSignature, Long> {
    List<EmailSignature> findAllByOrderByUpdatedAtDesc();
}

