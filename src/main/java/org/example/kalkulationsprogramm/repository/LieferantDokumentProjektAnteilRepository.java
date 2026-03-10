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
     * Findet alle Dokument-Anteile für ein bestimmtes Projekt.
     */
    List<LieferantDokumentProjektAnteil> findByProjektId(Long projektId);

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
