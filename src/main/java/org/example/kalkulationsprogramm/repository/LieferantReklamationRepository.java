package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.LieferantReklamation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LieferantReklamationRepository extends JpaRepository<LieferantReklamation, Long> {
    List<LieferantReklamation> findByLieferantIdOrderByStatusAscErstelltAmDesc(Long lieferantId);
}
