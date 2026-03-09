package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.ZeitkontoKorrekturAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository für Zeitkonto-Korrektur Audit-Einträge.
 */
@Repository
public interface ZeitkontoKorrekturAuditRepository extends JpaRepository<ZeitkontoKorrekturAudit, Long> {

    /**
     * Findet alle Audit-Einträge für eine Korrektur, sortiert nach Version
     * absteigend.
     */
    List<ZeitkontoKorrekturAudit> findByZeitkontoKorrekturIdOrderByVersionDesc(Long zeitkontoKorrekturId);
}
