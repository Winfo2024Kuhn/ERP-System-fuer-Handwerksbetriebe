package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.EmailBlacklistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailBlacklistRepository extends JpaRepository<EmailBlacklistEntry, Long> {
    
    boolean existsByEmailAddress(String emailAddress);
    
    Optional<EmailBlacklistEntry> findByEmailAddress(String emailAddress);
}
