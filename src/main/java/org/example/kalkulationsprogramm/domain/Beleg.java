package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Belegerfassung für die Buchhaltung (Kasse, Privatentnahme, Bank etc.).
 *
 * Im Gegensatz zu LieferantDokument kann ein Beleg auch ohne Lieferant
 * existieren (z.B. Bar-Tankquittung, Kassenbon). Der Workflow ist auf schnelles
 * Scannen am Handy + spätere Validierung am PC ausgelegt.
 *
 * HARTE INVARIANTE: gespeicherter_dateiname ist nicht null — es gibt keine
 * Buchhaltungs-Buchung ohne Belegfoto.
 */
@Getter
@Setter
@Entity
@Table(name = "beleg")
public class Beleg {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "beleg_kategorie", nullable = false, length = 40)
    private BelegKategorie belegKategorie = BelegKategorie.UNZUGEORDNET;

    /**
     * Vom KI-Analyse-Service erkannter Dokumenttyp. Null = noch nicht analysiert
     * oder kein klassifizierbares Geschaeftsdokument. Wenn RECHNUNG/GUTSCHRIFT
     * und Lieferant gesetzt ist, wird automatisch ein LieferantGeschaeftsdokument
     * erzeugt, damit der Beleg auch in der Rechnungsuebersicht erscheint.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "dokument_typ", length = 30)
    private LieferantDokumentTyp dokumentTyp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BelegStatus status = BelegStatus.NEU;

    @Enumerated(EnumType.STRING)
    @Column(name = "ki_analyse_status", nullable = false, length = 20)
    private BelegKiAnalyseStatus kiAnalyseStatus = BelegKiAnalyseStatus.PENDING;

    /**
     * Steuert, ob der gesamte Beleg fuer die Firma gebucht wird ({@code VOLLSTAENDIG})
     * oder nur ausgewaehlte Positionen ({@code TEILWEISE}, z.B. Mischbon mit
     * privatem Einkauf + Buero-Kaffee). Bei TEILWEISE legt der KI-Analyse-Service
     * die einzelnen {@link BelegPosition} an und BelegSplitService berechnet
     * aus der Checkbox-Auswahl die Felder {@link #betragFirmaNetto}/{@link #betragFirmaBrutto}/
     * {@link #betragFirmaMwst}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "aufteilungs_modus", nullable = false, length = 20)
    private BelegAufteilungsModus aufteilungsModus = BelegAufteilungsModus.VOLLSTAENDIG;

    /**
     * Anteil des Belegs, der tatsaechlich auf die Firma gebucht werden soll.
     * Bei VOLLSTAENDIG bleiben diese Felder null und die Buchhaltung liest
     * {@link #betragNetto}/{@link #betragBrutto}/{@link #mwstSatz}. Bei
     * TEILWEISE schreibt BelegSplitService die Summen aus den angehakten
     * Positionen hierhin — der Originalbeleg-Betrag bleibt unveraendert
     * (GoBD-Pflicht: Originalsumme dokumentiert).
     */
    @Column(name = "betrag_firma_netto", precision = 15, scale = 2)
    private BigDecimal betragFirmaNetto;

    @Column(name = "betrag_firma_brutto", precision = 15, scale = 2)
    private BigDecimal betragFirmaBrutto;

    @Column(name = "betrag_firma_mwst", precision = 15, scale = 2)
    private BigDecimal betragFirmaMwst;

    /**
     * true, wenn nur ein Teil des Belegs auf die Firma gebucht wird und dieser
     * Teil auch beziffert ist (Mischbon mit privatem Einkauf).
     */
    @Transient
    public boolean istTeilweiseFirma() {
        return aufteilungsModus == BelegAufteilungsModus.TEILWEISE
                && (betragFirmaNetto != null || betragFirmaBrutto != null);
    }

    /**
     * Der Netto-Betrag, der tatsaechlich auf die Firma gebucht wird -- bei
     * einem Mischbon also ohne den privaten Anteil.
     *
     * Verwendet von: Gemeinkosten im Stundensatz-Rechner,
     * Kostenstellen-Aufteilungen und Steuerberater-Export.
     *
     * Bewusst NICHT verwendet vom Kassenbuch und den Kassen-Summen: dort
     * fliesst real das ganze Bargeld ab, also zaehlt der Originalbetrag.
     * Der Originalbetrag bleibt in jedem Fall unveraendert stehen (GoBD).
     */
    @Transient
    public BigDecimal getBuchungsbetragNetto() {
        if (istTeilweiseFirma()) {
            return betragFirmaNetto != null ? betragFirmaNetto : betragFirmaBrutto;
        }
        return betragNetto != null ? betragNetto : betragBrutto;
    }

    /**
     * Brutto-Gegenstueck zu {@link #getBuchungsbetragNetto()}.
     */
    @Transient
    public BigDecimal getBuchungsbetragBrutto() {
        if (istTeilweiseFirma() && betragFirmaBrutto != null) {
            return betragFirmaBrutto;
        }
        return betragBrutto;
    }

    @Column(name = "beleg_datum")
    private LocalDate belegDatum;

    @Column(name = "beleg_nummer", length = 100)
    private String belegNummer;

    @Column(length = 500)
    private String beschreibung;

    @Column(name = "betrag_netto", precision = 15, scale = 2)
    private BigDecimal betragNetto;

    @Column(name = "betrag_brutto", precision = 15, scale = 2)
    private BigDecimal betragBrutto;

    @Column(name = "mwst_satz", precision = 5, scale = 2)
    private BigDecimal mwstSatz;

    @Column(length = 40)
    private String zahlungsart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id")
    private Lieferanten lieferant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sachkonto_id")
    private Sachkonto sachkonto;

    /**
     * Kostenstelle des Belegs (z.B. "Werkstatt", "Gemeinkosten", "Lager").
     * Wenn die zugeordnete Kostenstelle als Fixkosten markiert ist
     * (Kostenstelle.istFixkosten=true), fliesst der Beleg-Brutto-Betrag im
     * Jahr des Belegdatums in den Gemeinkosten-Block des Verrechnungslohn-
     * Rechners ein. Nullable: Belege ohne Zuordnung bleiben in der Auswertung
     * neutral und tauchen als "noch zuzuordnen" auf.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kostenstelle_id")
    private Kostenstelle kostenstelle;

    @Column(name = "ki_vorgeschlagener_lieferant", length = 255)
    private String kiVorgeschlagenerLieferant;

    @Column(name = "ki_confidence", precision = 3, scale = 2)
    private BigDecimal kiConfidence;

    /**
     * KI-Vorschlag fuer die Kostenstelle (nur ID — ohne FK, damit ein
     * spaeteres Loeschen der Kostenstelle keinen Beleg kaputtmacht und der
     * historische Vorschlag erhalten bleibt).
     */
    @Column(name = "ki_vorgeschlagener_kostenstelle_id")
    private Long kiVorgeschlagenerKostenstelleId;

    /**
     * KI-Vorschlag fuer das Sachkonto. Buchhalter sieht beides nebeneinander
     * (Kostenstelle = "wofuer wird das Geld ausgegeben", Sachkonto = "in
     * welche SKR03-Schublade kommt es buchhalterisch").
     */
    @Column(name = "ki_vorgeschlagener_sachkonto_id")
    private Long kiVorgeschlagenerSachkontoId;

    @Column(name = "ki_kostenkonto_confidence", precision = 3, scale = 2)
    private BigDecimal kiKostenkontoConfidence;

    @Column(name = "ki_kostenkonto_begruendung", length = 500)
    private String kiKostenkontoBegruendung;

    @Lob
    @Column(name = "ki_extraktion_json", columnDefinition = "LONGTEXT")
    private String kiExtraktionJson;

    @Column(name = "ki_fehler_text", length = 1000)
    private String kiFehlerText;

    @Column(name = "original_dateiname", length = 255)
    private String originalDateiname;

    /**
     * Gespeicherter Dateiname (UUID-Prefix). Nullable, weil Umbuchungen
     * (ist_umbuchung=true) auch ohne Beleg-Datei erfasst werden duerfen.
     * Service erzwingt: NULL nur dann erlaubt wenn istUmbuchung=true.
     */
    @Column(name = "gespeicherter_dateiname", length = 255)
    private String gespeicherterDateiname;

    /**
     * Belegfreie Buchhaltungs-Bewegung (Privatentnahme, Kasse->Bank etc.).
     * Wenn true, darf gespeicherterDateiname NULL bleiben.
     */
    @Column(name = "ist_umbuchung", nullable = false)
    private Boolean istUmbuchung = false;

    @Column(name = "mime_type", length = 120)
    private String mimeType;

    @Column(name = "upload_datum", nullable = false)
    private LocalDateTime uploadDatum;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id")
    private Mitarbeiter uploadedBy;

    @Column(name = "validiert_am")
    private LocalDateTime validiertAm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validiert_von_id")
    private Mitarbeiter validiertVon;

    @Column(length = 1000)
    private String notiz;

    @PrePersist
    protected void onCreate() {
        if (uploadDatum == null) {
            uploadDatum = LocalDateTime.now();
        }
    }
}
