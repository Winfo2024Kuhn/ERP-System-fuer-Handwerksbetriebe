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
}
