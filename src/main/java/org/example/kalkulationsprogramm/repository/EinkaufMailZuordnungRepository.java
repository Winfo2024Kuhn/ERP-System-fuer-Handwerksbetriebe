package org.example.kalkulationsprogramm.repository;

import java.util.Optional;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufMailZuordnung;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface EinkaufMailZuordnungRepository extends JpaRepository<EinkaufMailZuordnung, Long> {
    Optional<EinkaufMailZuordnung> findByEmailId(Long emailId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select z from EinkaufMailZuordnung z where z.emailId = :emailId")
    Optional<EinkaufMailZuordnung> findByEmailIdForUpdate(@Param("emailId") Long emailId);
    List<EinkaufMailZuordnung> findAllByEmailIdIn(java.util.Collection<Long> emailIds);
    Page<EinkaufMailZuordnung> findAllByTypAndVorgangId(String typ, Long vorgangId, Pageable pageable);

    @Query(value = "SELECT e.id FROM email e LEFT JOIN einkauf_mail_zuordnung z ON z.email_id = e.id "
            + "WHERE e.konto_id = 'EINKAUF' AND e.direction = 'IN' AND z.id IS NULL "
            + "ORDER BY e.id LIMIT :limit", nativeQuery = true)
    List<Long> findeOffeneImportZuordnungen(@Param("limit") int limit);
}
