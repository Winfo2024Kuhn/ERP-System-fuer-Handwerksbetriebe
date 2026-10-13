package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.ColumnDefault;

/**
 * Token-basierte digitale Freigabe eines Geschäftsdokuments durch den Kunden.
 *
 * Snapshot-Pattern: Beim Versand werden die anzeigerelevanten Felder aus dem Quelldokument
 * (Anfrage- oder ProjektGeschaeftsdokument) hierher kopiert. Damit bleibt die Freigabe
 * unabhängig nachvollziehbar, auch wenn das Original später bearbeitet/gelöscht wird.
 *
 * Der HashOriginal ist ein SHA-256-Fingerabdruck über die Geschäftsdaten + serverseitiges
 * Salt; HashAcceptance kommt beim Annehmen dazu und enthält IP, Zeitstempel und E-Mail.
 */
@Entity
@Table(name = "dokument_freigabe")
@Getter
@Setter
public class DokumentFreigabe
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String uuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "quell_typ", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private FreigabeQuellTyp quellTyp;

    @Column(name = "quell_dokument_id", nullable = false)
    private Long quellDokumentId;

    @Column(name = "dokument_nummer", nullable = false, length = 100)
    private String dokumentNummer;

    @Column(name = "dokument_art", nullable = false, length = 50)
    private String dokumentArt;

    @Column(name = "dokument_betrag", precision = 12, scale = 2)
    private BigDecimal dokumentBetrag;

    @Column(name = "dokument_datei", length = 255)
    private String dokumentDatei;

    @Column(name = "bauvorhaben", length = 500)
    private String bauvorhaben;

    @Column(name = "kunde_name", length = 255)
    private String kundeName;

    @Column(name = "kunde_email", length = 255)
    private String kundeEmail;

    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "erstellt_am", nullable = false)
    private LocalDateTime erstelltAm = LocalDateTime.now();

    @Column(name = "ablauf_datum", nullable = false)
    private LocalDateTime ablaufDatum;

    @Column(name = "hash_original", nullable = false, length = 128)
    private String hashOriginal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private FreigabeStatus status = FreigabeStatus.PENDING;

    @Column(name = "akzeptiert_am")
    private LocalDateTime akzeptiertAm;

    @Column(name = "akzeptiert_ip", length = 45)
    private String akzeptiertIp;

    @Column(name = "akzeptiert_user_agent", length = 500)
    private String akzeptiertUserAgent;

    @Column(name = "akzeptiert_email", length = 255)
    private String akzeptiertEmail;

    /**
     * Vorname der konkret klickenden Person. Für Altbestand (Akzeptanzen vor V317)
     * NULL — neue Akzeptanzen müssen den Wert tragen (Validierung im Service,
     * siehe {@code DokumentFreigabeService.akzeptiere}).
     */
    @Column(name = "unterzeichner_vorname", length = 80)
    private String unterzeichnerVorname;

    /** Nachname der konkret klickenden Person. Siehe {@link #unterzeichnerVorname}. */
    @Column(name = "unterzeichner_nachname", length = 80)
    private String unterzeichnerNachname;

    /**
     * Vorberechneter Anzeigename "Vorname Nachname". Wird beim Speichern aus
     * den Einzelfeldern normalisiert, damit das Audit und der Annahme-Beleg
     * immer denselben Wortlaut zeigen wie das Akzeptanz-Formular.
     */
    @Column(name = "unterzeichner_name", length = 160)
    private String unterzeichnerName;

    @Column(name = "hash_acceptance", length = 128)
    private String hashAcceptance;

    /**
     * JSON-Array der blockIds der optionalen (alternativen) Positionen, die der Kunde
     * bei der digitalen Annahme per Checkbox mitbeauftragt hat (z.B. {@code ["a1","b2"]}).
     * NULL/leer = es wurden keine Alternativen gewählt. Fließt als Teil der Beweissicherung
     * in den {@code hashAcceptance} ein.
     */
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "akzeptierte_alternativen")
    private String akzeptierteAlternativen;

    /**
     * Verbindlicher Brutto-Endbetrag der Annahme inkl. der mitbeauftragten Alternativen.
     * NULL = es wurden keine Alternativen gewählt, dann gilt {@link #dokumentBetrag}.
     */
    @Column(name = "akzeptierter_betrag", precision = 12, scale = 2)
    private BigDecimal akzeptierterBetrag;

    /**
     * Snapshot des {@code positionenJson} des Quelldokuments zum Versand-Zeitpunkt.
     * GoBD-/Tamper-Schutz: Ansicht, Alternativ-Validierung und die automatisch erzeugte
     * Auftragsbestätigung leiten sich hieraus ab — nicht aus dem ggf. zwischenzeitlich
     * bearbeiteten Live-Dokument. NULL bei Alt-Freigaben (vor V326) und bei Quelltypen
     * ohne Positionen; der Service fällt dann auf das Live-Dokument zurück.
     */
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "positionen_snapshot")
    private String positionenSnapshot;

    /** Netto-Basisbetrag (ohne Alternativen) zum Versand-Zeitpunkt. Siehe {@link #positionenSnapshot}. */
    @Column(name = "basis_netto", precision = 12, scale = 2)
    private BigDecimal basisNetto;

    /** MwSt-Faktor (z.B. 0.1900) zum Versand-Zeitpunkt. Siehe {@link #positionenSnapshot}. */
    @Column(name = "mwst_satz", precision = 5, scale = 4)
    private BigDecimal mwstSatz;

    /**
     * HMAC-SHA256 über UUID und den zuletzt verschickten Bestätigungscode. Der Code
     * selbst wird nie gespeichert. NULL = kein gültiger Code offen (nie angefordert,
     * bereits verbraucht oder nach zu vielen Fehlversuchen gesperrt).
     * Siehe {@code FreigabeCodeService}.
     */
    @Column(name = "code_hash", length = 64)
    private String codeHash;

    /** Ablauf des aktuell offenen Bestätigungscodes. */
    @Column(name = "code_gueltig_bis")
    private LocalDateTime codeGueltigBis;

    /** Falsche Eingaben für den aktuell offenen Code. */
    @ColumnDefault("0")
    @Column(name = "code_fehlversuche", nullable = false)
    private int codeFehlversuche;

    /**
     * Falsche Eingaben über alle Codes dieser Freigabe. Ab der Gesamtgrenze ist die
     * Online-Freigabe gesperrt – sonst ließe sich über immer neue Codes beliebig raten.
     */
    @ColumnDefault("0")
    @Column(name = "code_fehlversuche_gesamt", nullable = false)
    private int codeFehlversucheGesamt;

    /** Letzter Code-Versand – Grundlage der Wartezeit bis zum nächsten Versand. */
    @Column(name = "code_angefordert_am")
    private LocalDateTime codeAngefordertAm;

    /** Beginn des Stundenfensters für das Versandlimit. */
    @Column(name = "code_fenster_start")
    private LocalDateTime codeFensterStart;

    /** Im aktuellen Stundenfenster verschickte Codes. */
    @ColumnDefault("0")
    @Column(name = "code_fenster_anzahl", nullable = false)
    private int codeFensterAnzahl;

    /** Beweissicherung: Zeitpunkt, zu dem der Bestätigungscode bei der Annahme stimmte. */
    @Column(name = "code_bestaetigt_am")
    private LocalDateTime codeBestaetigtAm;

    /** Beweissicherung: Adresse, an die der bestätigte Code ging. */
    @Column(name = "code_bestaetigt_email", length = 255)
    private String codeBestaetigtEmail;

    /**
     * {@code true}, sobald {@link #kundeEmail} aus dem ersten echten Versand der Mail übernommen
     * wurde (siehe {@code FreigabeEmpfaengerService}). Danach bleibt der Empfänger fest.
     */
    @ColumnDefault("false")
    @Column(name = "kunde_email_aus_versand", nullable = false)
    private boolean kundeEmailAusVersand;

    /**
     * Rechtsnachweis (seit V423): {@code true} = der Kunde verlangt ausdrücklich, dass vor Ablauf
     * der Widerrufsfrist begonnen wird (und weiß, dass sein Widerrufsrecht bei vollständiger
     * Leistung erlischt). {@code false} = nicht verlangt, {@code null} = Annahme vor V423.
     */
    @Column(name = "vorzeitiger_beginn")
    private Boolean vorzeitigerBeginn;

    /** Stand der Widerrufsbelehrung, die bei der Annahme galt (z. B. {@code 2026-10-13}). */
    @Column(name = "widerruf_stand", length = 20)
    private String widerrufStand;

    /** Stand der Datenschutzerklärung, die bei der Annahme galt. */
    @Column(name = "datenschutz_stand", length = 20)
    private String datenschutzStand;

    /** Wortlaut des Pflicht-Häkchens („Ich beauftrage … verbindlich und zahlungspflichtig.“). */
    @Column(name = "bestaetigung_text", length = 500)
    private String bestaetigungText;

    /** Beschriftung des Knopfs, der die Annahme ausgelöst hat (Button-Lösung, § 312j BGB). */
    @Column(name = "knopf_text", length = 100)
    private String knopfText;

    @PrePersist
    protected void onCreate()
    {
        if (erstelltAm == null)
        {
            erstelltAm = LocalDateTime.now();
        }
    }

    @Transient
    public boolean istAbgelaufen()
    {
        return ablaufDatum != null && LocalDateTime.now().isAfter(ablaufDatum);
    }
}
