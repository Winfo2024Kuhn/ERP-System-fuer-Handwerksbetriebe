package org.example.kalkulationsprogramm.repository;

import java.util.*;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBelegPosition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EinkaufBelegPositionRepository extends JpaRepository<EinkaufBelegPosition,Long> {
    List<EinkaufBelegPosition> findByDokumentIdOrderByIdAsc(Long dokumentId);
    List<EinkaufBelegPosition> findByBestellPositionIdOrderByIdAsc(Long bestellPositionId);
    boolean existsByDokumentId(Long dokumentId);
}
