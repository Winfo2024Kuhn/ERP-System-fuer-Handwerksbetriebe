package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Preisanfrage;
import org.example.kalkulationsprogramm.domain.PreisanfrageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PreisanfrageRepository extends JpaRepository<Preisanfrage, Long> {

    Optional<Preisanfrage> findByNummer(String nummer);

    List<Preisanfrage> findByStatusOrderByErstelltAmDesc(PreisanfrageStatus status);

    /**
     * Hoechste bereits vergebene laufende Nummer fuer ein gegebenes Praefix,
     * z. B. {@code PA-2026-} gibt 41 zurueck, wenn PA-2026-041 die hoechste ist.
     * Dient der Nummern-Generierung im Service.
     */
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(p.nummer, LENGTH(:prefix) + 1) AS integer)), 0) "
            + "FROM Preisanfrage p WHERE p.nummer LIKE CONCAT(:prefix, '%')")
    int findMaxLfdNrByPrefix(@Param("prefix") String prefix);
}
