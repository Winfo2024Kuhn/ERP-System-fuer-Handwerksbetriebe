package org.example.kalkulationsprogramm.repository;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.UrlaubUebertrag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UrlaubUebertragRepository extends JpaRepository<UrlaubUebertrag, Long> {

    Optional<UrlaubUebertrag> findByMitarbeiterIdAndJahr(Long mitarbeiterId, Integer jahr);
}
