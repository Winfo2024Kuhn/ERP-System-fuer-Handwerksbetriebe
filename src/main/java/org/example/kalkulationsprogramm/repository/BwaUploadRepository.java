package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.BwaTyp;
import org.example.kalkulationsprogramm.domain.BwaUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BwaUploadRepository extends JpaRepository<BwaUpload, Long> {

    List<BwaUpload> findByJahrOrderByMonatDesc(Integer jahr);

    List<BwaUpload> findByJahrAndTypOrderByMonatDesc(Integer jahr, BwaTyp typ);

    Optional<BwaUpload> findByJahrAndMonat(Integer jahr, Integer monat);

    List<BwaUpload> findByFreigegebenTrueOrderByJahrDescMonatDesc();

    /**
     * Findet alle freigegebenen BWAs für ein Jahr.
     */
    List<BwaUpload> findByJahrAndFreigegebenTrue(Integer jahr);

    /**
     * Prüft ob für einen Monat bereits eine BWA existiert.
     */
    boolean existsByJahrAndMonat(Integer jahr, Integer monat);

    /**
     * Findet alle noch nicht freigegebenen BWAs.
     */
    List<BwaUpload> findByFreigegebenFalseAndAnalysiertTrueOrderByJahrDescMonatDesc();

    /**
     * Berechnet die Summe der freigegebenen Gemeinkosten für ein Jahr.
     */
    @Query("SELECT COALESCE(SUM(b.kostenAusBwa), 0) FROM BwaUpload b WHERE b.jahr = :jahr AND b.freigegeben = true")
    java.math.BigDecimal summeGemeinkostenAusBwa(@Param("jahr") Integer jahr);

    boolean existsBySourceEmailIdAndOriginalDateiname(Long emailId, String originalDateiname);
}
