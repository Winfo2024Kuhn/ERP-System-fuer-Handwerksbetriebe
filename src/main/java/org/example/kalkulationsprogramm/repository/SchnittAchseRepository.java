package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.SchnittAchse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchnittAchseRepository extends JpaRepository<SchnittAchse, Long> {
    List<SchnittAchse> findByKategorie_IdOrderByIdAsc(Integer kategorieId);
}
