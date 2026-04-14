package org.example.kalkulationsprogramm.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Individualisierte WPS-Zuweisung für ein Projekt.
 * Ordnet einer Schweißanweisung (WPS) einen konkreten Schweißer,
 * Schweißprüfer und Einsatzdatum pro Projekt zu.
 */
@Getter
@Setter
@Entity
@Table(name = "wps_projekt_zuweisung")
public class WpsProjektZuweisung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wps_id", nullable = false)
    private Wps wps;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projekt_id", nullable = false)
    private Projekt projekt;

    /** Mitarbeiter der diese WPS ausführt (Schweißer) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schweisser_id")
    private Mitarbeiter schweisser;

    /** Name des verantwortlichen Schweißprüfers */
    @Column(length = 200)
    private String schweisspruefer;

    /** Geplantes oder tatsächliches Einsatzdatum */
    private LocalDate einsatzDatum;

    @Column(columnDefinition = "TEXT")
    private String bemerkung;

    @Column(nullable = false)
    private LocalDateTime erstelltAm = LocalDateTime.now();
}
