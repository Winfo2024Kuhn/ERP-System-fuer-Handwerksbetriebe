package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Singleton-Entity für Firmenstammdaten.
 * Enthält alle wichtigen Informationen über die eigene Firma
 * für Dokumente, Rechnungen und Korrespondenz.
 */
@Getter
@Setter
@Entity
@Table(name = "firmeninformation")
public class Firmeninformation {

    @Id
    private Long id = 1L; // Singleton - nur ein Datensatz

    /**
     * Optimistisches Sperren: schuetzt gegen paralleles Speichern (auch aus
     * der Mobile-App, die keine Sperr-Oberflaeche hat).
     */
    @Version
    private Long version;

    @Column(nullable = false)
    private String firmenname;

    private String strasse;
    private String plz;
    private String ort;

    private String telefon;
    private String fax;
    private String email;
    private String website;

    // Steuerliche Angaben
    private String steuernummer;
    private String ustIdNr;
    private String handelsregister;
    private String handelsregisterNummer;

    // Bankverbindung
    private String bankName;
    private String iban;
    private String bic;

    // Logo als Datei-Referenz
    private String logoDateiname;

    // Geschäftsführer / Inhaber
    private String geschaeftsfuehrer;

    // Zusätzliche Felder für Dokumente
    @Column(length = 1000)
    private String fusszeileText; // Text für Dokumenten-Fußzeile

    // Hausfarbe des Betriebs als Hex-Wert mit fuehrendem # (z.B. "#500010").
    // Wird in Dokumenten als Akzent gesetzt: Fortschrittsbalken im Abrechnungsstand
    // und die kleinen Nettobetraege. Leer = Standardfarbe.
    @Column(name = "firmenfarbe", length = 7)
    private String firmenfarbe;

    // URL zur Google-Bewertungsseite des Betriebs.
    // Wird in E-Mail-Vorlagen über den Platzhalter {{REVIEW_LINK}} als klickbarer Link eingesetzt.
    @Column(name = "google_bewertungs_link", length = 500)
    private String googleBewertungsLink;

    // --- Automatisches Mahnverfahren ---
    // Opt-In: erst aktivieren, wenn die Tage-Schwellen vom Inhaber bestaetigt sind.
    @Column(name = "mahnverfahren_aktiv", nullable = false)
    private boolean mahnverfahrenAktiv = false;

    // Tage nach Faelligkeitsdatum der Rechnung, bis die Zahlungserinnerung
    // automatisch ausgeloest wird.
    @Column(name = "tage_bis_zahlungserinnerung", nullable = false)
    private int tageBisZahlungserinnerung = 7;

    // Abstand in Tagen NACH dem Versand der Zahlungserinnerung, bis die
    // 1. Mahnung folgt (nicht: Tage nach Faelligkeit der Rechnung).
    @Column(name = "tage_bis_erste_mahnung", nullable = false)
    private int tageBisErsteMahnung = 7;

    // Abstand in Tagen NACH dem Versand der 1. Mahnung, bis die 2. Mahnung
    // folgt (nicht: Tage nach Faelligkeit der Rechnung).
    @Column(name = "tage_bis_zweite_mahnung", nullable = false)
    private int tageBisZweiteMahnung = 7;

    // Neues Zahlungsziel, das jede ausgeloeste Mahnung dem Kunden setzt.
    @Column(name = "mahnverfahren_neues_zahlungsziel_tage", nullable = false)
    private int mahnverfahrenNeuesZahlungszielTage = 7;

    // Gewerk der Firma - liefert den Default-BG-Satz (Unfallversicherung).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gewerk_id")
    private Gewerk gewerk;

    // Tatsaechlicher BG-Satz aus dem Beitragsbescheid. Wenn NULL, gilt der
    // Default-Satz aus dem zugeordneten Gewerk.
    @Column(name = "bg_satz_override", precision = 5, scale = 2)
    private BigDecimal bgSatzOverride;
}
