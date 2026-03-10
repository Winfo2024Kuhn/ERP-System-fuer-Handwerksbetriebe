package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Urlaubsantrag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface UrlaubsantragRepository extends JpaRepository<Urlaubsantrag, Long> {
    List<Urlaubsantrag> findByMitarbeiterId(Long mitarbeiterId);

    List<Urlaubsantrag> findByStatus(Urlaubsantrag.Status status);

    List<Urlaubsantrag> findByMitarbeiterIdOrderByVonDatumDesc(Long mitarbeiterId);

    @Query("SELECT u FROM Urlaubsantrag u WHERE u.mitarbeiter.id = :mitarbeiterId " +
            "AND u.status != 'ABGELEHNT' " +
            "AND ((u.vonDatum <= :end) AND (u.bisDatum >= :start))")
    List<Urlaubsantrag> findOverlapping(@Param("mitarbeiterId") Long mitarbeiterId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    List<Urlaubsantrag> findByMitarbeiterIdAndVonDatumBetweenOrderByVonDatumDesc(Long mitarbeiterId, LocalDate start,
            LocalDate end);

    List<Urlaubsantrag> findByMitarbeiterIdAndStatusOrderByVonDatumDesc(Long mitarbeiterId,
            Urlaubsantrag.Status status);
}
