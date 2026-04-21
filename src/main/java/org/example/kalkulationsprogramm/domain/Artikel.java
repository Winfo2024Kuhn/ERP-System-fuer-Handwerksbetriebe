package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public class Artikel
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "artikel", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LieferantenArtikelPreise> artikelpreis = new ArrayList<>();

    public String getExterneArtikelnummer()
    {
        return artikelpreis.stream()
                .map(LieferantenArtikelPreise::getExterneArtikelnummer)
                .filter(n -> n != null && !n.isBlank())
                .findFirst()
                .orElse(null);
    }

    public String getExterneArtikelnummer(Lieferanten lieferant)
    {
        return artikelpreis.stream()
                .filter(p -> Objects.equals(lieferant, p.getLieferant()))
                .map(LieferantenArtikelPreise::getExterneArtikelnummer)
                .filter(n -> n != null && !n.isBlank())
                .findFirst()
                .orElse(null);
    }

    public void setExterneArtikelnummer(String nummer)
    {
        artikelpreis.clear();
        if (nummer != null)
        {
            LieferantenArtikelPreise externeNummer = new LieferantenArtikelPreise();
            externeNummer.setArtikel(this);
            externeNummer.setExterneArtikelnummer(nummer);
            artikelpreis.add(externeNummer);
        }
    }

    public void addExterneArtikelnummer(String nummer)
    {
        if (nummer != null && artikelpreis.stream().noneMatch(n -> nummer.equals(n.getExterneArtikelnummer())))
        {
            LieferantenArtikelPreise externeNummer = new LieferantenArtikelPreise();
            externeNummer.setArtikel(this);
            externeNummer.setExterneArtikelnummer(nummer);
            artikelpreis.add(externeNummer);
        }
    }

    public void addExterneArtikelnummer(Lieferanten lieferant, String nummer)
    {
        if (nummer != null && artikelpreis.stream().noneMatch(n -> nummer.equals(n.getExterneArtikelnummer()) && Objects.equals(lieferant, n.getLieferant())))
        {
            LieferantenArtikelPreise externeNummer = new LieferantenArtikelPreise();
            externeNummer.setArtikel(this);
            externeNummer.setExterneArtikelnummer(nummer);
            externeNummer.setLieferant(lieferant);
            artikelpreis.add(externeNummer);
        }
    }

    String produktlinie;

    String produktname;

    String produkttext;

    Long verpackungseinheit;

    String hicadName;

    String preiseinheit;

    @Enumerated(EnumType.STRING)
    private Verrechnungseinheit verrechnungseinheit;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kategorie_id")
    private Kategorie kategorie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "werkstoff_id")
    private Werkstoff werkstoff;

    @Column(name = "durchschnittspreis_netto", precision = 12, scale = 4)
    private BigDecimal durchschnittspreisNetto;

    @Column(name = "durchschnittspreis_menge", precision = 18, scale = 3)
    private BigDecimal durchschnittspreisMenge;

    @Column(name = "durchschnittspreis_aktualisiert_am")
    private LocalDateTime durchschnittspreisAktualisiertAm;

}
