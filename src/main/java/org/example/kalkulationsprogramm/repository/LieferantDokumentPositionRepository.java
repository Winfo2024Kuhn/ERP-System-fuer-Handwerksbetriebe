package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.LieferantDokumentPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LieferantDokumentPositionRepository extends JpaRepository<LieferantDokumentPosition, Long> {

    /** Positionen eines Dokuments in Belegreihenfolge, mit Projekt und Kostenstelle. */
    @Query("SELECT p FROM LieferantDokumentPosition p "
            + "LEFT JOIN FETCH p.projekt "
            + "LEFT JOIN FETCH p.kostenstelle "
            + "WHERE p.geschaeftsdokument.id = :geschaeftsdokumentId "
            + "ORDER BY p.positionNr")
    List<LieferantDokumentPosition> findByGeschaeftsdokumentId(
            @Param("geschaeftsdokumentId") Long geschaeftsdokumentId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM LieferantDokumentPosition p WHERE p.geschaeftsdokument.id = :geschaeftsdokumentId")
    int deleteByGeschaeftsdokumentId(@Param("geschaeftsdokumentId") Long geschaeftsdokumentId);

    /** Löscht die Positionsaufteilung (z. B. wenn wieder nach Prozent/Betrag aufgeteilt wird). */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE LieferantDokumentPosition p SET p.projekt = null, p.kostenstelle = null "
            + "WHERE p.geschaeftsdokument.id = :geschaeftsdokumentId")
    int entferneZuordnungen(@Param("geschaeftsdokumentId") Long geschaeftsdokumentId);
}
