package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;
import org.example.kalkulationsprogramm.domain.einkauf.HiCadImport;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HiCadImportRepository extends JpaRepository<HiCadImport, Long> {
    Optional<HiCadImport> findFirstByProjektIdAndDateiHashOrderByIdDesc(Long projektId, String dateiHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from HiCadImport i where i.id = :id")
    Optional<HiCadImport> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from HiCadImport i where i.projektId = :projektId order by i.id")
    List<HiCadImport> findAllByProjektIdForUpdate(@Param("projektId") Long projektId);
}
