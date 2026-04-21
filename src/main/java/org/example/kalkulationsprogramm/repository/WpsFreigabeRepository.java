package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.WpsFreigabe;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WpsFreigabeRepository extends JpaRepository<WpsFreigabe, Long> {

    List<WpsFreigabe> findByWpsIdOrderByZeitpunktAsc(Long wpsId);
}
