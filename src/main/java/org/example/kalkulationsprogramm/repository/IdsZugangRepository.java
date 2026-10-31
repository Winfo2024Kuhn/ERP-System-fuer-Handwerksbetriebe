package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.ids.IdsZugang;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IdsZugangRepository extends JpaRepository<IdsZugang, Long> {

    Optional<IdsZugang> findByLieferant_Id(Long lieferantId);

    /** Alle Zugänge mit Lieferantenname, alphabetisch – für die Einstellungen. */
    @Query("SELECT z FROM IdsZugang z JOIN FETCH z.lieferant l ORDER BY lower(l.lieferantenname) ASC")
    List<IdsZugang> findeAlleMitLieferant();
}
