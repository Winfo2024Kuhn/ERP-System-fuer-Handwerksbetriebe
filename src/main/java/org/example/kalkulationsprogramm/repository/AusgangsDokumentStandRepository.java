package org.example.kalkulationsprogramm.repository;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.AusgangsDokumentStand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Festgehaltene Stände der Ausgangsdokumente. Nur lesen und anlegen, nie ändern oder löschen. */
@Repository
public interface AusgangsDokumentStandRepository extends JpaRepository<AusgangsDokumentStand, Long> {

    /**
     * Eine Seite der Stände eines Dokuments, neueste zuerst – ohne die großen Textspalten
     * (Positionen, HTML), die die Liste nicht braucht.
     */
    @Query(value = "SELECT new org.example.kalkulationsprogramm.repository.AusgangsDokumentStandZeile("
            + "s.id, s.nummer, s.art, s.festgehaltenAm, s.festgehaltenVonId, s.bearbeitetVonIds, s.betragNetto, "
            + "s.aenderungen, s.wiederhergestelltAusNummer, s.inhaltFingerabdruck, s.pdfDateiname) "
            + "FROM AusgangsDokumentStand s WHERE s.dokumentId = :dokumentId ORDER BY s.nummer DESC",
            countQuery = "SELECT COUNT(s) FROM AusgangsDokumentStand s WHERE s.dokumentId = :dokumentId")
    Page<AusgangsDokumentStandZeile> findeSeite(@Param("dokumentId") Long dokumentId, Pageable pageable);

    /** Der neueste Stand eines Dokuments (höchste Nummer). */
    Optional<AusgangsDokumentStand> findFirstByDokumentIdOrderByNummerDesc(Long dokumentId);

    Optional<AusgangsDokumentStand> findByIdAndDokumentId(Long id, Long dokumentId);
}
