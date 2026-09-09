package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungPhase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository fuer {@link LangzeitkrankmeldungPhase}.
 */
@Repository
public interface LangzeitkrankmeldungPhaseRepository extends JpaRepository<LangzeitkrankmeldungPhase, Long> {

    List<LangzeitkrankmeldungPhase> findByLangzeitkrankmeldungIdOrderByVonDatumAsc(Long meldungId);

    /**
     * Alle Phasen aktiver (nicht abgebrochener) Meldungen, die den Zeitraum
     * beruehren. EINE Abfrage je Zeitraum - TagesSollService wertet sie im
     * Speicher pro Tag aus.
     */
    @Query("SELECT p FROM LangzeitkrankmeldungPhase p "
            + "WHERE p.langzeitkrankmeldung.mitarbeiter.id = :mitarbeiterId "
            + "AND p.langzeitkrankmeldung.status <> org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungStatus.ABGEBROCHEN "
            + "AND p.vonDatum <= :bis AND (p.bisDatum IS NULL OR p.bisDatum >= :von) "
            + "ORDER BY p.vonDatum ASC")
    List<LangzeitkrankmeldungPhase> findImZeitraum(@Param("mitarbeiterId") Long mitarbeiterId,
            @Param("von") LocalDate von,
            @Param("bis") LocalDate bis);
}
