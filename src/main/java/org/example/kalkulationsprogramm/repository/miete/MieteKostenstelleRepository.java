package org.example.kalkulationsprogramm.repository.miete;

import org.example.kalkulationsprogramm.domain.miete.Kostenstelle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MieteKostenstelleRepository extends JpaRepository<Kostenstelle, Long> {
    List<Kostenstelle> findByMietobjektIdOrderByNameAsc(Long mietobjektId);
}
