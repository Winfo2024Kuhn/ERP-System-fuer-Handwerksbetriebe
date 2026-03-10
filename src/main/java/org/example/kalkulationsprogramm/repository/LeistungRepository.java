package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Leistung;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LeistungRepository extends JpaRepository<Leistung, Long> {
    List<Leistung> findByBezeichnungContainingIgnoreCase(String search);
}
