package org.example.kalkulationsprogramm.repository;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WerkstoffRepository extends JpaRepository<Werkstoff, Long> {
	Optional<Werkstoff> findByNameIgnoreCase(String name);
}

