package org.example.kalkulationsprogramm.repository;

import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.config.DatenbankArt;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class MonatsSaldoRepositoryErweiterungImpl implements MonatsSaldoRepositoryErweiterung {

    /** One set query, including months without cache; technical migration dates are never anchors. */
    static final String OFFENE_MONATE_MYSQL = """
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
                  AND m.fuehrt_zeitkonto = TRUE AND (m.ist_geschaeftsfuehrer IS NULL OR m.ist_geschaeftsfuehrer = FALSE)
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
            """;

    /** Dieselbe Abfrage fuer PostgreSQL (date_trunc/make_date/INTERVAL statt DATE_FORMAT/STR_TO_DATE/DATE_ADD). */
    static final String OFFENE_MONATE_POSTGRES = """
            WITH RECURSIVE ereignisse AS (
                SELECT mitarbeiter_id, CAST(start_zeit AS DATE) AS datum FROM zeitbuchung
                UNION SELECT mitarbeiter_id, datum FROM abwesenheit
                UNION SELECT mitarbeiter_id, datum FROM zeitkonto_korrektur
                UNION SELECT mitarbeiter_id, make_date(jahr, monat, 1) FROM monats_saldo
            ), anker AS (
                SELECT mitarbeiter_id, datum FROM ereignisse
                UNION SELECT id, eintrittsdatum FROM mitarbeiter WHERE eintrittsdatum IS NOT NULL
                UNION SELECT mitarbeiter_id, gueltig_von FROM zeitkonto_version WHERE gueltig_von > DATE '1000-01-01'
            ), grenzen AS (
                SELECT mitarbeiter_id, MIN(datum) AS von FROM anker GROUP BY mitarbeiter_id
            ), letzte_daten AS (
                SELECT mitarbeiter_id, MAX(datum) AS bis FROM ereignisse GROUP BY mitarbeiter_id
            ), monate (tag) AS (
                SELECT CAST(date_trunc('month', MIN(von)) AS DATE) FROM grenzen
                WHERE von < :aktuellerMonat
                UNION ALL
                SELECT CAST(tag + INTERVAL '1 month' AS DATE) FROM monate
                WHERE tag + INTERVAL '1 month' < :aktuellerMonat
            ), kandidaten AS (
                SELECT DISTINCT m.id, mo.tag
                FROM mitarbeiter m
                JOIN grenzen g ON g.mitarbeiter_id = m.id
                JOIN monate mo ON mo.tag >= CAST(date_trunc('month', g.von) AS DATE)
                LEFT JOIN letzte_daten ld ON ld.mitarbeiter_id = m.id
                WHERE m.art = 'MENSCH' AND mo.tag < :aktuellerMonat
                  AND m.fuehrt_zeitkonto = TRUE AND (m.ist_geschaeftsfuehrer IS NULL OR m.ist_geschaeftsfuehrer = FALSE)
                  AND (EXISTS (SELECT 1 FROM zeitkonto_version v
                      WHERE v.mitarbeiter_id = m.id AND v.gueltig_von <= CAST(mo.tag + INTERVAL '1 month' - INTERVAL '1 day' AS DATE)
                        AND (v.gueltig_bis IS NULL OR v.gueltig_bis >= mo.tag)
                        AND (m.aktiv = TRUE OR mo.tag <= COALESCE(v.gueltig_bis, ld.bis)))
                    OR EXISTS (SELECT 1 FROM ereignisse e WHERE e.mitarbeiter_id = m.id
                        AND e.datum >= mo.tag AND e.datum < mo.tag + INTERVAL '1 month'))
            )
            SELECT CAST(EXTRACT(YEAR FROM k.tag) AS INTEGER) AS jahr,
                CAST(EXTRACT(MONTH FROM k.tag) AS INTEGER) AS monat, COUNT(*) AS anzahl
            FROM kandidaten k
            LEFT JOIN monats_saldo ms ON ms.mitarbeiter_id = k.id
                AND ms.jahr = CAST(EXTRACT(YEAR FROM k.tag) AS INTEGER) AND ms.monat = CAST(EXTRACT(MONTH FROM k.tag) AS INTEGER)
            WHERE ms.id IS NULL OR ms.festgeschrieben = FALSE
            GROUP BY k.tag ORDER BY k.tag
            """;

    private final EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public List<MonatsSaldoRepository.OffenerMonat> findOffeneAbschlussMonate(LocalDate aktuellerMonat) {
        List<Object[]> zeilen = entityManager
                .createNativeQuery(DatenbankArt.istPostgres(entityManager) ? OFFENE_MONATE_POSTGRES : OFFENE_MONATE_MYSQL)
                .setParameter("aktuellerMonat", aktuellerMonat)
                .getResultList();
        return zeilen.stream()
                .<MonatsSaldoRepository.OffenerMonat>map(zeile -> new OffenerMonatZeile(
                        ((Number) zeile[0]).intValue(), ((Number) zeile[1]).intValue(), ((Number) zeile[2]).intValue()))
                .toList();
    }

    record OffenerMonatZeile(Integer jahr, Integer monat, Integer anzahl) implements MonatsSaldoRepository.OffenerMonat {
        @Override
        public Integer getJahr() {
            return jahr;
        }

        @Override
        public Integer getMonat() {
            return monat;
        }

        @Override
        public Integer getAnzahl() {
            return anzahl;
        }
    }
}
