package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.EmailBlacklistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailBlacklistRepository extends JpaRepository<EmailBlacklistEntry, Long> {

    // LOWER: Adressen ohne Ruecksicht auf Gross-/Kleinschreibung - MySQL tut das
    // ueber die Collation von selbst, PostgreSQL nicht
    @Query("SELECT COUNT(b) > 0 FROM EmailBlacklistEntry b WHERE LOWER(b.emailAddress) = LOWER(:emailAddress)")
    boolean existsByEmailAddress(@Param("emailAddress") String emailAddress);

    @Query("SELECT b FROM EmailBlacklistEntry b WHERE LOWER(b.emailAddress) = LOWER(:emailAddress)")
    Optional<EmailBlacklistEntry> findByEmailAddress(@Param("emailAddress") String emailAddress);
}
