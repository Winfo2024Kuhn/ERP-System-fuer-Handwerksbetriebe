package org.example.kalkulationsprogramm.repository;

import java.util.Optional;
import org.example.kalkulationsprogramm.domain.einkauf.HiCadImport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HiCadImportRepository extends JpaRepository<HiCadImport, Long> {
    Optional<HiCadImport> findFirstByProjektIdAndDateiHashOrderByIdDesc(Long projektId, String dateiHash);
}
