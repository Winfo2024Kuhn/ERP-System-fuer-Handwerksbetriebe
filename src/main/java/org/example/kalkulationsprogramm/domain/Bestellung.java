package org.example.kalkulationsprogramm.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Bestellkopf eines Einkaufsvorgangs beim Lieferanten.
 *
 * <p>Separater Aggregat-Root gegenueber {@link ArtikelInProjekt} (=
 * Kalkulationsposition). Eine Bestellposition ({@link Bestellposition})
 * kopiert beim Anlegen ihre Daten aus einer AiP und haelt einen
 * optionalen Rueckverweis auf diese Herkunftszeile.</p>
 *
 * <p>Bewusst mit IDS-/IDS-CONNECT-kompatiblen Feldern ({@link #externeBestellnummer},
 * {@link #lieferterminSoll}, {@link #lieferadresse}, {@link #idsReferenz}),
 * damit spaetere Schnittstellen zum Grosshandel nicht noch einmal das
 * Datenmodell aufreissen.</p>
 *
 * <p>GoBD: {@link #erstelltVonName} ist Snapshot — bleibt lesbar, auch
 * wenn der Mitarbeiter-Datensatz spaeter geloescht wird (analog
 * {@link WpsFreigabe}).</p>
 */
@Getter
@Setter
@Entity
@Table(name = "bestellung")
public class Bestellung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Interne fortlaufende Bestellnummer, z. B. {@code B-2026-0042}. */
    @Column(name = "bestellnummer", nullable = false, length = 50, unique = true)
    private String bestellnummer;

    /** Nummer, unter der die Bestellung beim Lieferanten gefuehrt wird (IDS). */
    @Column(name = "externe_bestellnummer", length = 100)
    private String externeBestellnummer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id")
    private Lieferanten lieferant;

    /** Nullable: Lagerbestellungen haben kein Projekt. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projekt_id")
    private Projekt projekt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(30)")
    private BestellStatus status = BestellStatus.ENTWURF;

    @Column(name = "bestellt_am")
    private LocalDate bestelltAm;

    @Column(name = "versendet_am")
    private LocalDateTime versendetAm;

    @Column(name = "liefertermin_soll")
    private LocalDate lieferterminSoll;

    @Column(name = "lieferadresse", columnDefinition = "TEXT")
    private String lieferadresse;

    @Column(name = "kommentar", columnDefinition = "TEXT")
    private String kommentar;

    /**
     * Zeitpunkt des letzten Exports (PDF-Download oder E-Mail-Versand).
     * Nach dem Export darf die Bestellung fachlich nicht mehr veraendert werden.
     */
    @Column(name = "exportiert_am")
    private LocalDateTime exportiertAm;

    /** Platzhalter fuer die IDS-Vorgangs-Referenz (IDS-CONNECT). */
    @Column(name = "ids_referenz", length = 100)
    private String idsReferenz;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "erstellt_von_id")
    private Mitarbeiter erstelltVon;

    /** GoBD-Snapshot: Name des Erstellers zum Zeitpunkt der Anlage. */
    @Column(name = "erstellt_von_name", length = 255)
    private String erstelltVonName;

    @Column(name = "erstellt_am", nullable = false)
    private LocalDateTime erstelltAm = LocalDateTime.now();

    @OneToMany(mappedBy = "bestellung", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("positionsnummer ASC")
    private List<Bestellposition> positionen = new ArrayList<>();

    /** Helfer zum Hinzufuegen, pflegt die Rueckreferenz. */
    public void addPosition(Bestellposition position) {
        position.setBestellung(this);
        this.positionen.add(position);
    }
}
