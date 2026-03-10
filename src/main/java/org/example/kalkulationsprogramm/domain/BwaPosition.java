package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Einzelne Position aus einer BWA (z.B. Löhne, Sozialaufwendungen).
 * Wird durch KI-Analyse extrahiert und kann manuell korrigiert werden.
 */
@Getter
@Setter
@Entity
@Table(name = "bwa_position")
public class BwaPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bwa_upload_id", nullable = false)
    private BwaUpload bwaUpload;

    /**
     * SKR 03/04 Kontonummer (z.B. "4100", "4130").
     */
    @Column(length = 20)
    private String kontonummer;

    /**
     * Bezeichnung laut BWA (z.B. "Löhne und Gehälter").
     */
    @Column(nullable = false)
    private String bezeichnung;

    /**
     * Betrag für den aktuellen Monat.
     */
    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal betragMonat;

    /**
     * Kumulierter Jahresbetrag.
     */
    @Column(precision = 14, scale = 2)
    private BigDecimal betragKumuliert;

    /**
     * Kategorie für Gruppierung (PERSONALKOSTEN, RAUMKOSTEN, etc.).
     */
    @Column(length = 50)
    private String kategorie;

    /**
     * Automatisch zugeordnete Kostenstelle basierend auf Kontonummer.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kostenstelle_id")
    private Kostenstelle kostenstelle;

    /**
     * Ob diese Position bereits als Rechnung im System gefunden wurde.
     * Bei true: Betrag kommt aus LieferantGeschaeftsdokument.
     * Bei false: Betrag wird aus BWA importiert.
     */
    @Column(nullable = false)
    private Boolean inRechnungenGefunden = false;

    /**
     * Summe der gefundenen Rechnungen für diese Kategorie.
     */
    @Column(precision = 14, scale = 2)
    private BigDecimal rechnungssumme;

    /**
     * Differenz zwischen BWA-Betrag und Rechnungssumme.
     * Positiv = mehr in BWA als Rechnungen (normal für Lohnkosten etc.)
     */
    @Column(precision = 14, scale = 2)
    private BigDecimal differenz;

    /**
     * Ob User diese Position manuell korrigiert hat.
     */
    @Column(nullable = false)
    private Boolean manuellKorrigiert = false;

    /**
     * Notiz vom User bei manueller Korrektur.
     */
    @Column(length = 500)
    private String notiz;
}
