package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Langzeitkrankmeldung;
import org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository fuer {@link Langzeitkrankmeldung}.
 */
@Repository
public interface LangzeitkrankmeldungRepository extends JpaRepository<Langzeitkrankmeldung, Long> {

    List<Langzeitkrankmeldung> findByMitarbeiterIdOrderByBeginnDesc(Long mitarbeiterId);

    @Query("SELECT l FROM Langzeitkrankmeldung l JOIN FETCH l.mitarbeiter "
            + "LEFT JOIN FETCH l.phasen WHERE l.status IN :status ORDER BY l.beginn DESC")
    List<Langzeitkrankmeldung> findMitPhasen(@Param("status") Collection<LangzeitkrankmeldungStatus> status);

    @Query("SELECT l FROM Langzeitkrankmeldung l JOIN FETCH l.mitarbeiter "
            + "LEFT JOIN FETCH l.phasen WHERE l.id = :id")
    Optional<Langzeitkrankmeldung> findMitPhasenById(@Param("id") Long id);

    @Query("SELECT l FROM Langzeitkrankmeldung l WHERE l.mitarbeiter.id = :mitarbeiterId "
            + "AND l.status <> org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungStatus.ABGEBROCHEN "
            + "AND l.beginn <= :bis AND (l.ende IS NULL OR l.ende >= :von)")
    List<Langzeitkrankmeldung> findUeberlappende(@Param("mitarbeiterId") Long mitarbeiterId,
            @Param("von") LocalDate von,
            @Param("bis") LocalDate bis);
}
