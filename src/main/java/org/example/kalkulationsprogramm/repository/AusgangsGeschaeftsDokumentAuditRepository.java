package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository für unveränderliche Audit-Einträge zu AusgangsGeschaeftsDokumenten.
 * Es gibt bewusst KEINE Lösch-Methoden — die Tabelle ist append-only (GoBD).
 */
@Repository
public interface AusgangsGeschaeftsDokumentAuditRepository
        extends JpaRepository<AusgangsGeschaeftsDokumentAudit, Long> {

    /** Vollständige Historie eines Dokuments (neueste zuerst). */
    List<AusgangsGeschaeftsDokumentAudit> findByDokumentIdOrderByGeaendertAmDesc(Long dokumentId);

    /** Historie über Dokumentnummer — funktioniert auch nach Hard-Delete. */
    List<AusgangsGeschaeftsDokumentAudit> findByDokumentNummerOrderByGeaendertAmDesc(String dokumentNummer);

    /** Audit-Einträge in einem Zeitraum (für Steuerprüfungs-Export). */
    List<AusgangsGeschaeftsDokumentAudit> findByGeaendertAmBetweenOrderByGeaendertAmAsc(
            LocalDateTime von, LocalDateTime bis);

    /** Komplette Kette in chronologischer Reihenfolge (für Verifikation und Backfill). */
    List<AusgangsGeschaeftsDokumentAudit> findAllByOrderByChainIndexAsc();

    /** Einträge ohne Chain-Daten (Backfill) — sortiert nach Erfassungszeit. */
    List<AusgangsGeschaeftsDokumentAudit> findByChainIndexIsNullOrderByGeaendertAmAscIdAsc();

    /** Einträge in einer Zeitspanne, sortiert nach Position in der Kette. */
    List<AusgangsGeschaeftsDokumentAudit> findByGeaendertAmBetweenOrderByChainIndexAsc(
            LocalDateTime von, LocalDateTime bis);

    /** Schnelle Zählung ohne Memory-Load (für UI-Vorschau). */
    long countByGeaendertAmBetween(LocalDateTime von, LocalDateTime bis);
}
