package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Geschäftsmetadaten für Lieferanten-Dokumente.
 * Erweitert LieferantDokument um strukturierte Daten wie Dokumentnummer,
 * Beträge, Liefertermin etc.
 * Diese Daten werden primär durch KI-Analyse extrahiert.
 */
@Getter
@Setter
@Entity
@Table(name = "lieferant_geschaeftsdokument")
public class LieferantGeschaeftsdokument {

    @Id
    private Long id; // = LieferantDokument.id

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    private LieferantDokument dokument;

    // Basis-Metadaten
    @Column(length = 50)
    private String dokumentNummer; // z.B. RE-2024-001

    private LocalDate dokumentDatum;

    @Column(precision = 12, scale = 2)
    private BigDecimal betragNetto;

    @Column(precision = 12, scale = 2)
    private BigDecimal betragBrutto;

    @Column(precision = 5, scale = 4)
    private BigDecimal mwstSatz; // z.B. 0.19 für 19%

    // Lieferanten-spezifische Felder
    private LocalDate liefertermin;

    @Column(length = 50)
    private String bestellnummer; // Unsere Bestellnummer beim Lieferanten

    @Column(length = 50)
    private String referenzNummer; // Für Verknüpfung (z.B. AB-Nr auf Rechnung)

    // KI-Analyse Metadaten
    @Column(columnDefinition = "TEXT")
    private String aiRawJson; // Original JSON-Response der KI

    private Double aiConfidence; // Vertrauenswert 0.0 - 1.0

    private LocalDateTime analysiertAm;

    // Zahlungsstatus für Offene Posten
    private LocalDate zahlungsziel; // Fälligkeitsdatum

    @Column(nullable = false)
    private Boolean bezahlt = false; // true = bezahlt

    private LocalDate bezahltAm; // Wann wurde bezahlt

    private Boolean bereitsGezahlt = false; // Von KI erkannt (Vorauskasse etc.)

    // Skonto-Konditionen (z.B. "8 TAGE 2% 30 TAGE NETTO")
    private Integer skontoTage; // Tage ab Rechnungsdatum für Skonto

    @Column(precision = 5, scale = 2)
    private BigDecimal skontoProzent; // z.B. 2.00 für 2%

    private Integer nettoTage; // Zahlungsziel in Tagen (z.B. 30)

    // Tatsächlich gezahlter Betrag (nach Skonto-Abzug)
    @Column(precision = 12, scale = 2)
    private BigDecimal tatsaechlichGezahlt;

    private Boolean mitSkonto = false; // true wenn Skonto genutzt wurde

    // Lagerbestellung-Flag (Bestellung ohne Projekt-Zuordnung)
    @Column(nullable = false)
    private Boolean lagerbestellung = false; // true = Lagerbestellung, keine Projekt-Zuordnung nötig

    private Boolean verifiziert = false; // true wenn strukturiert aus ZUGFeRD/XML
    private String datenquelle; // ZUGFERD, XML, AI

    @Column(nullable = false)
    private Boolean genehmigt = false; // Genehmigung durch Abteilung 3 (Büro)

    /**
     * Flag für manuelle Prüfung erforderlich.
     * Wird gesetzt wenn die KI-Extraktion unvollständig war (Pflichtfelder fehlen).
     * Der User muss das Dokument manuell nachbearbeiten.
     */
    @Column(nullable = false)
    private Boolean manuellePruefungErforderlich = false;

    // Transient field to transport detected type from AI analysis to creation
    // service
    @Transient
    private LieferantDokumentTyp detectedTyp;
}
