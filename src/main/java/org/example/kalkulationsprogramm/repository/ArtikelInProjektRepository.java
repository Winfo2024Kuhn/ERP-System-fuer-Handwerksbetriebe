package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.dto.Projekt.MaterialKilogrammDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

@Repository
public interface ArtikelInProjektRepository extends JpaRepository<ArtikelInProjekt, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select aip from ArtikelInProjekt aip where aip.id = :id")
    Optional<ArtikelInProjekt> findByIdForUpdate(@Param("id") Long id);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM einkauf_bedarf WHERE artikel_in_projekt_id = :id)",
            nativeQuery = true)
    boolean existsEinkaufBedarfById(@Param("id") Long id);

    List<ArtikelInProjekt> findByBestelltFalseOrderByLieferant_LieferantennameAscProjekt_BauvorhabenAsc();

    List<ArtikelInProjekt> findByBestelltFalseAndLieferant_IdOrderByProjekt_BauvorhabenAsc(Long lieferantId);

    List<ArtikelInProjekt> findByArtikel_IdAndLieferant_IdAndBestelltFalse(Long artikelId, Long lieferantId);

    List<ArtikelInProjekt> findByProjekt_Id(Long projektId);

    @Query("SELECT new org.example.kalkulationsprogramm.dto.Projekt.MaterialKilogrammDto(w.name, SUM(aip.kilogramm)) " +
            "FROM ArtikelInProjekt aip " +
            "JOIN aip.artikel a " +
            "JOIN a.werkstoff w " +
            "WHERE aip.projekt.id = :projektId " +
            "GROUP BY w.name")
    List<MaterialKilogrammDto> sumKilogrammByProjektGroupedByWerkstoff(@Param("projektId") Long projektId);
}
