package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.WpsProjektAutoSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WpsProjektAutoSourceRepository extends JpaRepository<WpsProjektAutoSource, Long> {

    List<WpsProjektAutoSource> findByProjektId(Long projektId);

    boolean existsByWpsIdAndProjektIdAndLeistungId(Long wpsId, Long projektId, Long leistungId);
}
