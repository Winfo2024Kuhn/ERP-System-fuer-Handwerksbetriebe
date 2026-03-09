package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.ProjektNotizBild;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjektNotizBildRepository extends JpaRepository<ProjektNotizBild, Long> {

    /**
     * Findet alle Bilder zu einer Notiz.
     */
    List<ProjektNotizBild> findByNotizId(Long notizId);

    /**
     * Löscht alle Bilder zu einer Notiz.
     */
    void deleteByNotizId(Long notizId);
}
