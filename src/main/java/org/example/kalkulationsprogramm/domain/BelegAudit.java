package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Unveraenderlicher Audit-Eintrag fuer das Kassenbuch.
 *
 * <p>Fuer jede Aktion an einem Beleg -- Erfassen, Aendern, Pruefen, Verwerfen,
 * Festschreiben, Stornieren -- sowie fuer Kassenzaehlung und Monatsabschluss
 * wird ein vollstaendiger Snapshot weggeschrieben. Die Tabelle kennt nur
 * INSERT: Eintraege werden nie geloescht und nie veraendert
 * (§ 146 Abs. 4 AO -- Kassenaufzeichnungen muessen unveraenderbar sein, und
 * wo doch korrigiert wird, muss das Urspruengliche sichtbar bleiben).</p>
 *
 * <h3>Hash-Kette</h3>
 * <p>Jeder Eintrag traegt einen {@code entry_hash}: SHA-256 ueber die
 * kanonische Form aller eigenen Felder plus den {@code entry_hash} des
 * Vorgaengers. Wer einen alten Eintrag nachtraeglich veraendert, bricht damit
 * jeden nachfolgenden Hash -- ein Pruefer sieht das mit einem einzigen Aufruf
 * des Verify-Endpunkts, ohne die Daten selbst zu kennen.</p>
 *
 * <p>Baugleich zu {@link AusgangsGeschaeftsDokumentAudit}, das dasselbe fuer
 * die Ausgangsrechnungen macht. Bewusst eine eigene Kette, damit ein Ausfall
 * oder eine Reparatur auf der einen Seite die andere nicht beruehrt.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "beleg_audit")
