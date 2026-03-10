package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity für manuelle Zeitkonto-Korrekturen.
 * Ermöglicht Ausgleichsbuchungen ohne Projektzuordnung.
 * 
 * Positive Stunden = Gutschrift (z.B. Überstundenausgleich)
 * Negative Stunden = Abzug (z.B. Korrektur bei Fehleingabe)
 * 
 * GoBD-konform mit vollständiger Audit-Protokollierung.
 */
@Getter
@Setter
@Entity
@Table(name = "zeitkonto_korrektur")
public class ZeitkontoKorrektur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mitarbeiter_id", nullable = false)
    private Mitarbeiter mitarbeiter;

    @Column(nullable = false)
    private LocalDate datum;

    /**
     * Korrekturstunden.
     * Positiv = Gutschrift (Stunden werden dem Konto gutgeschrieben)
     * Negativ = Abzug (Stunden werden vom Konto abgezogen)
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal stunden;

    /**
     * Begründung für die Korrektur (Pflichtfeld für GoBD).
     * z.B. "Überstundenausgleich Q4", "Korrektur Fehleingabe vom 15.12."
     */
    @Column(length = 500, nullable = false)
    private String grund;

    /** GoBD: Versionsnummer für Änderungsnachverfolgung */
    @Column(nullable = false)
    private Integer version = 1;

    @Column(nullable = false)
    private LocalDateTime erstelltAm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "erstellt_von_id")
    private Mitarbeiter erstelltVon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KorrekturTyp typ = KorrekturTyp.STUNDEN;

    /** GoBD: Storniert-Kennzeichen (statt physischer Löschung) */
    @Column(nullable = false)
    private Boolean storniert = false;

    /** GoBD: Zeitpunkt der Stornierung */
    @Column
    private LocalDateTime storniertAm;

    /** GoBD: Wer hat storniert */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "storniert_von_id")
    private Mitarbeiter storniertVon;

    /** GoBD: Begründung für Stornierung */
    @Column(length = 500)
    private String stornierungsgrund;

    @PrePersist
    protected void onCreate() {
        if (erstelltAm == null) {
            erstelltAm = LocalDateTime.now();
        }
        if (version == null) {
            version = 1;
        }
    }

    /**
     * Erhöht die Version für Audit-Trail.
     */
    public void erhoeheVersion() {
        this.version = (this.version == null ? 1 : this.version) + 1;
    }
}
