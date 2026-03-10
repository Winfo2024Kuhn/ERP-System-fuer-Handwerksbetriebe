package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AbteilungRepository extends JpaRepository<Abteilung, Long> {
    Optional<Abteilung> findByName(String name);
}
