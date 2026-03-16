package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LieferantDokumentRepository extends JpaRepository<LieferantDokument, Long> {

        List<LieferantDokument> findByLieferantIdOrderByUploadDatumDesc(Long lieferantId);

        List<LieferantDokument> findByLieferantIdAndTypOrderByUploadDatumDesc(Long lieferantId,
                        LieferantDokumentTyp typ);

        @Query("SELECT d FROM LieferantDokument d WHERE d.lieferant.id = :lieferantId AND d.typ IN :typen ORDER BY d.uploadDatum DESC")
        List<LieferantDokument> findByLieferantIdAndTypIn(@Param("lieferantId") Long lieferantId,
                        @Param("typen") List<LieferantDokumentTyp> typen);

        /**
         * Findet alle Dokumente die einem Projekt zugeordnet sind (über
         * projektAnteile).
         */
        @Query("SELECT DISTINCT d FROM LieferantDokument d JOIN d.projektAnteile pa WHERE pa.projekt.id = :projektId ORDER BY d.uploadDatum DESC")
        List<LieferantDokument> findByProjektId(@Param("projektId") Long projektId);

        /**
         * Findet alle Dokumente eines bestimmten Typs die einem Projekt zugeordnet
         * sind.
         */
        @Query("SELECT DISTINCT d FROM LieferantDokument d JOIN d.projektAnteile pa WHERE pa.projekt.id = :projektId AND d.typ = :typ ORDER BY d.uploadDatum DESC")
        List<LieferantDokument> findByProjektIdAndTyp(@Param("projektId") Long projektId,
                        @Param("typ") LieferantDokumentTyp typ);

        /**
         * Prüft ob ein Dokument mit diesem Dateinamen bereits für diesen Lieferanten
         * existiert.
         * Wird für Vendor Invoice Import verwendet um Duplikate zu vermeiden.
         */
        boolean existsByLieferantIdAndOriginalDateinameContaining(Long lieferantId, String filenameFragment);

        @Query("SELECT d FROM LieferantDokument d LEFT JOIN d.geschaeftsdaten g " +
                        "WHERE d.lieferant.id = :lieferantId " +
                        "AND d.typ = 'LIEFERSCHEIN' " +
                        "AND (LOWER(g.dokumentNummer) LIKE LOWER(CONCAT('%', :query, '%')) " +
                        "OR LOWER(d.originalDateiname) LIKE LOWER(CONCAT('%', :query, '%')))")
        List<LieferantDokument> searchLieferscheine(@Param("lieferantId") Long lieferantId,
                        @Param("query") String query);

        /**
         * Findet alle Lieferscheine die nach einem bestimmten Zeitpunkt hochgeladen
         * wurden (für Benachrichtigungen auf dem PC-Desktop).
         */
        @Query("SELECT d FROM LieferantDokument d WHERE d.typ = org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.LIEFERSCHEIN AND d.uploadDatum >= :since ORDER BY d.uploadDatum DESC")
        List<LieferantDokument> findRecentLieferscheine(@Param("since") java.time.LocalDateTime since);
}
