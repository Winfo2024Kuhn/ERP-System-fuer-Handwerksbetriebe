package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.WpsProjektZuweisung;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WpsProjektZuweisungRepository extends JpaRepository<WpsProjektZuweisung, Long> {

    List<WpsProjektZuweisung> findByProjektId(Long projektId);
}
