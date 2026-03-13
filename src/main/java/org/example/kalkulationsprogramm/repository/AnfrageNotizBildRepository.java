package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.AnfrageNotizBild;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnfrageNotizBildRepository extends JpaRepository<AnfrageNotizBild, Long> {

    /**
     * Findet alle Bilder zu einer Notiz.
     */
    List<AnfrageNotizBild> findByNotizId(Long notizId);
}
