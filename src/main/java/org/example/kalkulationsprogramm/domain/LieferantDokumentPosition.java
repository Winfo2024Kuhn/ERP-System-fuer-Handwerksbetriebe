package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;

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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Eine Position auf einem Lieferanten-Dokument (Angebot, AB, Lieferschein,
 * Rechnung, Gutschrift).
 *
 * <p>Ausgelesen von der KI-Dokumentanalyse oder aus ZUGFeRD/XRechnung. Bei der
 * Projektaufteilung nach Positionen hält {@link #projekt} bzw.
 * {@link #kostenstelle} fest, wohin die Position gehört – die Beträge selbst
 * landen wie immer in {@link LieferantDokumentProjektAnteil}.
 */
@Getter
@Setter
@Entity
@Table(name = "lieferant_dokument_position")
@EqualsAndHashCode(of = "id")
@ToString(of = { "id", "positionNr", "positionsArt", "bezeichnung" })
public class LieferantDokumentPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "geschaeftsdokument_id", nullable = false)
    private LieferantGeschaeftsdokument geschaeftsdokument;

    /** Reihenfolge wie auf dem Beleg, ab 1. */
    @Column(name = "position_nr", nullable = false)
    private int positionNr;

    @Enumerated(EnumType.STRING)
    @Column(name = "positions_art", nullable = false)
    private PositionsArt positionsArt = PositionsArt.WARE;

    @Column(name = "externe_artikelnummer", length = 64)
    private String externeArtikelnummer;

    @Column(nullable = false, length = 500)
    private String bezeichnung;

    @Column(precision = 15, scale = 3)
    private BigDecimal menge;

    @Column(length = 20)
    private String mengeneinheit;

    @Column(precision = 15, scale = 4)
    private BigDecimal einzelpreis;

    @Column(length = 20)
    private String preiseinheit;

    @Column(name = "gesamtpreis_netto", precision = 15, scale = 2)
    private BigDecimal gesamtpreisNetto;

    /** Ziel bei der Aufteilung nach Positionen (entweder Projekt oder Kostenstelle). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projekt_id")
    private Projekt projekt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kostenstelle_id")
    private Kostenstelle kostenstelle;
}
