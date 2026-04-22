package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class ArtikelInProjekt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projekt_id")
    private Projekt projekt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "artikel_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Artikel artikel;

    @Column
    private Integer stueckzahl;

    @Column(precision = 19, scale = 2)
    private BigDecimal meter;

    @Column(precision = 19, scale = 2)
    private BigDecimal kilogramm;

    @Column(precision = 19, scale = 2)
    private BigDecimal preisProStueck;

    @Column(nullable = false)
    private LocalDate hinzugefuegtAm;

    @Column(name = "anschnitt_winkel_links", columnDefinition = "DECIMAL(5,2)")
    private Double anschnittWinkelLinks;

    @Column(name = "anschnitt_winkel_rechts", columnDefinition = "DECIMAL(5,2)")
    private Double anschnittWinkelRechts;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schnittbild_id")
    private Schnittbilder schnittbild;

    private String kommentar;

    // EN 1090: manuell gewähltes oder vorgeschlagenes Zeugnis pro Position
    @Enumerated(EnumType.STRING)
    @Column(name = "zeugnis_anforderung", columnDefinition = "varchar(30)")
    private ZeugnisTyp zeugnisAnforderung;

    // Freitext-Felder für manuelle Positionen (ohne Stammartikel)
    @Column(name = "freitext_produktname", length = 500)
    private String freitextProduktname;

    @Column(name = "freitext_produkttext", columnDefinition = "TEXT")
    private String freitextProdukttext;

    @Column(name = "freitext_einheit", length = 50)
    private String freitextEinheit;

    @Column(name = "freitext_menge", precision = 19, scale = 3)
    private java.math.BigDecimal freitextMenge;

    // Fixmaß pro Stück in Millimetern (z. B. für Träger auf Länge bestellt: 6000 mm × 3 Stück)
    @Column(name = "fixmass_mm")
    private Integer fixmassMm;

    // Warengruppe direkt (für freie Positionen ohne Stammartikel)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kategorie_id")
    private Kategorie kategorie;

    /**
     * Workflow-Zustand der Bedarfszeile. Treibt die Bedarf-Ansicht
     * (offen vs. aus Lager abgeharkt vs. in Preisanfrage vs. bestellt).
     * Der Bestellvorgang selbst lebt auf {@link Bestellung}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "quelle", columnDefinition = "varchar(30)", nullable = false)
    private BestellQuelle quelle = BestellQuelle.OFFEN;

    /** Zeitpunkt des Lager-Haken durch den Chef (nur gesetzt bei AUS_LAGER). */
    @Column(name = "lager_abgleich_am")
    private LocalDateTime lagerAbgleichAm;

    /** GoBD-Snapshot: Name des Chefs beim Lager-Haken (AUS_LAGER). */
    @Column(name = "lager_abgleich_durch", length = 255)
    private String lagerAbgleichDurch;
}
