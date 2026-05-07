package org.example.kalkulationsprogramm.repository;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.LieferantIdsKonfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LieferantIdsKonfigRepository extends JpaRepository<LieferantIdsKonfig, Long> {
    Optional<LieferantIdsKonfig> findByLieferantId(Long lieferantId);
}
