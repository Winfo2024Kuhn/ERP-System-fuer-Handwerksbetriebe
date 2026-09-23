package org.example.kalkulationsprogramm.repository;

import java.util.Optional;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufAngebot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface EinkaufAngebotRepository extends JpaRepository<EinkaufAngebot, Long> {
    Optional<EinkaufAngebot> findByBeteiligungId(Long beteiligungId);
    @Query("select distinct a from EinkaufAngebot a join fetch a.beteiligung b join fetch b.revision r "
            + "where r.anfrage.id = :anfrageId order by a.id")
    List<EinkaufAngebot> findAllByBeteiligungRevisionAnfrageIdOrderById(@Param("anfrageId") Long anfrageId);
}
