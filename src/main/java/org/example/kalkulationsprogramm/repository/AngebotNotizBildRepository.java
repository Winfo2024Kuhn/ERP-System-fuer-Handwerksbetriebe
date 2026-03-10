package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.AngebotNotizBild;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AngebotNotizBildRepository extends JpaRepository<AngebotNotizBild, Long> {

    /**
     * Findet alle Bilder zu einer Notiz.
     */
    List<AngebotNotizBild> findByNotizId(Long notizId);
}
