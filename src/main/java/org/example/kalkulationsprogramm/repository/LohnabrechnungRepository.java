package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Lohnabrechnung;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LohnabrechnungRepository extends JpaRepository<Lohnabrechnung, Long> {

    /**
     * Findet alle Lohnabrechnungen eines Mitarbeiters, sortiert nach Jahr und Monat absteigend.
     */
    List<Lohnabrechnung> findByMitarbeiterIdOrderByJahrDescMonatDesc(Long mitarbeiterId);

    /**
     * Findet alle Lohnabrechnungen eines Jahres.
     */
    List<Lohnabrechnung> findByJahrOrderByMonatDescMitarbeiterNachnameAsc(Integer jahr);

    /**
     * Findet alle Lohnabrechnungen eines Steuerberaters in einem Jahr.
     */
    List<Lohnabrechnung> findBySteuerberaterIdAndJahrOrderByMonatDescMitarbeiterNachnameAsc(
            Long steuerberaterId, Integer jahr);

    /**
     * Findet Lohnabrechnung für Mitarbeiter in einem bestimmten Monat/Jahr.
     */
    Optional<Lohnabrechnung> findByMitarbeiterIdAndJahrAndMonat(
            Long mitarbeiterId, Integer jahr, Integer monat);

    /**
     * Zählt Lohnabrechnungen pro Jahr.
     */
    @Query("SELECT l.jahr, COUNT(l) FROM Lohnabrechnung l GROUP BY l.jahr ORDER BY l.jahr DESC")
    List<Object[]> countByJahrGrouped();

    /**
     * Findet alle verfügbaren Jahre.
     */
    @Query("SELECT DISTINCT l.jahr FROM Lohnabrechnung l ORDER BY l.jahr DESC")
    List<Integer> findDistinctJahre();

    /**
     * Findet Lohnabrechnungen anhand der Quell-Email.
     */
    List<Lohnabrechnung> findBySourceEmailId(Long emailId);

    boolean existsBySourceEmailIdAndOriginalDateiname(Long emailId, String originalDateiname);
}
