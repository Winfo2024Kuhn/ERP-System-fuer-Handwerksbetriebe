package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MitarbeiterRepository extends JpaRepository<Mitarbeiter, Long> {
    Optional<Mitarbeiter> findByLoginToken(String loginToken);
    
    // Für Zeiterfassung: nur aktive Mitarbeiter dürfen sich einloggen
    Optional<Mitarbeiter> findByLoginTokenAndAktivTrue(String loginToken);
    
    // Für Lohnabrechnung-Zuweisung: alle aktiven Mitarbeiter
    java.util.List<Mitarbeiter> findByAktivTrue();
}
