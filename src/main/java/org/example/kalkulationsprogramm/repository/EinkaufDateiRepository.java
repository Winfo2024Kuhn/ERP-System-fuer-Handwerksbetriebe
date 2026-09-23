package org.example.kalkulationsprogramm.repository;

import java.util.Optional;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufDatei;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EinkaufDateiRepository extends JpaRepository<EinkaufDatei, Long> {
    Optional<EinkaufDatei> findBySha256(String sha256);
}
