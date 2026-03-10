package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.DokumentnummerCounter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DokumentnummerCounterRepository extends JpaRepository<DokumentnummerCounter, Long> {
    Optional<DokumentnummerCounter> findByMonthKey(String monthKey);
}
