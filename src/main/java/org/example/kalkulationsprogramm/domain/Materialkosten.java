package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import org.example.kalkulationsprogramm.domain.einkauf.Einheit;

@Getter
@Setter
@Entity
public class Materialkosten {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projekt_id")
    private Projekt projekt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id")
    private Lieferanten lieferant;

    @Column
    private String beschreibung;

    @Column
    private String externeArtikelnummer;

    @Column
    private Integer monat;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal betrag = BigDecimal.ZERO;

    @Column
    private String rechnungsnummer;

    @Column(name = "artikel_id_snapshot")
    private Long artikelIdSnapshot;

    @Column(name = "lieferanten_artikel_preis_id")
    private Long lieferantenArtikelPreisId;

    @Column(name = "lieferantenname_snapshot", length = 255)
    private String lieferantennameSnapshot;

    @Column(name = "menge_snapshot", precision = 19, scale = 6)
    private BigDecimal mengeSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "einheit_snapshot")
    private Einheit einheitSnapshot;

    @Column(name = "preis_je_einheit_snapshot", precision = 19, scale = 6)
    private BigDecimal preisJeEinheitSnapshot;

    @Column(name = "preisquelle_snapshot", length = 255)
    private String preisquelleSnapshot;

    @Column(name = "preisnotiz_snapshot", length = 1000)
    private String preisnotizSnapshot;
}
