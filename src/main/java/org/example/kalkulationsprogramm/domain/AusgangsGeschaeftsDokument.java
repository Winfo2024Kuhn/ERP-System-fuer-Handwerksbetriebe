package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;
import java.util.List;

/**
 * Zentrale Entity für alle ausgehenden Geschäftsdokumente.
 * Anfragen, Auftragsbestätigungen, Rechnungen, etc.
 * 
 * Verknüpfungskette: Anfrage → AB → Rechnung(en)
 */
@Entity
@Table(name = "ausgangs_geschaeftsdokument")
@Getter
@Setter
public class AusgangsGeschaeftsDokument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Transientes Transport-Feld: Anzahl der WPS, die beim letzten
     * {@code erstellen}/{@code aktualisieren} automatisch über die
     * Leistung↔WPS-Verknüpfung dem Projekt zugeordnet wurden.
     * Wird ausschließlich vom {@code LeistungWpsAutoAssignService} gesetzt
     * und vom Controller in die Response-DTO übernommen (Toast im Frontend).
     */
    @Transient
    private Integer autoZugewieseneWps;

    /**
     * Dokumentnummer im Format YYYY/MM/NNNNN, z.B. "2025/01/00001"
     */
    @Column(nullable = false, unique = true, length = 20)
    private String dokumentNummer;

    /**
     * Typ des Dokuments (Anfrage, AB, Rechnung, etc.)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AusgangsGeschaeftsDokumentTyp typ;

    /**
     * Datum des Dokuments
     */
    @Column(nullable = false)
    private LocalDate datum;

    /**
     * Betreff / Titel des Dokuments
     */
    @Column(length = 500)
    private String betreff;

    // --- Beträge ---

    @Column(precision = 12, scale = 2)
    private BigDecimal betragNetto;

    @Column(precision = 12, scale = 2)
    private BigDecimal betragBrutto;

    /**
     * MwSt-Satz als Dezimalzahl, z.B. 0.19 für 19%
     */
    @Column(precision = 5, scale = 4)
    private BigDecimal mwstSatz;

    /**
     * Für Abschlagsrechnungen: Nummer innerhalb der Rechnungskette (1, 2, 3...)
     */
    private Integer abschlagsNummer;

    // --- Inhalt ---

    /**
     * HTML-Inhalt aus dem DocumentBuilder Editor
     */
    @Column(columnDefinition = "LONGTEXT")
    private String htmlInhalt;

    /**
     * JSON der Leistungspositionen (für Neuberechnung bei Konvertierung)
     */
    @Column(columnDefinition = "LONGTEXT")
    private String positionenJson;

    // --- Buchhaltungs-Status ---

    /**
     * Wenn true: Dokument wurde exportiert und ist gesperrt (nicht mehr bearbeitbar).
     * Nur durch Stornierung "rückgängig" zu machen.
     */
    @Column(nullable = false)
    private boolean gebucht = false;

    /**
     * Datum des ersten Exports (Buchung)
     */
    private LocalDate gebuchtAm;

    /**
     * Wenn true: Dokument wurde storniert
     */
    @Column(nullable = false)
    private boolean storniert = false;

    /**
     * Datum der Stornierung
     */
    private LocalDate storniertAm;

    // --- Verknüpfungen ---

    /**
     * Verknüpftes Projekt (optional)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projekt_id")
    private Projekt projekt;

    /**
     * Verknüpftes Anfrage (für Dokumente die aus Anfrage-Kontext erstellt wurden)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anfrage_id")
    private Anfrage anfrage;

    /**
     * Verknüpfter Kunde - für Rechnungsadresse!
     * Die Adresse kommt immer aus Kunde, nicht aus Projekt/Anfrage.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kunde_id")
    private Kunde kunde;

    /**
     * Vorgänger-Dokument für Dokumentenkette.
     * z.B. AB verweist auf Anfrage, Rechnung verweist auf AB
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vorgaenger_id")
    private AusgangsGeschaeftsDokument vorgaenger;

    /**
     * Alle Nachfolger-Dokumente
     */
    @OneToMany(mappedBy = "vorgaenger")
    private List<AusgangsGeschaeftsDokument> nachfolger = new ArrayList<>();

    // --- Zahlungsverfolgung ---

    /**
     * Zahlungsziel in Tagen
     */
    private Integer zahlungszielTage;

    /**
     * Versanddatum (z.B. E-Mail-Versand)
     */
    private LocalDate versandDatum;

    /**
     * Optionaler Override der Rechnungsadresse für dieses Dokument.
     * Wenn gesetzt, wird diese Adresse statt der Kundenadresse verwendet.
     * Ändert NICHT die Kundenstammdaten.
     */
    @Column(length = 500)
    private String rechnungsadresseOverride;

    // --- Ersteller ---

    /**
     * Der Benutzer, der das Dokument erstellt hat
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "erstellt_von_id")
    private FrontendUserProfile erstelltVon;

    // --- Timestamps ---

    @Column(nullable = false, updatable = false)
    private LocalDateTime erstelltAm;

    private LocalDateTime geaendertAm;

    @PrePersist
    protected void onCreate() {
        erstelltAm = LocalDateTime.now();
        geaendertAm = LocalDateTime.now();
        if (datum == null) {
            datum = LocalDate.now();
        }
        if (mwstSatz == null) {
            mwstSatz = new BigDecimal("0.19");
        }
    }

    @PreUpdate
    protected void onUpdate() {
        geaendertAm = LocalDateTime.now();
    }

    // --- Helper Methods ---

    /**
     * Berechnet den MwSt-Betrag aus Netto und MwSt-Satz
     */
    public BigDecimal getMwstBetrag() {
        if (betragNetto == null || mwstSatz == null) {
            return BigDecimal.ZERO;
        }
        return betragNetto.multiply(mwstSatz);
    }

    /** Rechnungstypen, die nach Buchung/Versand gesperrt bleiben */
    private static final Set<AusgangsGeschaeftsDokumentTyp> SPERRBARE_TYPEN = EnumSet.of(
            AusgangsGeschaeftsDokumentTyp.RECHNUNG,
            AusgangsGeschaeftsDokumentTyp.TEILRECHNUNG,
            AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG,
            AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG,
            AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT,
            AusgangsGeschaeftsDokumentTyp.STORNO
    );

    /**
     * Prüft ob das Dokument bearbeitet werden darf.
     *
     * Sperrlogik:
     * - Stornierte Dokumente sind immer gesperrt (Korrekturnachweis).
     * - Gebuchte Rechnungen/Gutschriften/Stornos sind gesperrt (GoBD).
     * - Anfragen und ABs bleiben auch nach Buchung/Versand bearbeitbar.
     */
    public boolean istBearbeitbar() {
        if (storniert) return false;
        if (gebucht && SPERRBARE_TYPEN.contains(typ)) return false;
        return true;
    }
}
