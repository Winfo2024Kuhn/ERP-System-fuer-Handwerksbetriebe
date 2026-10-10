package org.example.kalkulationsprogramm.repository;

/** Datenbankabhaengiger Teil von {@link LieferantGeschaeftsdokumentRepository} (MySQL/PostgreSQL). */
public interface LieferantGeschaeftsdokumentRepositoryErweiterung {

    /**
     * Durchschnittliche Lieferzeit in Tagen fuer einen Lieferanten, basierend auf
     * Auftragsbestaetigungen (dokumentDatum -> liefertermin). {@code null}, wenn es
     * keine passenden Dokumente gibt.
     */
    Double calculateAverageLieferzeitByLieferantId(Long lieferantId);
}
