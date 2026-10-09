package org.example.kalkulationsprogramm.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.example.kalkulationsprogramm.domain.LieferantDokumentPosition;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.dto.PositionsSuchtreffer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LieferantDokumentPositionRepository extends JpaRepository<LieferantDokumentPosition, Long> {

    /**
     * Positionssuche: Jedes Suchwort muss im {@code suchtext} derselben Position
     * stehen (UND). Muster kommen fertig maskiert aus
     * {@link org.example.kalkulationsprogramm.domain.PositionsSuchtext#enthaeltMuster}
     * (Maskierzeichen {@code !}); nicht genutzte Wörter sind {@code null}. Neueste
     * Dokumente zuerst.
     *
     * @param lieferantId {@code null} = alle Lieferanten
     * @param typen       nur Dokumente dieser Typen (Rechte, Typfilter)
     * @param von         Belegdatum ab (inklusive); {@code null} = ohne Grenze
     * @param bis         Belegdatum bis (inklusive); {@code null} = ohne Grenze
     */
    @Query("SELECT new org.example.kalkulationsprogramm.dto.PositionsSuchtreffer("
            + "d.id, p.positionNr, p.bezeichnung, p.werkstoff, p.charge, p.abmessung, p.menge, p.mengeneinheit) "
            + "FROM LieferantDokumentPosition p "
            + "JOIN p.geschaeftsdokument gd "
            + "JOIN gd.dokument d "
            + "WHERE d.typ IN :typen "
            + "AND (:lieferantId IS NULL OR d.lieferant.id = :lieferantId) "
            + "AND (:von IS NULL OR gd.dokumentDatum >= :von) "
            + "AND (:bis IS NULL OR gd.dokumentDatum <= :bis) "
            + "AND p.suchtext LIKE :wort1 ESCAPE '!' "
            + "AND (:wort2 IS NULL OR p.suchtext LIKE :wort2 ESCAPE '!') "
            + "AND (:wort3 IS NULL OR p.suchtext LIKE :wort3 ESCAPE '!') "
            + "AND (:wort4 IS NULL OR p.suchtext LIKE :wort4 ESCAPE '!') "
            + "AND (:wort5 IS NULL OR p.suchtext LIKE :wort5 ESCAPE '!') "
            + "ORDER BY d.id DESC, p.positionNr")
    List<PositionsSuchtreffer> suche(@Param("typen") Collection<LieferantDokumentTyp> typen,
            @Param("lieferantId") Long lieferantId,
            @Param("von") LocalDate von, @Param("bis") LocalDate bis,
            @Param("wort1") String wort1, @Param("wort2") String wort2, @Param("wort3") String wort3,
            @Param("wort4") String wort4, @Param("wort5") String wort5,
            Pageable seite);

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
