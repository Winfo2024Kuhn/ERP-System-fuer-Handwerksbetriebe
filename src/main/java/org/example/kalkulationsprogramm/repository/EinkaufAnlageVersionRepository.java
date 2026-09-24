package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufAnlageVersion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EinkaufAnlageVersionRepository extends JpaRepository<EinkaufAnlageVersion, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "datei")
    List<EinkaufAnlageVersion> findByBedarfIdOrderByIdAsc(Long bedarfId);
    Optional<EinkaufAnlageVersion> findByBedarfIdAndRevision(Long bedarfId, String revision);
    List<EinkaufAnlageVersion> findAllByIdIn(List<Long> ids);
    List<EinkaufAnlageVersion> findByBedarfIdAndFreigegebenTrue(Long bedarfId);
}
