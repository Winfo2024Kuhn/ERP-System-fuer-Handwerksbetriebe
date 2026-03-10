package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.AngebotNotiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AngebotNotizRepository extends JpaRepository<AngebotNotiz, Long> {

    /**
     * Findet alle Notizen zu einem Angebot, sortiert nach Erstellungsdatum (neueste
     * zuerst).
     */
    List<AngebotNotiz> findByAngebotIdOrderByErstelltAmDesc(Long angebotId);
}
