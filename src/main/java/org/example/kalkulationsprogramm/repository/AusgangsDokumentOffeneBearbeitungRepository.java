package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.AusgangsDokumentOffeneBearbeitung;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Wer ein Ausgangsdokument seit dem letzten Stand bearbeitet hat. */
@Repository
public interface AusgangsDokumentOffeneBearbeitungRepository
        extends JpaRepository<AusgangsDokumentOffeneBearbeitung, Long> {

    Optional<AusgangsDokumentOffeneBearbeitung> findByDokumentIdAndBenutzerId(Long dokumentId, Long benutzerId);

    List<AusgangsDokumentOffeneBearbeitung> findByDokumentIdOrderBySeitAscIdAsc(Long dokumentId);

    @Modifying
    @Query("DELETE FROM AusgangsDokumentOffeneBearbeitung b WHERE b.dokumentId = :dokumentId")
    int loescheFuerDokument(@Param("dokumentId") Long dokumentId);
}
