package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Entity representing an external article number and its price for a supplier.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "ux_aen_ci", columnNames = {"lieferant_id", "externe_artikelnummer"}))
@Getter
@Setter
public class LieferantenArtikelPreise {

    @EmbeddedId
    private LieferantenArtikelPreiseId id = new LieferantenArtikelPreiseId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("artikelId")
    @JoinColumn(name = "artikel_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Artikel artikel;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("lieferantId")
    @JoinColumn(name = "lieferant_id")
    private Lieferanten lieferant;

    @Column(name = "externe_artikelnummer")
    private String externeArtikelnummer;

    private Date preisAenderungsdatum;

    @Column(precision = 19, scale = 2)
    private BigDecimal preis;

    public void setArtikel(Artikel artikel) {
        this.artikel = artikel;
        if (this.id == null) {
            this.id = new LieferantenArtikelPreiseId();
        }
        this.id.setArtikelId(artikel != null ? artikel.getId() : null);
    }

    public void setLieferant(Lieferanten lieferant) {
        this.lieferant = lieferant;
        if (this.id == null) {
            this.id = new LieferantenArtikelPreiseId();
        }
        this.id.setLieferantId(lieferant != null ? lieferant.getId() : null);
    }
}
