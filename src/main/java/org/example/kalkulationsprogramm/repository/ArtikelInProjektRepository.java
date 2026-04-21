package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.domain.BestellQuelle;
import org.example.kalkulationsprogramm.dto.Projekt.MaterialKilogrammDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArtikelInProjektRepository extends JpaRepository<ArtikelInProjekt, Long> {
    /**
     * Bedarfspositionen in einem bestimmten Workflow-Zustand
     * (OFFEN, AUS_LAGER, BESTELLT), sortiert nach Projekt.
     * Lieferant lebt nach Stufe A2 auf der Bestellung, nicht mehr auf der AiP.
     */
    List<ArtikelInProjekt> findByQuelleOrderByProjekt_BauvorhabenAsc(BestellQuelle quelle);

    List<ArtikelInProjekt> findByProjekt_Id(Long projektId);

    @Query("SELECT new org.example.kalkulationsprogramm.dto.Projekt.MaterialKilogrammDto(w.name, SUM(aip.kilogramm)) " +
            "FROM ArtikelInProjekt aip " +
            "JOIN aip.artikel a " +
            "JOIN a.werkstoff w " +
            "WHERE aip.projekt.id = :projektId " +
            "GROUP BY w.name")
    List<MaterialKilogrammDto> sumKilogrammByProjektGroupedByWerkstoff(@Param("projektId") Long projektId);
}
