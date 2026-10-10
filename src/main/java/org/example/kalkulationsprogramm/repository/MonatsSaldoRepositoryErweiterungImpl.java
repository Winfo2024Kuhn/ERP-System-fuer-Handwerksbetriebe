package org.example.kalkulationsprogramm.repository;

import java.time.LocalDate;
import java.util.List;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class MonatsSaldoRepositoryErweiterungImpl implements MonatsSaldoRepositoryErweiterung {

    /**
     * One set query, including months without cache; technical migration dates are never anchors.
     * {@code :abMonat} lifts every employee's first month to the start of time tracking.
     */
    private static final String OFFENE_MONATE = """
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
                SELECT mitarbeiter_id, CAST(GREATEST(MIN(datum), :abMonat) AS DATE) AS von FROM anker WHERE datum IS NOT NULL GROUP BY mitarbeiter_id
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

    /** Below every real anchor and still a valid DATE. */
    private static final LocalDate OHNE_GRENZE = LocalDate.of(1000, 1, 1);

    private final EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public List<MonatsSaldoRepository.OffenerMonat> findOffeneAbschlussMonate(LocalDate aktuellerMonat, LocalDate abMonat) {
        List<Object[]> zeilen = entityManager
                .createNativeQuery(OFFENE_MONATE)
                .setParameter("aktuellerMonat", aktuellerMonat)
                // A real date instead of NULL: PostgreSQL cannot infer the type of a bare NULL inside GREATEST.
                .setParameter("abMonat", abMonat != null ? abMonat.withDayOfMonth(1) : OHNE_GRENZE)
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
