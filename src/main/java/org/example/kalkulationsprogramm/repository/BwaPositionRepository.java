package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.BwaPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BwaPositionRepository extends JpaRepository<BwaPosition, Long> {

    // Als @Query statt abgeleitet: nur so laesst sich festlegen, wohin leere Werte sortieren
    // (wie bisher: aufsteigend zuerst, absteigend zuletzt) - siehe DatenbankKompatibilitaetSortierungTest
    @Query("SELECT p FROM BwaPosition p WHERE p.bwaUpload.id = :bwaUploadId ORDER BY p.kontonummer ASC NULLS FIRST")
    List<BwaPosition> findByBwaUploadIdOrderByKontonummerAsc(@Param("bwaUploadId") Long bwaUploadId);

    List<BwaPosition> findByBwaUploadIdAndKategorie(Long bwaUploadId, String kategorie);

    /**
     * Findet alle Positionen die nicht in Rechnungen gefunden wurden.
     */
    List<BwaPosition> findByBwaUploadIdAndInRechnungenGefundenFalse(Long bwaUploadId);

    /**
     * Kategorisierte Summen für eine BWA.
     */
    @Query("SELECT p.kategorie, SUM(p.betragMonat) FROM BwaPosition p WHERE p.bwaUpload.id = :bwaId GROUP BY p.kategorie")
    List<Object[]> summenNachKategorie(@Param("bwaId") Long bwaId);
}
