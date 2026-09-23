package org.example.kalkulationsprogramm.repository;

import jakarta.persistence.LockModeType;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufVersandAnnahmeereignis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;
import java.util.Set;

public interface EinkaufVersandAnnahmeereignisRepository extends JpaRepository<EinkaufVersandAnnahmeereignis, Long> {
    Optional<EinkaufVersandAnnahmeereignis> findByEreignisSchluessel(UUID ereignisSchluessel);
    Optional<EinkaufVersandAnnahmeereignis> findByVersandauftragId(Long versandauftragId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EinkaufVersandAnnahmeereignis e where e.verarbeitetAm is null "
            + "and e.vorgangTyp in :vorgangTypen order by e.id")
    java.util.List<EinkaufVersandAnnahmeereignis> sperreOffene(
            @org.springframework.data.repository.query.Param("vorgangTypen") Set<String> vorgangTypen, Pageable pageable);
}
