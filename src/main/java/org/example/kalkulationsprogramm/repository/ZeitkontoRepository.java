package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Zeitkonto;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ZeitkontoRepository extends JpaRepository<Zeitkonto, Long> {

    Optional<Zeitkonto> findByMitarbeiter(Mitarbeiter mitarbeiter);

    Optional<Zeitkonto> findByMitarbeiterId(Long mitarbeiterId);

    boolean existsByMitarbeiterId(Long mitarbeiterId);
}
