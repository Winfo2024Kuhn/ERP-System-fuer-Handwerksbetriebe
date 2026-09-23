package org.example.kalkulationsprogramm.repository;

import java.util.List;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufZeugnisErwartung;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EinkaufZeugnisRepository extends JpaRepository<EinkaufZeugnisErwartung, Long> {
    List<EinkaufZeugnisErwartung> findByRevision_IdOrderByIdAsc(Long revisionId);
    List<EinkaufZeugnisErwartung> findByBestellPosition_IdIn(List<Long> positionIds);
    List<EinkaufZeugnisErwartung> findByRevision_Bestellung_IdOrderByFristAscIdAsc(Long bestellungId);
}
