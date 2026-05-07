package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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

    // URL zur Google-Bewertungsseite des Betriebs.
    // Wird in E-Mail-Vorlagen über den Platzhalter {{REVIEW_LINK}} als klickbarer Link eingesetzt.
    @Column(name = "google_bewertungs_link", length = 500)
    private String googleBewertungsLink;

    // --- Automatisches Mahnverfahren ---
    // Opt-In: erst aktivieren, wenn die Tage-Schwellen vom Inhaber bestaetigt sind.
    @Column(name = "mahnverfahren_aktiv", nullable = false)
    private boolean mahnverfahrenAktiv = false;

    // Tage nach Faelligkeitsdatum, bis die jeweilige Stufe automatisch ausgeloest wird.
    @Column(name = "tage_bis_zahlungserinnerung", nullable = false)
    private int tageBisZahlungserinnerung = 7;

    @Column(name = "tage_bis_erste_mahnung", nullable = false)
    private int tageBisErsteMahnung = 14;

    @Column(name = "tage_bis_zweite_mahnung", nullable = false)
    private int tageBisZweiteMahnung = 21;

    // Neues Zahlungsziel, das jede ausgeloeste Mahnung dem Kunden setzt.
    @Column(name = "mahnverfahren_neues_zahlungsziel_tage", nullable = false)
    private int mahnverfahrenNeuesZahlungszielTage = 7;
}
