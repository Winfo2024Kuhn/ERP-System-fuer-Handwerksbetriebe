package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Eine Position innerhalb einer Preisanfrage. Kopie der relevanten Bedarfsposition
 * zum Anfrage-Zeitpunkt; {@link #artikelInProjekt} ist der optionale Rueckverweis
 * auf die Original-Bedarfsposition fuer die spaetere Vergabe.
 */
@Entity
@Table(name = "preisanfrage_position")
@Getter
@Setter
@NoArgsConstructor
public class PreisanfragePosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "preisanfrage_id", nullable = false)
    private Preisanfrage preisanfrage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artikel_in_projekt_id")
    private ArtikelInProjekt artikelInProjekt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artikel_id")
    private Artikel artikel;

    @Column(name = "externe_artikelnummer", length = 100)
    private String externeArtikelnummer;

    @Column(length = 255)
    private String produktname;

    @Column(columnDefinition = "TEXT")
    private String produkttext;

    @Column(name = "werkstoff_name", length = 100)
    private String werkstoffName;

    @Column(precision = 12, scale = 3)
    private BigDecimal menge;

    @Column(length = 20)
    private String einheit;

    @Column(columnDefinition = "TEXT")
    private String kommentar;

    @Column(nullable = false)
    private Integer reihenfolge = 0;

    @OneToMany(mappedBy = "preisanfragePosition", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PreisanfrageAngebot> angebote = new ArrayList<>();
}
