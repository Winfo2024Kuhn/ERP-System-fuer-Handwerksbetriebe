package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * BWA-Dokument (Betriebswirtschaftliche Auswertung) vom Steuerberater.
 * Enthält monatliche oder jährliche Auswertungen die durch KI analysiert werden.
 * Nach manueller Prüfung und Freigabe fließen die Daten in die Gemeinkostenberechnung.
 */
@Getter
@Setter
@Entity
@Table(name = "bwa_upload")
public class BwaUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BwaTyp typ;

    @Column(nullable = false)
    private Integer jahr;

    /**
     * Monat (1-12), null bei JAEHRLICH.
     */
    private Integer monat;

    private String originalDateiname;
    private String gespeicherterDateiname;

    @Column(nullable = false)
    private LocalDateTime uploadDatum;

    private LocalDateTime analyseDatum;

    /**
     * Original JSON-Response der Gemini KI-Analyse.
     */
    @Column(columnDefinition = "TEXT")
    private String aiRawJson;

    /**
     * Vertrauenswert der KI-Analyse (0.0 - 1.0).
     */
    private Double aiConfidence;

    /**
     * Status: wurde bereits durch KI analysiert.
     */
    @Column(nullable = false)
    private Boolean analysiert = false;

    /**
     * Status: wurde vom User geprüft und freigegeben.
     * Erst nach Freigabe fließen Daten in die Berechnung.
     */
    @Column(nullable = false)
    private Boolean freigegeben = false;

    private LocalDateTime freigegebenAm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freigegeben_von_id")
    private Mitarbeiter freigegebenVon;

    /**
     * Summe Gemeinkosten laut BWA.
     */
    @Column(precision = 14, scale = 2)
    private BigDecimal gesamtGemeinkosten;

    /**
     * Summe der Kosten die bereits als Rechnung im System sind.
     */
    @Column(precision = 14, scale = 2)
    private BigDecimal kostenAusRechnungen;

    /**
     * Summe der Kosten die aus BWA importiert werden (nicht in Rechnungen).
     */
    @Column(precision = 14, scale = 2)
    private BigDecimal kostenAusBwa;

    /**
     * Referenz zum Steuerberater von dem die BWA stammt.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "steuerberater_id")
    private SteuerberaterKontakt steuerberater;

    /**
     * Quellemail, aus der die BWA importiert wurde.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_id")
    private Email sourceEmail;

    @OneToMany(mappedBy = "bwaUpload", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BwaPosition> positionen = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (uploadDatum == null) {
            uploadDatum = LocalDateTime.now();
        }
    }
}
