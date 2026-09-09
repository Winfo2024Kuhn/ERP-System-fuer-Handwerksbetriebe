package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.MonatsSaldo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository für MonatsSaldo-Cache-Einträge.
 */
@Repository
public interface MonatsSaldoRepository extends JpaRepository<MonatsSaldo, Long> {

    /**
     * Findet den Cache-Eintrag für einen bestimmten Mitarbeiter/Monat.
     */
    Optional<MonatsSaldo> findByMitarbeiterIdAndJahrAndMonat(Long mitarbeiterId, Integer jahr, Integer monat);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ms FROM MonatsSaldo ms WHERE ms.mitarbeiter.id = :id AND ms.jahr = :jahr AND ms.monat = :monat")
    Optional<MonatsSaldo> findGesperrt(@Param("id") Long id, @Param("jahr") int jahr, @Param("monat") int monat);

    /**
     * Findet alle gültigen Cache-Einträge für einen Mitarbeiter in einem Jahr.
     */
    List<MonatsSaldo> findByMitarbeiterIdAndJahrAndGueltigTrue(Long mitarbeiterId, Integer jahr);

    /**
     * Findet alle Cache-Einträge für einen Mitarbeiter (alle Jahre).
     */
    List<MonatsSaldo> findByMitarbeiterIdAndGueltigTrue(Long mitarbeiterId);

    /**
     * Findet alle gültigen Cache-Einträge für einen Mitarbeiter im Bereich von/bis (Jahr).
     */
    @Query("SELECT ms FROM MonatsSaldo ms WHERE ms.mitarbeiter.id = :mitarbeiterId " +
           "AND ms.gueltig = true " +
           "AND (ms.jahr * 100 + ms.monat) >= :vonJahrMonat " +
           "AND (ms.jahr * 100 + ms.monat) <= :bisJahrMonat " +
           "ORDER BY ms.jahr, ms.monat")
    List<MonatsSaldo> findGueltigeImZeitraum(
            @Param("mitarbeiterId") Long mitarbeiterId,
            @Param("vonJahrMonat") int vonJahrMonat,
            @Param("bisJahrMonat") int bisJahrMonat);

    /**
     * Invalidiert den Cache für einen bestimmten Monat (setzt gueltig=false).
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE MonatsSaldo ms SET ms.gueltig = false, ms.version = ms.version + 1 WHERE ms.festgeschrieben = false AND ms.mitarbeiter.id = :mitarbeiterId AND ms.jahr = :jahr AND ms.monat = :monat")
    void invalidiere(@Param("mitarbeiterId") Long mitarbeiterId, @Param("jahr") int jahr, @Param("monat") int monat);

    /**
     * Invalidiert den Cache für ein ganzes Jahr (z.B. bei Zeitkonto-Korrekturen).
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE MonatsSaldo ms SET ms.gueltig = false, ms.version = ms.version + 1 WHERE ms.festgeschrieben = false AND ms.mitarbeiter.id = :mitarbeiterId AND ms.jahr = :jahr")
    void invalidiereJahr(@Param("mitarbeiterId") Long mitarbeiterId, @Param("jahr") int jahr);

    /**
     * Invalidiert ALLE Cache-Einträge für einen Mitarbeiter (z.B. bei Zeitkonto-Änderung).
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE MonatsSaldo ms SET ms.gueltig = false, ms.version = ms.version + 1 WHERE ms.festgeschrieben = false AND ms.mitarbeiter.id = :mitarbeiterId")
    void invalidiereAlle(@Param("mitarbeiterId") Long mitarbeiterId);

    interface OffenerMonat {
        Integer getJahr();
        Integer getMonat();
        Integer getAnzahl();
    }

    /** One set query, including months without cache; technical migration dates are never anchors. */
    @Query(value = """
            WITH RECURSIVE ereignisse AS (
                SELECT mitarbeiter_id, DATE(start_zeit) AS datum FROM zeitbuchung
                UNION SELECT mitarbeiter_id, datum FROM abwesenheit
                UNION SELECT mitarbeiter_id, datum FROM zeitkonto_korrektur
                UNION SELECT mitarbeiter_id, STR_TO_DATE(CONCAT(jahr, '-', monat, '-01'), '%Y-%c-%d') FROM monats_saldo
            ), anker AS (
                SELECT mitarbeiter_id, datum FROM ereignisse
                UNION SELECT id, eintrittsdatum FROM mitarbeiter WHERE eintrittsdatum IS NOT NULL
                UNION SELECT mitarbeiter_id, gueltig_von FROM zeitkonto_version WHERE gueltig_von > '1000-01-01'
            ), grenzen AS (
                SELECT mitarbeiter_id, MIN(datum) AS von FROM anker GROUP BY mitarbeiter_id
            ), letzte_daten AS (
                SELECT mitarbeiter_id, MAX(datum) AS bis FROM ereignisse GROUP BY mitarbeiter_id
            ), monate (tag) AS (
                SELECT CAST(DATE_FORMAT(MIN(von), '%Y-%m-01') AS DATE) FROM grenzen
                WHERE von < :aktuellerMonat
                UNION ALL
                SELECT DATE_ADD(tag, INTERVAL 1 MONTH) FROM monate
                WHERE DATE_ADD(tag, INTERVAL 1 MONTH) < :aktuellerMonat
            ), kandidaten AS (
                SELECT DISTINCT m.id, mo.tag
                FROM mitarbeiter m
                JOIN grenzen g ON g.mitarbeiter_id = m.id
                JOIN monate mo ON mo.tag >= CAST(DATE_FORMAT(g.von, '%Y-%m-01') AS DATE)
                LEFT JOIN letzte_daten ld ON ld.mitarbeiter_id = m.id
                WHERE m.art = 'MENSCH' AND mo.tag < :aktuellerMonat
                  AND (EXISTS (SELECT 1 FROM zeitkonto_version v
                      WHERE v.mitarbeiter_id = m.id AND v.gueltig_von <= LAST_DAY(mo.tag)
                        AND (v.gueltig_bis IS NULL OR v.gueltig_bis >= mo.tag)
                        AND (m.aktiv = TRUE OR mo.tag <= COALESCE(v.gueltig_bis, ld.bis)))
                    OR EXISTS (SELECT 1 FROM ereignisse e WHERE e.mitarbeiter_id = m.id
                        AND e.datum >= mo.tag AND e.datum < DATE_ADD(mo.tag, INTERVAL 1 MONTH)))
            )
            SELECT /*+ SET_VAR(cte_max_recursion_depth=120000) */ YEAR(k.tag) AS jahr,
                MONTH(k.tag) AS monat, COUNT(*) AS anzahl
            FROM kandidaten k
            LEFT JOIN monats_saldo ms ON ms.mitarbeiter_id = k.id
                AND ms.jahr = YEAR(k.tag) AND ms.monat = MONTH(k.tag)
            WHERE ms.id IS NULL OR ms.festgeschrieben = FALSE
            GROUP BY k.tag ORDER BY k.tag
            """, nativeQuery = true)
    List<OffenerMonat> findOffeneAbschlussMonate(@Param("aktuellerMonat") java.time.LocalDate aktuellerMonat);
}
