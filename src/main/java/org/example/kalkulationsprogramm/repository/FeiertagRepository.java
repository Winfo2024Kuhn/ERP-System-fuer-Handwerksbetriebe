package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Feiertag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FeiertagRepository extends JpaRepository<Feiertag, Long> {

    List<Feiertag> findByBundesland(String bundesland);

    @Query("SELECT f FROM Feiertag f WHERE YEAR(f.datum) = :jahr AND f.bundesland = :bundesland ORDER BY f.datum")
    List<Feiertag> findByJahrAndBundesland(int jahr, String bundesland);

    @Query("SELECT f FROM Feiertag f WHERE YEAR(f.datum) = :jahr ORDER BY f.datum")
    List<Feiertag> findByJahr(int jahr);

    boolean existsByDatumAndBundesland(LocalDate datum, String bundesland);

    @Query("SELECT f FROM Feiertag f WHERE f.datum BETWEEN :von AND :bis ORDER BY f.datum")
    List<Feiertag> findByDatumBetween(LocalDate von, LocalDate bis);

    /**
     * Findet einen Feiertag nach Datum und Bundesland.
     */
    java.util.Optional<Feiertag> findByDatumAndBundesland(LocalDate datum, String bundesland);
}