public class BelegAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Lueckenlose Position in der Kette, beginnend bei 0. Wird beim Anhaengen
     * unter Lock gesetzt. Nullable nur, weil die Spalte vor dem ersten
     * Schreibvorgang leer ist.
     */
    @Column(name = "chain_index")
    private Long chainIndex;

    /**
     * Betroffener Beleg. Ohne Foreign Key: verschwindet der Beleg jemals aus
     * der Haupttabelle, muss trotzdem nachvollziehbar bleiben, welcher Betrag
     * mit welcher Begruendung verschwunden ist. Null bei Aktionen, die keinen
     * einzelnen Beleg betreffen (Kassenzaehlung, Monatsabschluss).
     */
    @Column(name = "beleg_id")
    private Long belegId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "VARCHAR(30)")
    private BelegAuditAktion aktion;

    // ============== Snapshot des Belegs ==============

    @Column(name = "laufende_nummer")
    private Long laufendeNummer;

    @Enumerated(EnumType.STRING)
    @Column(name = "beleg_kategorie", length = 40, columnDefinition = "VARCHAR(40)")
    private BelegKategorie belegKategorie;

    @Enumerated(EnumType.STRING)
    @Column(name = "beleg_status", length = 20, columnDefinition = "VARCHAR(20)")
    private BelegStatus belegStatus;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "aufteilungs_modus", length = 20, columnDefinition = "VARCHAR(20)")
    private BelegAufteilungsModus aufteilungsModus;

    @Column(name = "betrag_firma_netto", precision = 15, scale = 2)
    private BigDecimal betragFirmaNetto;

    @Column(name = "betrag_firma_brutto", precision = 15, scale = 2)
    private BigDecimal betragFirmaBrutto;

    @Column(name = "betrag_firma_mwst", precision = 15, scale = 2)
    private BigDecimal betragFirmaMwst;

    @Column(name = "lieferant_id")
    private Long lieferantId;

    @Column(name = "sachkonto_id")
    private Long sachkontoId;

    @Column(name = "sachkonto_nummer", length = 20)
    private String sachkontoNummer;

    @Column(name = "kostenstelle_id")
    private Long kostenstelleId;

    @Column(name = "ist_umbuchung", nullable = false)
    private boolean istUmbuchung;

    @Column(name = "gespeicherter_dateiname", length = 255)
    private String gespeicherterDateiname;

    /** SHA-256 des Belegfotos. Erkennt, wenn im Ordner ein anderes Bild liegt als geprueft wurde. */
    @Column(name = "datei_hash", columnDefinition = "CHAR(64)")
    private String dateiHash;

    @Column(name = "storno_fuer_beleg_id")
    private Long stornoFuerBelegId;

    @Column(name = "storniert_durch_beleg_id")
    private Long storniertDurchBelegId;

    @Column(nullable = false)
    private boolean festgeschrieben;

    // ============== Bezug fuer beleguebergreifende Aktionen ==============

    /** {@code KASSENZAEHLUNG} oder {@code MONATSABSCHLUSS}; sonst null. */
    @Column(name = "bezug_typ", length = 30)
    private String bezugTyp;

    @Column(name = "bezug_id")
    private Long bezugId;

    /** Klartext-Details, die nicht in den Beleg-Snapshot passen (z.B. gezaehlter Bestand). */
    @Column(columnDefinition = "TEXT")
    private String zusatz;

    // ============== Hash-Kette ==============

    /** entry_hash des Vorgaengers. NULL beim allerersten Eintrag. */
    @Column(name = "previous_hash", columnDefinition = "CHAR(64)")
    private String previousHash;

    @Column(name = "entry_hash", columnDefinition = "CHAR(64)")
    private String entryHash;

    // ============== Wer, wann, warum ==============

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "geaendert_von_id")
    private Mitarbeiter geaendertVon;

    @Column(name = "geaendert_am", nullable = false)
    private LocalDateTime geaendertAm;

    @Column(columnDefinition = "TEXT")
    private String aenderungsgrund;

    @Column(name = "ip_adresse", length = 45)
    private String ipAdresse;

    /**
     * Snapshot aus dem aktuellen Zustand eines Belegs.
     *
     * <p>Die Kettenfelder (chainIndex, previousHash, entryHash) bleiben hier
     * leer -- die setzt {@code BelegAuditService} beim Anhaengen, weil dafuer
     * ein Lock auf den Kettenkopf noetig ist.</p>
     */
    public static BelegAudit fromBeleg(Beleg b, BelegAuditAktion aktion,
                                       Mitarbeiter bearbeiter, String grund, String ipAdresse) {
        BelegAudit a = neutral(aktion, bearbeiter, grund, ipAdresse);
        a.setBelegId(b.getId());
        a.setLaufendeNummer(b.getLaufendeNummer());
        a.setBelegKategorie(b.getBelegKategorie());
        a.setBelegStatus(b.getStatus());
        a.setBelegDatum(b.getBelegDatum());
        a.setBelegNummer(b.getBelegNummer());
        a.setBeschreibung(b.getBeschreibung());
        a.setBetragNetto(b.getBetragNetto());
        a.setBetragBrutto(b.getBetragBrutto());
        a.setMwstSatz(b.getMwstSatz());
        a.setZahlungsart(b.getZahlungsart());
        a.setAufteilungsModus(b.getAufteilungsModus());
        a.setBetragFirmaNetto(b.getBetragFirmaNetto());
        a.setBetragFirmaBrutto(b.getBetragFirmaBrutto());
        a.setBetragFirmaMwst(b.getBetragFirmaMwst());
        a.setLieferantId(b.getLieferant() != null ? b.getLieferant().getId() : null);
        a.setSachkontoId(b.getSachkonto() != null ? b.getSachkonto().getId() : null);
        a.setSachkontoNummer(b.getSachkonto() != null ? b.getSachkonto().getNummer() : null);
        a.setKostenstelleId(b.getKostenstelle() != null ? b.getKostenstelle().getId() : null);
        a.setIstUmbuchung(Boolean.TRUE.equals(b.getIstUmbuchung()));
        a.setGespeicherterDateiname(b.getGespeicherterDateiname());
        a.setDateiHash(b.getDateiHash());
        a.setStornoFuerBelegId(b.getStornoFuerBelegId());
        a.setStorniertDurchBelegId(b.getStorniertDurchBelegId());
        a.setFestgeschrieben(Boolean.TRUE.equals(b.getFestgeschrieben()));
        return a;
    }

    /**
     * Eintrag ohne Beleg-Bezug -- fuer Kassenzaehlung und Monatsabschluss.
     * Die Zahlen dieser Vorgaenge stehen in {@link #zusatz}.
     */
    public static BelegAudit neutral(BelegAuditAktion aktion, Mitarbeiter bearbeiter,
                                     String grund, String ipAdresse) {
        BelegAudit a = new BelegAudit();
        a.setAktion(aktion);
        a.setGeaendertVon(bearbeiter);
        // Auf Mikrosekunden kuerzen: MySQL DATETIME(6) kann nicht mehr. Bliebe
        // die Nanosekunde stehen, wiche die Form im Speicher von der in der
        // Datenbank ab und der Verifier meldete faelschlich eine Manipulation.
        a.setGeaendertAm(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        a.setAenderungsgrund(grund);
        a.setIpAdresse(ipAdresse);
        return a;
    }

    /**
     * Kanonische Serialisierung fuer die Hash-Berechnung.
     *
     * <p>Reihenfolge und Format muessen stabil bleiben -- eine Aenderung hier
     * macht jede bereits gespeicherte Kette ungueltig. Felder mit {@code |}
     * getrennt, NULL als leerer String, Zeitstempel ISO-8601.</p>
     */
    public String canonicalForm() {
        StringBuilder sb = new StringBuilder(768);
        sb.append(emptyIfNull(chainIndex)).append('|');
        sb.append(emptyIfNull(belegId)).append('|');
        sb.append(aktion != null ? aktion.name() : "").append('|');
        sb.append(emptyIfNull(laufendeNummer)).append('|');
        sb.append(belegKategorie != null ? belegKategorie.name() : "").append('|');
        sb.append(belegStatus != null ? belegStatus.name() : "").append('|');
        sb.append(belegDatum != null ? belegDatum.toString() : "").append('|');
        sb.append(emptyIfNull(belegNummer)).append('|');
        sb.append(emptyIfNull(beschreibung)).append('|');
        sb.append(plain(betragNetto)).append('|');
        sb.append(plain(betragBrutto)).append('|');
        sb.append(plain(mwstSatz)).append('|');
        sb.append(emptyIfNull(zahlungsart)).append('|');
        sb.append(aufteilungsModus != null ? aufteilungsModus.name() : "").append('|');
        sb.append(plain(betragFirmaNetto)).append('|');
        sb.append(plain(betragFirmaBrutto)).append('|');
        sb.append(plain(betragFirmaMwst)).append('|');
        sb.append(emptyIfNull(lieferantId)).append('|');
        sb.append(emptyIfNull(sachkontoId)).append('|');
        sb.append(emptyIfNull(sachkontoNummer)).append('|');
        sb.append(emptyIfNull(kostenstelleId)).append('|');
        sb.append(istUmbuchung).append('|');
        sb.append(emptyIfNull(gespeicherterDateiname)).append('|');
        sb.append(emptyIfNull(dateiHash)).append('|');
        sb.append(emptyIfNull(stornoFuerBelegId)).append('|');
        sb.append(emptyIfNull(storniertDurchBelegId)).append('|');
        sb.append(festgeschrieben).append('|');
        sb.append(emptyIfNull(bezugTyp)).append('|');
        sb.append(emptyIfNull(bezugId)).append('|');
        sb.append(emptyIfNull(zusatz)).append('|');
        sb.append(geaendertVon != null ? String.valueOf(geaendertVon.getId()) : "").append('|');
        sb.append(geaendertAm != null ? geaendertAm.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "").append('|');
        sb.append(emptyIfNull(aenderungsgrund)).append('|');
        sb.append(emptyIfNull(ipAdresse));
        return sb.toString();
    }

    /**
     * entry_hash = SHA-256(canonicalForm | previousHash).
     *
     * <p>Aendert sich ein einziges Bit am Eintrag oder am Vorgaenger-Hash,
     * kommt ein voellig anderer Wert heraus -- eine Manipulation ist damit
     * nicht zu verstecken.</p>
     */
    public String computeEntryHash() {
        return sha256(canonicalForm() + "|" + emptyIfNull(previousHash));
    }

    private static String emptyIfNull(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String plain(BigDecimal b) {
        return b == null ? "" : b.toPlainString();
    }

    public static String sha256(String input) {
        if (input == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    /** SHA-256 ueber Rohbytes -- fuer den Hash der Belegdatei. */
    public static String sha256(byte[] input) {
        if (input == null) return null;
        try {
            return toHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    private static String toHex(byte[] hash) {
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
