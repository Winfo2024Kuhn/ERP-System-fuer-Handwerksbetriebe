package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Leistung;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface LeistungRepository extends JpaRepository<Leistung, Long> {
    List<Leistung> findByBezeichnungContainingIgnoreCase(String search);

    List<Leistung> findByKategorieId(Long kategorieId);

    List<Leistung> findByKategorieIdIn(Collection<Long> kategorieIds);

    boolean existsByKategorieId(Long kategorieId);

    /** Anzahl Leistungen je Ordner: {@code [kategorieId, anzahl]}. */
    @Query("SELECT l.kategorie.id, COUNT(l) FROM Leistung l WHERE l.kategorie IS NOT NULL GROUP BY l.kategorie.id")
    List<Object[]> zaehleJeKategorie();
}
