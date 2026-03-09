package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

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
}
