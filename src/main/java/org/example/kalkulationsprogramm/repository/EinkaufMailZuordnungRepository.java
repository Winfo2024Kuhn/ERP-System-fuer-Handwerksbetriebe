package org.example.kalkulationsprogramm.repository;

import java.util.Optional;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufMailZuordnung;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EinkaufMailZuordnungRepository extends JpaRepository<EinkaufMailZuordnung, Long> {
    Optional<EinkaufMailZuordnung> findByEmailId(Long emailId);
    Page<EinkaufMailZuordnung> findAllByTypAndVorgangId(String typ, Long vorgangId, Pageable pageable);
}
