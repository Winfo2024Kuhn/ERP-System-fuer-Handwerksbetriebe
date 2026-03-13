package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.AnfrageNotiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnfrageNotizRepository extends JpaRepository<AnfrageNotiz, Long> {

    /**
     * Findet alle Notizen zu einem Anfrage, sortiert nach Erstellungsdatum (neueste
     * zuerst).
     */
    List<AnfrageNotiz> findByAnfrageIdOrderByErstelltAmDesc(Long anfrageId);
}
