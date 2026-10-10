package org.example.kalkulationsprogramm.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class LieferantGeschaeftsdokumentRepositoryErweiterungImpl implements LieferantGeschaeftsdokumentRepositoryErweiterung {

    /** Datum minus Datum ergibt in PostgreSQL direkt die Tage. */
    private static final String DURCHSCHNITT = "SELECT AVG(gd.liefertermin - gd.dokument_datum) "
            + "FROM lieferant_geschaeftsdokument gd "
            + "JOIN lieferant_dokument d ON gd.id = d.id "
            + "WHERE d.lieferant_id = :lieferantId "
            + "AND d.typ = 'AUFTRAGSBESTAETIGUNG' "
            + "AND gd.dokument_datum IS NOT NULL "
            + "AND gd.liefertermin IS NOT NULL";

    private final EntityManager entityManager;

    @Override
    public Double calculateAverageLieferzeitByLieferantId(Long lieferantId) {
        Object ergebnis = entityManager
                .createNativeQuery(DURCHSCHNITT)
                .setParameter("lieferantId", lieferantId)
                .getSingleResult();
        return ergebnis == null ? null : ((Number) ergebnis).doubleValue();
    }
}
