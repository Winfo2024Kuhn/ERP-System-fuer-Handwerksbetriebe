package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.einkauf.EinkaufMengenbuchung;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EinkaufMengenbuchungRepository extends JpaRepository<EinkaufMengenbuchung, Long> {
    List<EinkaufMengenbuchung> findAllByVorgangsschluesselOrderByIdAsc(String vorgangsschluessel);
    List<EinkaufMengenbuchung> findAllByIdempotenzKey(UUID idempotenzKey);
    List<EinkaufMengenbuchung> findAllByBedarf_IdAndVorgangsschluesselOrderByIdAsc(Long bedarfId,
            String vorgangsschluessel);
    boolean existsByBedarf_Id(Long bedarfId);
}
