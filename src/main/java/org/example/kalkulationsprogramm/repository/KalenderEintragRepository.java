package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.KalenderEintrag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface KalenderEintragRepository extends JpaRepository<KalenderEintrag, Long> {

        /**
         * Findet alle Einträge im angegebenen Datumsbereich, sortiert nach Datum und
         * Startzeit.
         */
        @Query("SELECT k FROM KalenderEintrag k " +
                        "LEFT JOIN FETCH k.projekt " +
                        "LEFT JOIN FETCH k.kunde " +
                        "LEFT JOIN FETCH k.lieferant " +
                        "LEFT JOIN FETCH k.anfrage " +
                        "LEFT JOIN FETCH k.ersteller " +
                        "WHERE k.datum BETWEEN :von AND :bis " +
                        "ORDER BY k.datum, k.startZeit")
        List<KalenderEintrag> findByDatumBetween(@Param("von") LocalDate von, @Param("bis") LocalDate bis);

        /**
         * Findet alle Einträge für einen Mitarbeiter in einem Datumsbereich.
         * Zeigt Einträge, wo der Mitarbeiter Ersteller ODER Teilnehmer ist.
         * Firmenkalender-Einträge (ersteller = null UND keine Teilnehmer) werden allen
         * gezeigt.
         */
        @Query("SELECT DISTINCT k FROM KalenderEintrag k " +
                        "LEFT JOIN FETCH k.projekt " +
                        "LEFT JOIN FETCH k.kunde " +
                        "LEFT JOIN FETCH k.lieferant " +
                        "LEFT JOIN FETCH k.anfrage " +
                        "LEFT JOIN FETCH k.ersteller " +
                        "LEFT JOIN k.teilnehmer t " +
                        "WHERE k.datum BETWEEN :von AND :bis " +
                        "AND (k.ersteller.id = :mitarbeiterId " +
                        "     OR t.id = :mitarbeiterId " +
                        "     OR (k.ersteller IS NULL AND SIZE(k.teilnehmer) = 0)) " +
                        "ORDER BY k.datum, k.startZeit")
        List<KalenderEintrag> findByMitarbeiterAndDatumBetween(
                        @Param("mitarbeiterId") Long mitarbeiterId,
                        @Param("von") LocalDate von,
                        @Param("bis") LocalDate bis);

        /**
         * Findet alle Einträge für einen Mitarbeiter an einem bestimmten Tag.
         * Zeigt Einträge, wo der Mitarbeiter Ersteller ODER Teilnehmer ist.
         * Firmenkalender-Einträge (ersteller = null UND keine Teilnehmer) werden allen
         * gezeigt.
         */
        @Query("SELECT DISTINCT k FROM KalenderEintrag k " +
                        "LEFT JOIN FETCH k.projekt " +
                        "LEFT JOIN FETCH k.kunde " +
                        "LEFT JOIN FETCH k.lieferant " +
                        "LEFT JOIN FETCH k.anfrage " +
                        "LEFT JOIN FETCH k.ersteller " +
                        "LEFT JOIN k.teilnehmer t " +
                        "WHERE k.datum = :datum " +
                        "AND (k.ersteller.id = :mitarbeiterId " +
                        "     OR t.id = :mitarbeiterId " +
                        "     OR (k.ersteller IS NULL AND SIZE(k.teilnehmer) = 0)) " +
                        "ORDER BY k.startZeit")
        List<KalenderEintrag> findByMitarbeiterAndDatum(
                        @Param("mitarbeiterId") Long mitarbeiterId,
                        @Param("datum") LocalDate datum);

        /**
         * Findet alle Einträge für ein bestimmtes Projekt.
         */
        List<KalenderEintrag> findByProjektIdOrderByDatumDesc(Long projektId);

        /**
         * Findet alle Einträge für einen bestimmten Kunden.
         */
        List<KalenderEintrag> findByKundeIdOrderByDatumDesc(Long kundeId);

        /**
         * Findet alle Einträge für einen bestimmten Lieferanten.
         */
        List<KalenderEintrag> findByLieferantIdOrderByDatumDesc(Long lieferantId);

        /**
         * Findet alle Einträge für ein bestimmtes Anfrage.
         */
        List<KalenderEintrag> findByAnfrageIdOrderByDatumDesc(Long anfrageId);

        /**
         * Lädt Teilnehmer für einen Kalendereintrag.
         */
        @Query("SELECT k FROM KalenderEintrag k LEFT JOIN FETCH k.teilnehmer WHERE k.id = :id")
        KalenderEintrag findByIdWithTeilnehmer(@Param("id") Long id);
}
