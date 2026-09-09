package org.example.kalkulationsprogramm.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.ZeitkontoVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ZeitkontoVersionRepository extends JpaRepository<ZeitkontoVersion, Long> {
    /** Inklusive Grenzen; Herkunft mitladen, um spätere Vergleiche ohne N+1 zu erlauben. */
    @Query("""
            SELECT v FROM ZeitkontoVersion v LEFT JOIN FETCH v.vorlage
            WHERE v.mitarbeiter.id = :mitarbeiterId AND v.gueltigVon <= :bis
              AND (v.gueltigBis IS NULL OR v.gueltigBis >= :von)
            ORDER BY v.gueltigVon, v.id
            """)
    List<ZeitkontoVersion> findImZeitraum(@Param("mitarbeiterId") Long mitarbeiterId,
            @Param("von") LocalDate von, @Param("bis") LocalDate bis);

    @Query("""
            SELECT v FROM ZeitkontoVersion v LEFT JOIN FETCH v.vorlage
            WHERE v.mitarbeiter.id = :mitarbeiterId AND v.gueltigVon <= :tag
              AND (v.gueltigBis IS NULL OR v.gueltigBis >= :tag)
            """)
    Optional<ZeitkontoVersion> findAm(@Param("mitarbeiterId") Long mitarbeiterId,
            @Param("tag") LocalDate tag);
}
