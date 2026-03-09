package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArbeitsgangStundensatzRepository extends JpaRepository<ArbeitsgangStundensatz, Long> {
    Optional<ArbeitsgangStundensatz> findTopByArbeitsgangIdOrderByJahrDesc(Long arbeitsgangId);
    Optional<ArbeitsgangStundensatz> findTopByArbeitsgangIdAndJahrOrderByIdDesc(Long arbeitsgangId, int jahr);
    
    // Fallback: Nächster verfügbarer Stundensatz ab einem Jahr (aufsteigend)
    Optional<ArbeitsgangStundensatz> findTopByArbeitsgangIdAndJahrGreaterThanEqualOrderByJahrAsc(Long arbeitsgangId, int jahr);
}
