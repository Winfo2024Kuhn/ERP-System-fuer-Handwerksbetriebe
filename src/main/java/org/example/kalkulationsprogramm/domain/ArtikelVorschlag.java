package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "artikel_vorschlag")
@Getter
@Setter
public class ArtikelVorschlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private ArtikelVorschlagStatus status = ArtikelVorschlagStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "varchar(30)")
    private ArtikelVorschlagTyp typ = ArtikelVorschlagTyp.NEU_ANLAGE;

    @Column(name = "erstellt_am", nullable = false)
    private LocalDateTime erstelltAm;

    @Column(name = "bearbeitet_am")
    private LocalDateTime bearbeitetAm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id")
    private Lieferanten lieferant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quelle_dokument_id")
    private LieferantDokument quelleDokument;

    @Column(name = "externe_artikelnummer", length = 255)
    private String externeArtikelnummer;

    @Column(length = 500)
    private String produktname;

    @Column(length = 500)
    private String produktlinie;

    @Column(length = 2000)
    private String produkttext;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vorgeschlagene_kategorie_id")
    private Kategorie vorgeschlageneKategorie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vorgeschlagener_werkstoff_id")
    private Werkstoff vorgeschlagenerWerkstoff;

    @Column(precision = 19, scale = 4)
    private BigDecimal masse;

    private Integer hoehe;

    private Integer breite;

    @Column(precision = 19, scale = 4)
    private BigDecimal einzelpreis;

    @Column(length = 50)
    private String preiseinheit;

    @Column(name = "ki_konfidenz", precision = 4, scale = 3)
    private BigDecimal kiKonfidenz;

    @Column(name = "ki_begruendung", length = 1000)
    private String kiBegruendung;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "konflikt_artikel_id")
    private Artikel konfliktArtikel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treffer_artikel_id")
    private Artikel trefferArtikel;

    @PrePersist
    protected void onCreate() {
        if (erstelltAm == null) {
            erstelltAm = LocalDateTime.now();
        }
    }
}
