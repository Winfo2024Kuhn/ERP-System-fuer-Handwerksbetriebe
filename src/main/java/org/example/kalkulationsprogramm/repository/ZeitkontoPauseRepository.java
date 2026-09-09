package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.ZeitkontoPause;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface ZeitkontoPauseRepository extends JpaRepository<ZeitkontoPause, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<ZeitkontoPause> findByMitarbeiterIdAndGueltigBisIsNull(Long mitarbeiterId);
    List<ZeitkontoPause> findByMitarbeiterIdOrderByGueltigVonAsc(Long mitarbeiterId);
}
