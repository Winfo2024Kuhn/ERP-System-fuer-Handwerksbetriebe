package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;
import org.example.kalkulationsprogramm.domain.einkauf.AngebotVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface AngebotVersionRepository extends JpaRepository<AngebotVersion, Long> {
    List<AngebotVersion> findByAngebotIdOrderByNummerAsc(Long angebotId);
    Optional<AngebotVersion> findFirstByAngebotIdOrderByNummerDesc(Long angebotId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AngebotVersion> findById(Long id);
}
