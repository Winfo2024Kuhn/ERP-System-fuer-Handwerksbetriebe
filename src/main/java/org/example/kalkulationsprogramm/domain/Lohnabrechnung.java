package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lohnabrechnung eines Mitarbeiters.
 * Wird automatisch aus Steuerberater-E-Mails importiert.
 */
@Entity
@Table(name = "lohnabrechnung", indexes = {
    @Index(name = "idx_lohnabrechnung_mitarbeiter", columnList = "mitarbeiter_id"),
    @Index(name = "idx_lohnabrechnung_periode", columnList = "jahr, monat"),
    @Index(name = "idx_lohnabrechnung_steuerberater", columnList = "steuerberater_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Lohnabrechnung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Mitarbeiter, dem diese Lohnabrechnung gehört.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mitarbeiter_id", nullable = false)
    private Mitarbeiter mitarbeiter;

    /**
     * Steuerberater, von dem die Lohnabrechnung stammt.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "steuerberater_id")
    private SteuerberaterKontakt steuerberater;

    /**
     * Jahr der Lohnabrechnung.
     */
    @Column(nullable = false)
    private Integer jahr;

    /**
     * Monat der Lohnabrechnung (1-12).
     */
    @Column(nullable = false)
    private Integer monat;

    /**
     * Original-Dateiname der PDF.
     */
    @Column(length = 255)
    private String originalDateiname;

    /**
     * Gespeicherter Dateiname auf dem Server.
     */
    @Column(length = 255, nullable = false)
    private String gespeicherterDateiname;

    /**
     * Bruttolohn (aus KI-Extraktion, optional).
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal bruttolohn;

    /**
     * Nettolohn (aus KI-Extraktion, optional).
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal nettolohn;

    /**
     * Zeitpunkt des Imports.
     */
    @Column(nullable = false)
    private LocalDateTime importDatum;

    /**
     * Quellemail, aus der die Lohnabrechnung importiert wurde.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_id")
    private Email sourceEmail;

    /**
     * Status der KI-Analyse.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LohnabrechnungStatus status = LohnabrechnungStatus.IMPORTIERT;

    /**
     * Rohe JSON-Antwort der KI-Analyse (für Debugging).
     */
    @Column(columnDefinition = "TEXT")
    private String aiRawJson;

    @PrePersist
    protected void onCreate() {
        if (importDatum == null) {
            importDatum = LocalDateTime.now();
        }
    }
}
