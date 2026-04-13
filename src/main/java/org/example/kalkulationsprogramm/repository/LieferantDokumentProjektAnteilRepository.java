package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface LieferantDokumentProjektAnteilRepository extends JpaRepository<LieferantDokumentProjektAnteil, Long> {

    /**
     * Findet alle Projekt-Anteile für ein bestimmtes Dokument.
     */
    List<LieferantDokumentProjektAnteil> findByDokumentId(Long dokumentId);

    /**
     * Findet alle Projekt-Anteile für ein bestimmtes Dokument mit eager-loaded Projekt/Kostenstelle/User.
     */
    @Query("SELECT pa FROM LieferantDokumentProjektAnteil pa " +
            "LEFT JOIN FETCH pa.projekt " +
            "LEFT JOIN FETCH pa.kostenstelle " +
            "LEFT JOIN FETCH pa.zugeordnetVon " +
            "WHERE pa.dokument.id = :dokumentId")
    List<LieferantDokumentProjektAnteil> findByDokumentIdEager(@Param("dokumentId") Long dokumentId);

    /**
     * Findet alle Dokument-Anteile für ein bestimmtes Projekt.
     */
    List<LieferantDokumentProjektAnteil> findByProjektId(Long projektId);

    /**
     * Findet alle Dokument-Anteile für ein Projekt mit eager-loaded Dokument + Geschäftsdaten + Lieferant.
     */
    @Query("SELECT pa FROM LieferantDokumentProjektAnteil pa " +
            "LEFT JOIN FETCH pa.dokument d " +
            "LEFT JOIN FETCH d.geschaeftsdaten " +
            "LEFT JOIN FETCH d.lieferant " +
            "LEFT JOIN FETCH d.attachment a " +
            "LEFT JOIN FETCH a.email " +
            "LEFT JOIN FETCH pa.zugeordnetVon " +
            "WHERE pa.projekt.id = :projektId")
    List<LieferantDokumentProjektAnteil> findByProjektIdEager(@Param("projektId") Long projektId);

    /**
     * Berechnet die Summe aller zugeordneten Beträge für ein Projekt.
     */
    @Query("SELECT COALESCE(SUM(pa.berechneterBetrag), 0) FROM LieferantDokumentProjektAnteil pa WHERE pa.projekt.id = :projektId")
    BigDecimal sumBerechneterBetragByProjektId(@Param("projektId") Long projektId);

    /**
     * Findet alle Anteile für ein Projekt, gefiltert nach Dokumenttyp.
     */
    @Query("SELECT pa FROM LieferantDokumentProjektAnteil pa " +
            "WHERE pa.projekt.id = :projektId AND pa.dokument.typ = :typ " +
            "ORDER BY pa.dokument.uploadDatum DESC")
    List<LieferantDokumentProjektAnteil> findByProjektIdAndDokumentTyp(
            @Param("projektId") Long projektId,
            @Param("typ") org.example.kalkulationsprogramm.domain.LieferantDokumentTyp typ);

    /**
     * Findet alle Anteile für eine bestimmte Kostenstelle.
     */
    List<LieferantDokumentProjektAnteil> findByKostenstelleId(Long kostenstelleId);
}
