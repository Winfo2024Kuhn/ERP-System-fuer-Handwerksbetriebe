package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Getter
@Setter
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public class Artikel
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Alle Preisstaende dieses Artikels - einschliesslich der historischen.
     * Fuer "den Preis" immer {@link #getAktuellePreise()} verwenden.
     */
    @OneToMany(mappedBy = "artikel", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LieferantenArtikelPreise> artikelpreis = new ArrayList<>();

    /** Nur die derzeit gueltigen Preisstaende, je Lieferant hoechstens einer. */
    @Transient
    public List<LieferantenArtikelPreise> getAktuellePreise()
    {
        return artikelpreis.stream()
                .filter(LieferantenArtikelPreise::isAktuell)
                .toList();
    }

    /** Der guenstigste derzeit gueltige Preis ueber alle Lieferanten. */
    @Transient
    public Optional<LieferantenArtikelPreise> getGuenstigsterPreis()
    {
        return artikelpreis.stream()
                .filter(LieferantenArtikelPreise::isAktuell)
                .filter(p -> p.getPreis() != null)
                .min(Comparator.comparing(LieferantenArtikelPreise::getPreis));
    }

    /** Alle Preisstaende eines Lieferanten, juengster zuerst - fuer die Verlaufsanzeige. */
    @Transient
    public List<LieferantenArtikelPreise> getPreisverlauf(Lieferanten lieferant)
    {
        return artikelpreis.stream()
                .filter(p -> Objects.equals(lieferant, p.getLieferant()))
                .sorted(Comparator.comparing(LieferantenArtikelPreise::getPreisAenderungsdatum,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public String getExterneArtikelnummer()
    {
        return artikelpreis.stream()
                .filter(LieferantenArtikelPreise::isAktuell)
                .map(LieferantenArtikelPreise::getExterneArtikelnummer)
                .filter(n -> n != null && !n.isBlank())
                .findFirst()
                .orElse(null);
    }

    public String getExterneArtikelnummer(Lieferanten lieferant)
    {
        return artikelpreis.stream()
                .filter(LieferantenArtikelPreise::isAktuell)
                .filter(p -> Objects.equals(lieferant, p.getLieferant()))
                .map(LieferantenArtikelPreise::getExterneArtikelnummer)
                .filter(n -> n != null && !n.isBlank())
                .findFirst()
                .orElse(null);
    }

    /**
     * Setzt die Artikelnummer ohne Lieferantenbezug. Ersetzt nur den aktuellen
     * lieferantenlosen Eintrag - bereits erfasste Lieferantenpreise und die
     * Preishistorie bleiben erhalten.
     */
    public void setExterneArtikelnummer(String nummer)
    {
        artikelpreis.removeIf(p -> p.getLieferant() == null && p.isAktuell());
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
        if (nummer != null && artikelpreis.stream()
                .filter(LieferantenArtikelPreise::isAktuell)
                .noneMatch(n -> nummer.equals(n.getExterneArtikelnummer())))
        {
            LieferantenArtikelPreise externeNummer = new LieferantenArtikelPreise();
            externeNummer.setArtikel(this);
            externeNummer.setExterneArtikelnummer(nummer);
            artikelpreis.add(externeNummer);
        }
    }

    public void addExterneArtikelnummer(Lieferanten lieferant, String nummer)
    {
        if (nummer != null && artikelpreis.stream()
                .filter(LieferantenArtikelPreise::isAktuell)
                .noneMatch(n -> nummer.equals(n.getExterneArtikelnummer()) && Objects.equals(lieferant, n.getLieferant())))
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

    /**
     * Kurztext fuer die Innensicht: Er hilft dem Bediener, den Artikel im
     * DocumentEditor wiederzufinden, und steht nicht auf dem Kundendokument.
     * Entspricht {@code Leistung.bezeichnung}.
     */
    @Column(name = "kurzbeschreibung", length = 255)
    private String kurzbeschreibung;

    /**
     * Rich-Text-HTML, das der Kunde auf PDF und Freigabe-Website liest.
     * Entspricht {@code Leistung.beschreibung}.
     */
    @Column(name = "beschreibung", columnDefinition = "TEXT")
    private String beschreibung;

    /**
     * Aufschlag auf den Einkaufspreis in Prozent. {@code null} = nicht gepflegt;
     * dann steht der reine Einkaufspreis im Vorschlag und der Editor warnt.
     *
     * <p>Nicht zu verwechseln mit {@code ArtikelDetailDto.LieferantEintragDto.aufschlagProzent} —
     * das ist die Abweichung zum guenstigsten Lieferanten, keine Verkaufsmarge.
     */
    @Column(name = "verkaufsaufschlag_prozent", precision = 5, scale = 2)
    private BigDecimal verkaufsaufschlagProzent;

    @Enumerated(EnumType.STRING)
    private Verrechnungseinheit verrechnungseinheit;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kategorie_id")
    private Kategorie kategorie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "werkstoff_id")
    private Werkstoff werkstoff;

    /** Betriebseigene Artikelnummer, z.B. "ST-RR-042.4-2.0". Eindeutig. */
    @Column(name = "artikelnummer", unique = true, length = 64)
    private String artikelnummer;

    /**
     * Normalisierter Suchtext mit allen Schreibweisen ("42.4", "42,4", "rundrohr",
     * "rohr"). Die Suche zerlegt die Eingabe in Woerter und verlangt, dass alle
     * darin vorkommen - so trifft "rundrohr 42.4 x2" den richtigen Artikel.
     */
    @Column(name = "suchtext", columnDefinition = "TEXT")
    private String suchtext;

    @Enumerated(EnumType.STRING)
    @Column(name = "herstellverfahren")
    private Herstellverfahren herstellverfahren;

    @Enumerated(EnumType.STRING)
    @Column(name = "fertigungszustand")
    private Fertigungszustand fertigungszustand;

    @Enumerated(EnumType.STRING)
    @Column(name = "profilform")
    private Profilform profilform;

    /** Massnorm, z.B. "EN 10219-2". Ergibt sich aus Form, Verfahren und Zustand. */
    @Column(name = "massnorm", length = 64)
    private String massnorm;

    /** Werkstoffnorm des Artikels. Faellt sonst auf die Norm des Werkstoffs zurueck. */
    @Column(name = "werkstoffnorm", length = 64)
    private String werkstoffnorm;

    /**
     * Abweichende Verzinkungs-Eignung. {@code null} bedeutet: es gilt der Standard
     * des Werkstoffs. Nur bei einer echten Ausnahme setzen.
     */
    @Column(name = "verzinkungsgeeignet")
    private Boolean verzinkungsgeeignet;

    /** Abweichende Pulverbeschichtungs-Eignung. {@code null} = Standard des Werkstoffs. */
    @Column(name = "pulverbeschichtungsgeeignet")
    private Boolean pulverbeschichtungsgeeignet;

    /** Vom System mitgelieferter Stammdatensatz - wird beim CSV-Import nicht ueberschrieben. */
    @Column(name = "system_stammdaten", nullable = false)
    private boolean systemStammdaten;

    /**
     * Kann das Teil feuerverzinkt werden? Der Werkstoff gibt den Standard vor,
     * ein am Artikel gesetzter Wert geht vor.
     */
    @Transient
    public boolean istVerzinkungsgeeignet() {
        if (verzinkungsgeeignet != null) {
            return verzinkungsgeeignet;
        }
        return werkstoff != null && werkstoff.isVerzinkungsgeeignet();
    }

    /** Kann das Teil pulverbeschichtet werden? Werkstoff-Standard, am Artikel ueberschreibbar. */
    @Transient
    public boolean istPulverbeschichtungsgeeignet() {
        if (pulverbeschichtungsgeeignet != null) {
            return pulverbeschichtungsgeeignet;
        }
        return werkstoff != null && werkstoff.isPulverbeschichtungsgeeignet();
    }

    /** Werkstoffnorm des Artikels, sonst die des Werkstoffs. */
    @Transient
    public String getWirksameWerkstoffnorm() {
        if (werkstoffnorm != null && !werkstoffnorm.isBlank()) {
            return werkstoffnorm;
        }
        return werkstoff != null ? werkstoff.getWerkstoffnorm() : null;
    }
}
