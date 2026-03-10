package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArbeitsgangRepository extends JpaRepository<Arbeitsgang, Long> {
    // Repository für die Stammdaten der Arbeitsgänge (Planung, Montage etc.).
    java.util.Optional<Arbeitsgang> findByBeschreibung(String bezeichnung);
}
