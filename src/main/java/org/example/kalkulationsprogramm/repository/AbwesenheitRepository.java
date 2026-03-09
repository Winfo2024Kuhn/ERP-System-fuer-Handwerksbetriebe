package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Abwesenheit;
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository für Abwesenheiten (Urlaub, Krankheit, Fortbildung).
 */
@Repository
public interface AbwesenheitRepository extends JpaRepository<Abwesenheit, Long> {

        /**
         * Findet alle Abwesenheiten eines Mitarbeiters.
         */
        List<Abwesenheit> findByMitarbeiterIdOrderByDatumDesc(Long mitarbeiterId);

        /**
         * Findet Abwesenheiten eines Mitarbeiters in einem Zeitraum.
         */
        @Query("SELECT a FROM Abwesenheit a WHERE a.mitarbeiter.id = :mitarbeiterId AND a.datum >= :von AND a.datum <= :bis ORDER BY a.datum ASC")
        List<Abwesenheit> findByMitarbeiterIdAndDatumBetween(
                        @Param("mitarbeiterId") Long mitarbeiterId,
                        @Param("von") LocalDate von,
                        @Param("bis") LocalDate bis);

        /**
         * Findet Abwesenheiten eines Mitarbeiters nach Typ.
         */
        List<Abwesenheit> findByMitarbeiterIdAndTypOrderByDatumDesc(Long mitarbeiterId, AbwesenheitsTyp typ);

        /**
         * Findet Abwesenheiten für einen Urlaubsantrag.
         */
        List<Abwesenheit> findByUrlaubsantragId(Long urlaubsantragId);

        /**
         * Löscht alle Abwesenheiten für einen Urlaubsantrag (z.B. bei Stornierung).
         */
        void deleteByUrlaubsantragId(Long urlaubsantragId);

        /**
         * Zählt Urlaubstage eines Mitarbeiters in einem Jahr.
         */
        @Query("SELECT COUNT(a) FROM Abwesenheit a WHERE a.mitarbeiter.id = :mitarbeiterId AND a.typ = :typ AND YEAR(a.datum) = :jahr")
        long countByMitarbeiterIdAndTypAndJahr(
                        @Param("mitarbeiterId") Long mitarbeiterId,
                        @Param("typ") AbwesenheitsTyp typ,
                        @Param("jahr") int jahr);

        /**
         * Summiert Stunden für Abwesenheiten eines Mitarbeiters in einem Zeitraum.
         */
        @Query("SELECT COALESCE(SUM(a.stunden), 0) FROM Abwesenheit a WHERE a.mitarbeiter.id = :mitarbeiterId AND a.datum >= :von AND a.datum <= :bis")
        java.math.BigDecimal sumStundenByMitarbeiterIdAndDatumBetween(
                        @Param("mitarbeiterId") Long mitarbeiterId,
                        @Param("von") LocalDate von,
                        @Param("bis") LocalDate bis);

        /**
         * Prüft ob für einen Mitarbeiter an einem Datum bereits eine Abwesenheit
         * existiert.
         */
        boolean existsByMitarbeiterIdAndDatumAndTyp(Long mitarbeiterId, LocalDate datum, AbwesenheitsTyp typ);

        /**
         * Findet alle Abwesenheiten aller Mitarbeiter in einem Zeitraum (für
         * Team-Kalender).
         */
        @Query("SELECT a FROM Abwesenheit a JOIN FETCH a.mitarbeiter WHERE a.datum >= :von AND a.datum <= :bis ORDER BY a.datum ASC, a.mitarbeiter.nachname ASC")
        List<Abwesenheit> findAllByDatumBetween(@Param("von") LocalDate von, @Param("bis") LocalDate bis);
}
