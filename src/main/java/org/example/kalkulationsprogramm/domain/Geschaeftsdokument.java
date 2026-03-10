package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Zentrale Entity für alle Geschäftsdokumente.
 * Ermöglicht Verknüpfung: Angebot → Auftragsbestätigung → Rechnung(en)
 */
@Entity
@Table(name = "geschaeftsdokument")
@Getter
@Setter
public class Geschaeftsdokument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Verknüpfung zum Dokumenttyp (z.B. Angebot, Auftragsbestätigung, Rechnung)
    @Enumerated(EnumType.STRING)
    @Column(name = "dokumenttyp_enum", nullable = false, length = 30)
    private Dokumenttyp dokumenttyp;

    @Column(nullable = false, unique = true, length = 50)
    private String dokumentNummer;

    @Column(nullable = false)
    private LocalDate datum;

    @Column(precision = 12, scale = 2)
    private BigDecimal betragNetto;

    @Column(precision = 12, scale = 2)
    private BigDecimal betragBrutto;

    @Column(precision = 5, scale = 4)
    private BigDecimal mwstSatz; // z.B. 0.19 für 19%

    // Für Abschlagsrechnungen: 1, 2, 3...
    private Integer abschlagsNummer;

    // Bezug zum Projekt
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projekt_id")
    private Projekt projekt;

    // Bezug zum Kunden
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kunde_id")
    private Kunde kunde;

    // Verknüpfung zum Vorgängerdokument (z.B. AB → Angebot, Rechnung → AB)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vorgaenger_id")
    private Geschaeftsdokument vorgaengerDokument;

    // Alle Nachfolger-Dokumente
    @OneToMany(mappedBy = "vorgaengerDokument")
    private List<Geschaeftsdokument> nachfolgerDokumente = new ArrayList<>();

    // Zahlungen zu diesem Dokument
    @OneToMany(mappedBy = "geschaeftsdokument", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Zahlung> zahlungen = new ArrayList<>();

    // Gespeicherter HTML-Inhalt aus dem DocumentBuilder
    @Column(columnDefinition = "TEXT")
    private String htmlInhalt;

    // Status-Tracking
    private LocalDate versandDatum;
    private boolean storniert = false;

    // Betreff/Titel des Dokuments
    @Column(length = 255)
    private String betreff;

    // Zahlungsziel in Tagen
    private Integer zahlungszielTage;

    /**
     * Berechnet die Summe aller Zahlungen zu diesem Dokument.
     */
    public BigDecimal getSummeZahlungen() {
        return zahlungen.stream()
                .map(Zahlung::getBetrag)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Berechnet den offenen Betrag (Brutto - Zahlungen).
     */
    public BigDecimal getOffenerBetrag() {
        if (betragBrutto == null)
            return BigDecimal.ZERO;
        return betragBrutto.subtract(getSummeZahlungen());
    }

    /**
     * Prüft ob das Dokument vollständig bezahlt ist.
     */
    public boolean istBezahlt() {
        return getOffenerBetrag().compareTo(BigDecimal.ZERO) <= 0;
    }
}
