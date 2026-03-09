package org.example.kalkulationsprogramm.repository.miete;

import org.example.kalkulationsprogramm.domain.miete.Mietobjekt;
import org.example.kalkulationsprogramm.domain.miete.Mietpartei;
import org.example.kalkulationsprogramm.domain.miete.MietparteiRolle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MietparteiRepository extends JpaRepository<Mietpartei, Long> {
    List<Mietpartei> findByMietobjektOrderByNameAsc(Mietobjekt mietobjekt);

    List<Mietpartei> findByMietobjektAndRolle(Mietobjekt mietobjekt, MietparteiRolle rolle);
}
