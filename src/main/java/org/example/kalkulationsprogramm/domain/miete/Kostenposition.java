package org.example.kalkulationsprogramm.domain.miete;

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
import lombok.Getter;
import lombok.Setter;
import org.example.kalkulationsprogramm.domain.LieferantDokument;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "kostenposition")
public class Kostenposition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kostenstelle_id", nullable = false)
    private Kostenstelle kostenstelle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verteilungsschluessel_id")
    private Verteilungsschluessel verteilungsschluesselOverride;

    @Enumerated(EnumType.STRING)
    @Column(name = "berechnung")
    private KostenpositionBerechnung berechnung = KostenpositionBerechnung.BETRAG;

    @Column(name = "verbrauchsfaktor", precision = 19, scale = 6)
    private BigDecimal verbrauchsfaktor;

    @Column(nullable = false)
    private Integer abrechnungsJahr;

    @Column(precision = 19, scale = 2)
    private BigDecimal betrag;

    private String beschreibung;

    private String belegNummer;

    private LocalDate buchungsdatum;
}
