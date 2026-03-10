package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Unveränderliches Audit-Protokoll für Zeitbuchungen.
 * Speichert Snapshots bei jeder Änderung für GoBD-Konformität.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "zeitbuchung_audit", uniqueConstraints = @UniqueConstraint(name = "uk_zeitbuchung_audit_version", columnNames = {
                "zeitbuchung_id", "version" }))
public class ZeitbuchungAudit {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        /** Referenz zur Haupt-Zeitbuchung */
        @Column(name = "zeitbuchung_id", nullable = false)
        private Long zeitbuchungId;

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

        @Column(name = "projekt_id")
        private Long projektId;

        @Column(name = "arbeitsgang_id")
        private Long arbeitsgangId;

        @Column(name = "arbeitsgang_stundensatz_id")
        private Long arbeitsgangStundensatzId;

        @Column(name = "projekt_produktkategorie_id")
        private Long projektProduktkategorieId;

        @Column(name = "start_zeit", nullable = false)
        private LocalDateTime startZeit;

        @Column(name = "ende_zeit")
        private LocalDateTime endeZeit;

        @Column(name = "anzahl_in_stunden", precision = 10, scale = 2)
        private BigDecimal anzahlInStunden;

        @Column(columnDefinition = "TEXT")
        private String notiz;

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
         * Erstellt einen Snapshot aus einer Zeitbuchung.
         */
        public static ZeitbuchungAudit fromZeitbuchung(Zeitbuchung buchung, AuditAktion aktion,
                        Mitarbeiter bearbeiter, ErfassungsQuelle quelle,
                        String grund) {
                ZeitbuchungAudit audit = new ZeitbuchungAudit();
                audit.setZeitbuchungId(buchung.getId());
                audit.setVersion(buchung.getVersion());
                audit.setAktion(aktion);

                // Snapshot der aktuellen Daten
                audit.setMitarbeiterId(buchung.getMitarbeiter().getId());
                // Projekt kann bei PAUSE-Buchungen null sein
                audit.setProjektId(buchung.getProjekt() != null ? buchung.getProjekt().getId() : null);
                audit.setArbeitsgangId(buchung.getArbeitsgang() != null ? buchung.getArbeitsgang().getId() : null);
                audit.setArbeitsgangStundensatzId(buchung.getArbeitsgangStundensatz() != null
                                ? buchung.getArbeitsgangStundensatz().getId()
                                : null);
                audit.setProjektProduktkategorieId(buchung.getProjektProduktkategorie() != null
                                ? buchung.getProjektProduktkategorie().getId()
                                : null);
                audit.setStartZeit(buchung.getStartZeit());
                audit.setEndeZeit(buchung.getEndeZeit());
                audit.setAnzahlInStunden(buchung.getAnzahlInStunden());
                audit.setNotiz(buchung.getNotiz());

                // Metadaten
                audit.setGeaendertVon(bearbeiter);
                audit.setGeaendertAm(LocalDateTime.now());
                audit.setGeaendertVia(quelle);
                audit.setAenderungsgrund(grund);

                return audit;
        }
}
