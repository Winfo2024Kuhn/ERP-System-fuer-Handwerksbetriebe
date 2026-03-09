package org.example.kalkulationsprogramm.repository.miete;

import org.example.kalkulationsprogramm.domain.miete.Mietobjekt;
import org.example.kalkulationsprogramm.domain.miete.Raum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RaumRepository extends JpaRepository<Raum, Long> {
    List<Raum> findByMietobjektOrderByNameAsc(Mietobjekt mietobjekt);
}
