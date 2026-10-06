package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.LieferantDokumentVerknuepfungSperre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LieferantDokumentVerknuepfungSperreRepository extends
        JpaRepository<LieferantDokumentVerknuepfungSperre, LieferantDokumentVerknuepfungSperre.Schluessel> {

    /**
     * Sperren, deren Nachfolger-Dokument zum Lieferanten gehört. Der Abgleich
     * vergleicht nur Dokumente desselben Lieferanten – mehr braucht er nicht.
     */
    @Query("SELECT s FROM LieferantDokumentVerknuepfungSperre s, LieferantDokument d "
            + "WHERE d.id = s.dokumentId AND d.lieferant.id = :lieferantId")
    List<LieferantDokumentVerknuepfungSperre> findByLieferantId(@Param("lieferantId") Long lieferantId);

    /** Hebt die Sperre eines Paares auf, egal in welcher Richtung sie gespeichert ist. */
    @Modifying
    @Query("DELETE FROM LieferantDokumentVerknuepfungSperre s "
            + "WHERE (s.dokumentId = :a AND s.verknuepftId = :b) OR (s.dokumentId = :b AND s.verknuepftId = :a)")
    int loeschePaar(@Param("a") Long a, @Param("b") Long b);
}
