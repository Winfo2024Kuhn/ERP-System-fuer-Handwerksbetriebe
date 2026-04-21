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
import lombok.Getter;
import lombok.Setter;

/**
 * Einzelne Zeile einer {@link Bestellung}.
 *
 * <p>Vollkopie der Daten zum Bestellzeitpunkt (Snapshot-Muster). Die
 * Herkunfts-Kalkulationszeile ist ueber {@link #ausArtikelInProjekt}
 * weiterhin erreichbar, kann aber auch {@code null} sein (z. B. bei
 * freien Nachbestellungen direkt aus dem Bestelldialog).</p>
 *
 * <p>Eine Position hat entweder einen {@link #artikel} (Stammartikel)
 * <b>oder</b> ist eine Freitextposition ({@link #freitextProduktname}
 * + {@link #freitextProdukttext}). Nie beides gesetzt.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "bestellposition")
public class Bestellposition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bestellung_id", nullable = false)
    private Bestellung bestellung;

    /** Laufende Positionsnummer innerhalb der Bestellung (1..n, IDS-Pflicht). */
    @Column(name = "positionsnummer", nullable = false)
    private Integer positionsnummer;

    /** Herkunft: die Kalkulationsposition, aus der diese Bestellzeile stammt. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aus_artikel_in_projekt_id")
    private ArtikelInProjekt ausArtikelInProjekt;

    // ───── Artikelbezug: entweder Stammartikel ODER Freitext ──────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artikel_id")
    private Artikel artikel;

    /** Artikelnummer beim Lieferanten (IDS-Pflicht fuer Bestellvorgang). */
    @Column(name = "externe_artikelnummer", length = 100)
    private String externeArtikelnummer;

    @Column(name = "freitext_produktname", length = 500)
    private String freitextProduktname;

    @Column(name = "freitext_produkttext", columnDefinition = "TEXT")
    private String freitextProdukttext;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kategorie_id")
    private Kategorie kategorie;

    // ───── Menge & Preis (Snapshot zum Bestellzeitpunkt) ──────────
    @Column(name = "menge", precision = 19, scale = 3)
    private BigDecimal menge;

    @Column(name = "einheit", length = 50)
    private String einheit;

    @Column(name = "stueckzahl")
    private Integer stueckzahl;

    @Column(name = "preis_pro_einheit", precision = 19, scale = 2)
    private BigDecimal preisProEinheit;

    @Column(name = "kilogramm", precision = 19, scale = 2)
    private BigDecimal kilogramm;

    // ───── Fertigungsdetails ──────────────────────────────────────
    @Column(name = "schnitt_form", length = 255)
    private String schnittForm;

    @Column(name = "anschnitt_winkel_links", length = 50)
    private String anschnittWinkelLinks;

    @Column(name = "anschnitt_winkel_rechts", length = 50)
    private String anschnittWinkelRechts;

    @Column(name = "fixmass_mm")
    private Integer fixmassMm;

    // ───── EN 1090 ────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "zeugnis_anforderung", length = 30)
    private ZeugnisTyp zeugnisAnforderung;

    @Column(name = "kommentar", columnDefinition = "TEXT")
    private String kommentar;
}
