package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.MitarbeiterQualifikation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MitarbeiterQualifikationRepository extends JpaRepository<MitarbeiterQualifikation, Long> {
    List<MitarbeiterQualifikation> findByMitarbeiterIdOrderByErstelltAmDesc(Long mitarbeiterId);
}
