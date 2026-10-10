package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.AnfrageNotizBild;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnfrageNotizBildRepository extends JpaRepository<AnfrageNotizBild, Long> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "notiz", "notiz.anfrage", "notiz.mitarbeiter" })
    List<AnfrageNotizBild> findByGespeicherterDateiname(String gespeicherterDateiname);

    /**
     * Findet alle Bilder zu einer Notiz.
     */
    List<AnfrageNotizBild> findByNotizId(Long notizId);
}
