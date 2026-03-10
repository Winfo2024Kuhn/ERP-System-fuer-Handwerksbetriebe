package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.ProjektNotiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProjektNotizRepository extends JpaRepository<ProjektNotiz, Long> {

    /**
     * Findet alle Notizen zu einem Projekt, sortiert nach Erstellungsdatum (neueste
     * zuerst).
     */
    List<ProjektNotiz> findByProjektIdOrderByErstelltAmDesc(Long projektId);

    /**
     * Findet alle Notizen die nach einem bestimmten Zeitpunkt erstellt wurden.
     */
    List<ProjektNotiz> findByErstelltAmAfterOrderByErstelltAmDesc(LocalDateTime after);
}
