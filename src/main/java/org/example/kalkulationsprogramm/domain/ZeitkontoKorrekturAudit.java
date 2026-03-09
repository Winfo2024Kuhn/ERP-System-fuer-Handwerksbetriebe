package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Unveränderliches Audit-Protokoll für Zeitkonto-Korrekturen.
 * Speichert Snapshots bei jeder Änderung für GoBD-Konformität.
 * 
 * Korrekturen sind buchhalterisch relevant und müssen nachvollziehbar sein.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "zeitkonto_korrektur_audit", uniqueConstraints = @UniqueConstraint(name = "uk_zeitkonto_korrektur_audit_version", columnNames = {
        "zeitkonto_korrektur_id", "version" }))
public class ZeitkontoKorrekturAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Referenz zur Haupt-Korrektur */
    @Column(name = "zeitkonto_korrektur_id", nullable = false)
    private Long zeitkontoKorrekturId;

    /** Version dieses Snapshots (1 = initial, 2 = erste Änderung, ...) */
    @Column(nullable = false)
    private Integer version;

    /** Art der Änderung */
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private AuditAktion aktion;

    // ============== Snapshot der Daten zum Zeitpunkt der Änderung ==============

    @Column(name = "mitarbeiter_id", nullable = false)
    private Long mitarbeiterId;

    @Column(name = "datum", nullable = false)
    private LocalDate datum;

    @Column(name = "stunden", nullable = false, precision = 10, scale = 2)
    private BigDecimal stunden;

    @Column(name = "grund", columnDefinition = "TEXT")
    private String grund;

    // ============== Änderungs-Metadaten ==============

    /** Wer hat die Änderung durchgeführt */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "geaendert_von_mitarbeiter_id", nullable = false)
    private Mitarbeiter geaendertVon;

    /** Wann wurde die Änderung durchgeführt */
    @Column(name = "geaendert_am", nullable = false)
    private LocalDateTime geaendertAm;

    /** Über welchen Kanal wurde geändert */
    @Enumerated(EnumType.STRING)
    @Column(name = "geaendert_via", length = 50, nullable = false)
    private ErfassungsQuelle geaendertVia;

    /** Begründung für die Änderung (Pflicht bei GEAENDERT/STORNIERT) */
    @Column(name = "aenderungsgrund", columnDefinition = "TEXT")
    private String aenderungsgrund;

    /**
     * Erstellt einen Snapshot aus einer Zeitkonto-Korrektur.
     */
    public static ZeitkontoKorrekturAudit fromKorrektur(ZeitkontoKorrektur korrektur, AuditAktion aktion,
            Mitarbeiter bearbeiter, ErfassungsQuelle quelle, String aenderungsgrund) {
        ZeitkontoKorrekturAudit audit = new ZeitkontoKorrekturAudit();
        audit.setZeitkontoKorrekturId(korrektur.getId());
        audit.setVersion(korrektur.getVersion());
        audit.setAktion(aktion);

        // Snapshot der aktuellen Daten
        audit.setMitarbeiterId(korrektur.getMitarbeiter().getId());
        audit.setDatum(korrektur.getDatum());
        audit.setStunden(korrektur.getStunden());
        audit.setGrund(korrektur.getGrund());

        // Metadaten
        audit.setGeaendertVon(bearbeiter);
        audit.setGeaendertAm(LocalDateTime.now());
        audit.setGeaendertVia(quelle);
        audit.setAenderungsgrund(aenderungsgrund);

        return audit;
    }
}
