package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.LieferantNotiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LieferantNotizRepository extends JpaRepository<LieferantNotiz, Long> {

    List<LieferantNotiz> findByLieferantIdOrderByErstelltAmDesc(Long lieferantId);

    List<LieferantNotiz> findByLieferantIdAndTextContainingIgnoreCaseOrderByErstelltAmDesc(
            Long lieferantId, String query);
}
