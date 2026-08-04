package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Ein Preisstand eines Lieferanten fuer einen Artikel, samt dessen Artikelnummer.
 *
 * <p>Die Tabelle ist eine Historie: jede Preisaenderung legt eine neue Zeile an,
 * die bisherige bleibt zur Nachvollziehbarkeit erhalten. Nur die jeweils juengste
 * Zeile je Artikel und Lieferant traegt {@link #aktuell} - alle Auswertungen, die
 * "den Preis" meinen, filtern darauf.
 *
 * <p>Frueher war der Schluessel (artikel_id, lieferant_id), wodurch je Lieferant
 * nur ein einziger Preis speicherbar war und ein neuer den alten ueberschrieben
 * hat. Siehe Migration V338.
 */
@Entity
@Table(name = "lieferanten_artikel_preise", indexes = {
        @Index(name = "ix_lap_lieferant_aen", columnList = "lieferant_id, externe_artikelnummer"),
        @Index(name = "ix_lap_artikel_aktuell", columnList = "artikel_id, aktuell, preis"),
        @Index(name = "ix_lap_verlauf", columnList = "artikel_id, lieferant_id, preis_aenderungsdatum")
})
@Getter
@Setter
public class LieferantenArtikelPreise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artikel_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Artikel artikel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id")
    private Lieferanten lieferant;

    /** Die Artikelnummer, unter der dieser Lieferant den Artikel fuehrt. */
    @Column(name = "externe_artikelnummer")
    private String externeArtikelnummer;

    /** Fachlicher Stand des Preises, z.B. das Datum der Angebots-Mail. */
    private Date preisAenderungsdatum;

    @Column(precision = 19, scale = 2)
    private BigDecimal preis;

    /**
     * Kennzeichnet den derzeit gueltigen Preisstand. Genau eine Zeile je Artikel
     * und Lieferant sollte gesetzt sein; aeltere Staende stehen auf {@code false}.
     */
    @Column(name = "aktuell", nullable = false)
    private boolean aktuell = true;

    /** Herkunft des Preises - macht die Verlaesslichkeit nachvollziehbar. */
    @Enumerated(EnumType.STRING)
    @Column(name = "quelle", nullable = false)
    private PreisQuelle quelle = PreisQuelle.UNBEKANNT;

    /** Freitext, z.B. Angebotsnummer oder Gespraechsnotiz. */
    @Column(name = "notiz")
    private String notiz;

    /** Technischer Erfassungszeitpunkt, unabhaengig vom fachlichen Preisdatum. */
    @Column(name = "erfasst_am")
    private Date erfasstAm;

    @PrePersist
    void erfassungszeitpunktSetzen() {
        if (erfasstAm == null) {
            erfasstAm = new Date();
        }
        if (quelle == null) {
            quelle = PreisQuelle.UNBEKANNT;
        }
    }

    /**
     * Legt den Nachfolger dieses Preisstands an: gleiche Zuordnung und
     * Lieferanten-Artikelnummer, aber neuer Preis. Der bisherige Stand wird
     * dabei als nicht mehr aktuell markiert.
     */
    public LieferantenArtikelPreise neuerPreisstand(BigDecimal neuerPreis, PreisQuelle neueQuelle) {
        this.aktuell = false;
        LieferantenArtikelPreise nachfolger = new LieferantenArtikelPreise();
        nachfolger.setArtikel(this.artikel);
        nachfolger.setLieferant(this.lieferant);
        nachfolger.setExterneArtikelnummer(this.externeArtikelnummer);
        nachfolger.setPreis(neuerPreis);
        nachfolger.setPreisAenderungsdatum(new Date());
        nachfolger.setQuelle(neueQuelle != null ? neueQuelle : PreisQuelle.UNBEKANNT);
        nachfolger.setAktuell(true);
        return nachfolger;
    }
}
