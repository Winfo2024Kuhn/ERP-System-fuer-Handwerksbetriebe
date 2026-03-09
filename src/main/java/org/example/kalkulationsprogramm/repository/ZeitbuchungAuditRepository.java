package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.ZeitbuchungAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository für unveränderliche Zeitbuchungs-Audit-Einträge.
 */
@Repository
public interface ZeitbuchungAuditRepository extends JpaRepository<ZeitbuchungAudit, Long> {

    /**
     * Gibt die vollständige Änderungshistorie einer Zeitbuchung zurück (neueste
     * zuerst).
     */
    List<ZeitbuchungAudit> findByZeitbuchungIdOrderByVersionDesc(Long zeitbuchungId);

    /**
     * Gibt die vollständige Änderungshistorie einer Zeitbuchung zurück (älteste
     * zuerst).
     */
    List<ZeitbuchungAudit> findByZeitbuchungIdOrderByVersionAsc(Long zeitbuchungId);

    /**
     * Prüft ob für eine Zeitbuchung bereits Audit-Einträge existieren.
     */
    boolean existsByZeitbuchungId(Long zeitbuchungId);

    /**
     * Zählt die Anzahl der Versionen einer Zeitbuchung.
     */
    long countByZeitbuchungId(Long zeitbuchungId);
}
