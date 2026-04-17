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

    @Column(nullable = false)
    private boolean bestellt = false;

    String anschnittWinkelLinks;
    String anschnittWinkelRechts;
    String schnittForm;
    String kommentar;

    private LocalDate bestelltAm;

    @Column(name = "exportiert_am")
    private LocalDateTime exportiertAm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id")
    private Lieferanten lieferant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "artikel_id", referencedColumnName = "artikel_id", insertable = false, updatable = false),
            @JoinColumn(name = "lieferant_id", referencedColumnName = "lieferant_id", insertable = false, updatable = false)
    })
    private LieferantenArtikelPreise lieferantenArtikelPreis;

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
}
