package org.example.kalkulationsprogramm.repository.miete;

import org.example.kalkulationsprogramm.domain.miete.Mietobjekt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MietobjektRepository extends JpaRepository<Mietobjekt, Long> {
    Optional<Mietobjekt> findByNameIgnoreCase(String name);
}
