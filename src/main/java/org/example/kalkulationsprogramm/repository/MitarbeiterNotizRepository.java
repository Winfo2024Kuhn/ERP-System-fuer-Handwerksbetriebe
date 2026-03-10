package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.MitarbeiterNotiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MitarbeiterNotizRepository extends JpaRepository<MitarbeiterNotiz, Long> {
    List<MitarbeiterNotiz> findByMitarbeiterIdOrderByErstelltAmDesc(Long mitarbeiterId);
}
