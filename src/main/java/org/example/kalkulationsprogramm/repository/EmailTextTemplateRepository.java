package org.example.kalkulationsprogramm.repository;

import java.util.Optional;
import org.example.kalkulationsprogramm.domain.EmailTextTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailTextTemplateRepository extends JpaRepository<EmailTextTemplate, Long> {
    Optional<EmailTextTemplate> findByDokumentTyp(String dokumentTyp);
}
