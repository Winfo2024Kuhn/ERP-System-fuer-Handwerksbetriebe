package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.KassenbuchMonatsabschluss;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Zugriff auf die abgeschlossenen Kassenbuch-Monate: lesen und anlegen.
 *
 * <p>Bewusst {@link Repository} statt {@code JpaRepository} -- damit gibt es
 * keinen Delete-Pfad. Einen Abschluss wieder aufzumachen waere genau die
 * Hintertuer, gegen die die Festschreibung gebaut ist.</p>
 */
public interface KassenbuchMonatsabschlussRepository extends Repository<KassenbuchMonatsabschluss, Long> {

    KassenbuchMonatsabschluss saveAndFlush(KassenbuchMonatsabschluss abschluss);

    Optional<KassenbuchMonatsabschluss> findByJahrAndMonat(Integer jahr, Integer monat);

    boolean existsByJahrAndMonat(Integer jahr, Integer monat);

    List<KassenbuchMonatsabschluss> findAllByOrderByJahrDescMonatDesc();

    /**
     * Zuletzt abgeschlossener Monat. Der Abschluss muss lueckenlos
     * fortschreiten -- man kann nicht den Maerz abschliessen, solange der
     * Februar offen ist, sonst waeren die Nummern nicht mehr chronologisch.
     */
    Optional<KassenbuchMonatsabschluss> findFirstByOrderByJahrDescMonatDesc();

    /**
     * Gibt es fuer den Monat, in dem dieses Datum liegt, schon einen
     * Abschluss? Damit blockt der Service nachtraegliche Buchungen in einen
     * bereits abgeschlossenen Monat.
     */
    @Query("SELECT COUNT(m) > 0 FROM KassenbuchMonatsabschluss m " +
           "WHERE (m.jahr > :jahr) OR (m.jahr = :jahr AND m.monat >= :monat)")
    boolean existsAbschlussAbMonat(@Param("jahr") Integer jahr, @Param("monat") Integer monat);
}
