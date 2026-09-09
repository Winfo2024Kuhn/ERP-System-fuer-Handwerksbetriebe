package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.MonatsabschlussAudit;
import org.springframework.data.repository.Repository;

/** Bewusst keine Update-/Löschmethoden; Einträge haben keine öffentlichen Setter. */
public interface MonatsabschlussAuditRepository extends Repository<MonatsabschlussAudit, Long> {
    MonatsabschlussAudit save(MonatsabschlussAudit eintrag);

    List<MonatsabschlussAudit> findByMitarbeiterIdAndJahrAndMonatOrderByZeitpunktAscIdAsc(
            Long mitarbeiterId, Integer jahr, Integer monat);
}
