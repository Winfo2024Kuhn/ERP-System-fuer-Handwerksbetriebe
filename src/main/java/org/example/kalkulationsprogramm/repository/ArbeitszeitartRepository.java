package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Arbeitszeitart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArbeitszeitartRepository extends JpaRepository<Arbeitszeitart, Long> {

    /**
     * Findet alle aktiven Arbeitszeitarten, sortiert nach Sortierung und Name
     */
    @Query("SELECT a FROM Arbeitszeitart a WHERE a.aktiv = true ORDER BY a.sortierung, a.bezeichnung")
    List<Arbeitszeitart> findAllAktiv();

    /**
     * Findet alle Arbeitszeitarten (auch inaktive), sortiert
     */
    @Query("SELECT a FROM Arbeitszeitart a ORDER BY a.sortierung, a.bezeichnung")
    List<Arbeitszeitart> findAllSorted();
}
