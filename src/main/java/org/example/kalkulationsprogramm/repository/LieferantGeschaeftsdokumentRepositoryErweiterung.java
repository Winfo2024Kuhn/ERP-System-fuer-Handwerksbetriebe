package org.example.kalkulationsprogramm.repository;

/** Teil von {@link LieferantGeschaeftsdokumentRepository} mit eigenem (PostgreSQL-)SQL. */
public interface LieferantGeschaeftsdokumentRepositoryErweiterung {

    /**
     * Durchschnittliche Lieferzeit in Tagen fuer einen Lieferanten, basierend auf
     * Auftragsbestaetigungen (dokumentDatum -> liefertermin). {@code null}, wenn es
     * keine passenden Dokumente gibt.
     */
    Double calculateAverageLieferzeitByLieferantId(Long lieferantId);
}
