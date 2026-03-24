package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Entity für Arbeitszeit-Buchungen.
 * 
 * Speichert echte Projektarbeit sowie Pausen.
 * Hinweis: projekt_id ist nullable - PAUSE-Buchungen haben kein Projekt,
 * ARBEIT-Buchungen benötigen ein Projekt (wird im Service validiert).
 */
@Getter
@Setter
@Entity
@Table(name = "zeitbuchung", uniqueConstraints = @UniqueConstraint(name = "uk_zeitbuchung_mitarbeiter_start", columnNames = {
        "mitarbeiter_id", "start_zeit" }))
public class Zeitbuchung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mitarbeiter_id", nullable = false)
    private Mitarbeiter mitarbeiter;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "projekt_id", nullable = true)
    private Projekt projekt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arbeitsgang_id")
    private Arbeitsgang arbeitsgang;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arbeitsgang_stundensatz_id")
    private ArbeitsgangStundensatz arbeitsgangStundensatz;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projekt_produktkategorie_id")
    private ProjektProduktkategorie projektProduktkategorie;

    @Column(nullable = false)
    private LocalDateTime startZeit;

    @Column
    private LocalDateTime endeZeit;

    @Column(precision = 10, scale = 2)
    private BigDecimal anzahlInStunden;

    @Column(length = 500)
    private String notiz;

    /**
     * Typ der Buchung: ARBEIT (normal) oder PAUSE (wird nicht zur Arbeitszeit
     * gezählt)
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BuchungsTyp typ = BuchungsTyp.ARBEIT;

    // ============== GoBD-konforme Audit-Felder ==============

    /** Versions-Counter für Änderungsverfolgung */
    @Column(nullable = false)
    private Integer version = 1;

    /** Wer hat die Buchung ursprünglich erfasst */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "erfasst_von_mitarbeiter_id")
    private Mitarbeiter erfasstVon;

    /** Wann wurde die Buchung erfasst */
    @Column
    private LocalDateTime erfasstAm;

    /** Über welchen Kanal wurde erfasst (MOBILE_APP, DESKTOP, etc.) */
    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private ErfassungsQuelle erfasstVia;

    /** Wer hat zuletzt geändert */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zuletzt_geaendert_von")
    private Mitarbeiter zuletztGeaendertVon;

    /** Wann wurde zuletzt geändert */
    @Column
    private LocalDateTime zuletztGeaendertAm;

    /** Idempotency-Key für Offline-Sync (UUID vom Client, verhindert Duplikate) */
    @Column(length = 36, unique = true)
    private String idempotencyKey;

    /** Idempotency-Key für Stop-Operationen auf bestehende Buchungen */
    @Column(length = 36, unique = true)
    private String stopIdempotencyKey;

    /**
     * Erhöht die Version und setzt Änderungs-Metadaten.
     */
    public void markiereAlsGeaendert(Mitarbeiter bearbeiter) {
        if (this.version == null) {
            this.version = 2;
        } else {
            this.version = this.version + 1;
        }
        this.zuletztGeaendertVon = bearbeiter;
        this.zuletztGeaendertAm = LocalDateTime.now();
    }
}
