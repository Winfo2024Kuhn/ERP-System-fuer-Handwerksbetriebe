package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.config.DatenbankArt;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class LieferantGeschaeftsdokumentRepositoryErweiterungImpl implements LieferantGeschaeftsdokumentRepositoryErweiterung {

    private static final String BEDINGUNG = "FROM lieferant_geschaeftsdokument gd "
            + "JOIN lieferant_dokument d ON gd.id = d.id "
            + "WHERE d.lieferant_id = :lieferantId "
            + "AND d.typ = 'AUFTRAGSBESTAETIGUNG' "
            + "AND gd.dokument_datum IS NOT NULL "
            + "AND gd.liefertermin IS NOT NULL";
    /** DATEDIFF gibt es nur in MySQL ... */
    static final String DURCHSCHNITT_MYSQL = "SELECT AVG(DATEDIFF(gd.liefertermin, gd.dokument_datum)) " + BEDINGUNG;
    /** ... in PostgreSQL ergibt Datum minus Datum direkt die Tage. */
    static final String DURCHSCHNITT_POSTGRES = "SELECT AVG(gd.liefertermin - gd.dokument_datum) " + BEDINGUNG;

    private final EntityManager entityManager;

    @Override
    public Double calculateAverageLieferzeitByLieferantId(Long lieferantId) {
        Object ergebnis = entityManager
                .createNativeQuery(DatenbankArt.istPostgres(entityManager) ? DURCHSCHNITT_POSTGRES : DURCHSCHNITT_MYSQL)
                .setParameter("lieferantId", lieferantId)
                .getSingleResult();
        return ergebnis == null ? null : ((Number) ergebnis).doubleValue();
    }
}
