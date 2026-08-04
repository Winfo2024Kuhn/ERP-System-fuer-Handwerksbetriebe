package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Kassenzaehlung;
import org.springframework.data.repository.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Zugriff auf die Kassenstuerze: lesen und anlegen, sonst nichts.
 *
 * <p>Bewusst {@link Repository} statt {@code JpaRepository} -- Letzteres
 * brachte Loeschmethoden mit, die es hier nicht geben darf. Eine gezaehlte
 * Kasse, die sich nachtraeglich entfernen laesst, beweist nichts.</p>
 */
public interface KassenzaehlungRepository extends Repository<Kassenzaehlung, Long> {

    Kassenzaehlung saveAndFlush(Kassenzaehlung zaehlung);

    List<Kassenzaehlung> findByStichtagBetweenOrderByStichtagAscIdAsc(LocalDate von, LocalDate bis);

    List<Kassenzaehlung> findTop20ByOrderByStichtagDescIdDesc();

    /** Juengste Zaehlung ueberhaupt -- fuer die Anzeige "zuletzt gezaehlt am ...". */
    Optional<Kassenzaehlung> findFirstByOrderByStichtagDescIdDesc();
}
