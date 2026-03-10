package org.example.kalkulationsprogramm.repository.miete;

import org.example.kalkulationsprogramm.domain.miete.Mietobjekt;
import org.example.kalkulationsprogramm.domain.miete.Verteilungsschluessel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerteilungsschluesselRepository extends JpaRepository<Verteilungsschluessel, Long> {
    List<Verteilungsschluessel> findByMietobjektOrderByNameAsc(Mietobjekt mietobjekt);
}
