package org.example.kalkulationsprogramm.repository;

import java.util.*;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBelegPosition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EinkaufBelegPositionRepository extends JpaRepository<EinkaufBelegPosition,Long> {
    List<EinkaufBelegPosition> findByDokumentIdOrderByIdAsc(Long dokumentId);
    List<EinkaufBelegPosition> findByBestellPositionIdOrderByIdAsc(Long bestellPositionId);
    @org.springframework.data.jpa.repository.Query("select p from EinkaufBelegPosition p join fetch p.dokument left join fetch p.bestellPosition where p.dokument.id in :ids order by p.id")
    List<EinkaufBelegPosition> findeAlleBelege(@org.springframework.data.repository.query.Param("ids") Collection<Long> ids);
    boolean existsByDokumentId(Long dokumentId);
}
