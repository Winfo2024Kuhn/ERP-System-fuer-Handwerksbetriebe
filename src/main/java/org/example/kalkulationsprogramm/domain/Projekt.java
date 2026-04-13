package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Projekt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Original: ProjektID

    @Column(nullable = false)
    private String bauvorhaben;

    @Column
    private String strasse;

    @Column
    private String plz;

    @Column
    private String ort;

    @Column(length = 1000)
    private String kurzbeschreibung;

    @Column(nullable = false, unique = true)
    private String auftragsnummer;

    @Column(nullable = false)
    private LocalDate anlegedatum;

    @Column
    private LocalDate abschlussdatum;

    // Bild ist optional, daher darf die URL null sein
    @Column(name = "bild_url")
    private String bildUrl;

    @Column(nullable = false)
    private BigDecimal bruttoPreis;

    @Column(nullable = false)
    private boolean bezahlt;

    /**
     * Manuelles Beenden/Schließen des Projekts durch den Benutzer.
     * Unabhängig vom Abschlussdatum.
     */
    @Column(nullable = false)
    private boolean abgeschlossen = false;

    /**
     * Art des Projekts - bestimmt ob produktive oder unproduktive Stunden.
     * PAUSCHAL/REGIE = produktiv (für Gemeinkostensatz-Berechnung)
     * INTERN/GARANTIE = unproduktiv
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjektArt projektArt = ProjektArt.PAUSCHAL;

    /**
     * EN 1090 Ausführungsklasse (optional).
     * Mögliche Werte: EXC_1, EXC_2, EXC_3, EXC_4 – null = kein EN 1090 Projekt.
     */
    @Column(name = "exc_klasse", length = 10)
    private String excKlasse;

    /**
     * @return true wenn Stunden auf diesem Projekt als produktiv gelten.
     */
    @Transient
    public boolean isProduktiv() {
        return projektArt != null && projektArt.isProduktiv();
    }

    // Aus ZUGFeRD-Dokumenten extrahierte Rechnungsdaten

    @BatchSize(size = 30)
    @OneToMany(mappedBy = "projekt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProjektProduktkategorie> projektProduktkategorien = new ArrayList<>();

    @OneToMany(mappedBy = "projekt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Materialkosten> materialkosten = new ArrayList<>();

    @OneToMany(mappedBy = "projekt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ArtikelInProjekt> artikelInProjekt = new ArrayList<>();

    // --- Beziehungen zu den Zwischentabellen ---

    @OneToMany(mappedBy = "projekt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Zeitbuchung> zeitbuchungen = new ArrayList<>();

    @OneToMany(mappedBy = "projekt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProjektDokument> projektDokument = new ArrayList<>();

    @OneToMany(mappedBy = "projekt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Anfrage> anfragen = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kundenId")
    private Kunde kundenId;

    /**
     * Zusätzliche Projekt-spezifische E-Mail-Adressen (z.B. Statiker, externe
     * Beteiligte).
     * Diese sind unabhängig von den Kunden-E-Mails.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "projekt_kunden_emails", joinColumns = @JoinColumn(name = "projekt_id"))
    @Column(name = "email")
    private List<String> kundenEmails = new ArrayList<>();

    // --- Transient Getter für normalisierte Felder (aus Kunde-Relation abgeleitet)
    // ---

    @Transient
    public String getKunde() {
        return kundenId != null ? kundenId.getName() : null;
    }

    @Transient
    public String getKundennummer() {
        return kundenId != null ? kundenId.getKundennummer() : null;
    }

    /**
     * Gibt alle relevanten E-Mail-Adressen für dieses Projekt zurück:
     * Sowohl die projekt-spezifischen als auch die Kunden-E-Mails.
     */
    @Transient
    public List<String> getAllEmails() {
        List<String> allEmails = new ArrayList<>(kundenEmails);
        if (kundenId != null && kundenId.getKundenEmails() != null) {
            kundenId.getKundenEmails().forEach(email -> {
                if (!allEmails.contains(email)) {
                    allEmails.add(email);
                }
            });
        }
        return allEmails;
    }
}
