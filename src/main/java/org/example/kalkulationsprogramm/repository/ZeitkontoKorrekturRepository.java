package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.ZeitkontoKorrektur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Repository für Zeitkonto-Korrekturen.
 */
@Repository
public interface ZeitkontoKorrekturRepository extends JpaRepository<ZeitkontoKorrektur, Long> {

    /**
     * Findet alle Korrekturen eines Mitarbeiters, sortiert nach Datum absteigend.
     */
    List<ZeitkontoKorrektur> findByMitarbeiterIdOrderByDatumDesc(Long mitarbeiterId);

    /**
     * Findet Korrekturen eines Mitarbeiters in einem Zeitraum.
     */
    @Query("SELECT k FROM ZeitkontoKorrektur k WHERE k.mitarbeiter.id = :mitarbeiterId AND k.datum >= :von AND k.datum <= :bis ORDER BY k.datum ASC")
    List<ZeitkontoKorrektur> findByMitarbeiterIdAndDatumBetween(
            @Param("mitarbeiterId") Long mitarbeiterId,
            @Param("von") LocalDate von,
            @Param("bis") LocalDate bis);

    /**
     * Summiert alle Korrekturstunden eines Mitarbeiters in einem Zeitraum.
     */
    @Query("SELECT COALESCE(SUM(k.stunden), 0) FROM ZeitkontoKorrektur k WHERE k.mitarbeiter.id = :mitarbeiterId AND k.datum >= :von AND k.datum <= :bis")
    BigDecimal sumStundenByMitarbeiterIdAndDatumBetween(
            @Param("mitarbeiterId") Long mitarbeiterId,
            @Param("von") LocalDate von,
            @Param("bis") LocalDate bis);

    /**
     * Summiert alle Korrekturstunden eines Mitarbeiters für ein gesamtes Jahr.
     */
    @Query("SELECT COALESCE(SUM(k.stunden), 0) FROM ZeitkontoKorrektur k WHERE k.mitarbeiter.id = :mitarbeiterId AND YEAR(k.datum) = :jahr")
    BigDecimal sumStundenByMitarbeiterIdAndJahr(
            @Param("mitarbeiterId") Long mitarbeiterId,
            @Param("jahr") int jahr);
}
