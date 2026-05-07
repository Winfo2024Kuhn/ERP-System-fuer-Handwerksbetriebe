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

/**
 * Unveränderlicher Audit-Eintrag für ein AusgangsGeschaeftsDokument.
 *
 * <p>Speichert für jede relevante Aktion (Erstellen, Ändern, Buchen, Versenden,
 * Stornieren, Löschen) einen vollständigen Snapshot der Stammdaten plus Begründung
 * und Bearbeiter. Die Tabelle wird ausschließlich per INSERT befüllt — Einträge
 * werden NIE gelöscht oder verändert (GoBD § 146 AO Unveränderbarkeit).</p>
 *
 * <p>Bei Hard-Delete des Originaldokuments bleibt der Audit-Eintrag erhalten, weil
 * die Spalte {@code dokument_id} keinen Foreign-Key-Constraint trägt. So kann ein
 * Steuerprüfer nachvollziehen, welche Dokumentnummer mit welchem Betrag gelöscht
 * wurde und mit welcher Begründung.</p>
 *
 * <h3>Hash-Kette (manipulationssicher)</h3>
 * <p>Jeder Eintrag bekommt einen {@code entry_hash} (SHA-256 über die kanonische
 * Form aller Felder + {@code previous_hash}). Wer einen alten Eintrag verändert,
 * bricht die gesamte nachfolgende Kette → ein Prüfer kann mit einem einzigen
 * Aufruf des Verify-Endpunkts feststellen, ob die Buchhaltung manipuliert wurde.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ausgangs_geschaeftsdokument_audit")
public class AusgangsGeschaeftsDokumentAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Lückenlose, fortlaufende Position dieses Eintrags in der globalen Hash-Kette.
     * Wird beim Anhängen vom AuditService gesetzt (Lock auf audit_chain_state).
     * Nullable nur wegen Backfill — nach dem ersten Boot mit Migration immer gesetzt.
     */
    @Column(name = "chain_index")
    private Long chainIndex;

    @Column(name = "dokument_id", nullable = false)
    private Long dokumentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private AusgangsGeschaeftsDokumentAuditAktion aktion;

    // ============== Snapshot der Daten zum Zeitpunkt der Aktion ==============

    @Column(name = "dokument_nummer", nullable = false, length = 20)
    private String dokumentNummer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "VARCHAR(30)")
    private AusgangsGeschaeftsDokumentTyp typ;

    private LocalDate datum;

    @Column(length = 500)
    private String betreff;

    @Column(name = "betrag_netto", precision = 12, scale = 2)
    private BigDecimal betragNetto;

    @Column(name = "betrag_brutto", precision = 12, scale = 2)
    private BigDecimal betragBrutto;

    @Column(name = "mwst_satz", precision = 5, scale = 4)
    private BigDecimal mwstSatz;

    @Column(name = "abschlags_nummer")
    private Integer abschlagsNummer;

    @Column(name = "projekt_id")
    private Long projektId;

    @Column(name = "anfrage_id")
    private Long anfrageId;

    @Column(name = "kunde_id")
    private Long kundeId;

    @Column(name = "vorgaenger_id")
    private Long vorgaengerId;

    @Column(name = "versand_datum")
    private LocalDate versandDatum;

    @Column(nullable = false)
    private boolean gebucht;

    @Column(name = "gebucht_am")
    private LocalDate gebuchtAm;

    @Column(nullable = false)
    private boolean storniert;

    @Column(name = "storniert_am")
    private LocalDate storniertAm;

    @Column(name = "digital_angenommen", nullable = false)
    private boolean digitalAngenommen;

    /** SHA-256-Hash des HTML-Inhalts; erkennt nachträgliche Manipulation am Dokument-Body. */
    @Column(name = "inhalt_hash", columnDefinition = "CHAR(64)")
    private String inhaltHash;

    // ============== Hash-Kette ==============

    /** entry_hash des vorherigen Eintrags (NULL beim ersten Eintrag der Kette). */
    @Column(name = "previous_hash", columnDefinition = "CHAR(64)")
    private String previousHash;

    /** SHA-256 über die kanonische Repräsentation dieses Eintrags + previousHash. */
    @Column(name = "entry_hash", columnDefinition = "CHAR(64)")
    private String entryHash;

    // ============== Änderungs-Metadaten ==============

    /** User, der die Aktion durchgeführt hat (FrontendUserProfile). Nullable für System-Vorgänge. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "geaendert_von_id")
    private FrontendUserProfile geaendertVon;

    @Column(name = "geaendert_am", nullable = false)
    private LocalDateTime geaendertAm;

    /** Pflicht bei {@link AusgangsGeschaeftsDokumentAuditAktion#GELOESCHT} und {@code GEAENDERT}. */
    @Column(columnDefinition = "TEXT")
    private String aenderungsgrund;

    @Column(name = "ip_adresse", length = 45)
    private String ipAdresse;

    /**
     * Erstellt einen Snapshot aus dem aktuellen Zustand eines Dokuments.
     * Hash-Kette (chainIndex, previousHash, entryHash) wird vom AuditService gesetzt,
     * weil dafür ein Lock auf {@code audit_chain_state} nötig ist.
     */
    public static AusgangsGeschaeftsDokumentAudit fromDokument(
            AusgangsGeschaeftsDokument d,
            AusgangsGeschaeftsDokumentAuditAktion aktion,
            FrontendUserProfile bearbeiter,
            String aenderungsgrund,
            String ipAdresse) {

        AusgangsGeschaeftsDokumentAudit audit = new AusgangsGeschaeftsDokumentAudit();
        audit.setDokumentId(d.getId());
        audit.setAktion(aktion);

        audit.setDokumentNummer(d.getDokumentNummer());
        audit.setTyp(d.getTyp());
        audit.setDatum(d.getDatum());
        audit.setBetreff(d.getBetreff());
        audit.setBetragNetto(d.getBetragNetto());
        audit.setBetragBrutto(d.getBetragBrutto());
        audit.setMwstSatz(d.getMwstSatz());
        audit.setAbschlagsNummer(d.getAbschlagsNummer());

        audit.setProjektId(d.getProjekt() != null ? d.getProjekt().getId() : null);
        audit.setAnfrageId(d.getAnfrage() != null ? d.getAnfrage().getId() : null);
        audit.setKundeId(d.getKunde() != null ? d.getKunde().getId() : null);
        audit.setVorgaengerId(d.getVorgaenger() != null ? d.getVorgaenger().getId() : null);

        audit.setVersandDatum(d.getVersandDatum());
        audit.setGebucht(d.isGebucht());
        audit.setGebuchtAm(d.getGebuchtAm());
        audit.setStorniert(d.isStorniert());
        audit.setStorniertAm(d.getStorniertAm());
        audit.setDigitalAngenommen(d.isDigitalAngenommen());

        audit.setInhaltHash(sha256(d.getHtmlInhalt()));

        audit.setGeaendertVon(bearbeiter);
        audit.setGeaendertAm(LocalDateTime.now());
        audit.setAenderungsgrund(aenderungsgrund);
        audit.setIpAdresse(ipAdresse);

        return audit;
    }

    /**
     * Kanonische Serialisierung für Hash-Berechnung. Reihenfolge und Format MÜSSEN
     * stabil bleiben — sonst wird die ganze Kette ungültig. Felder werden mit
     * {@code |} getrennt, NULLs als leerer String, Zeitstempel ISO-8601 in
     * Mikrosekundenpräzision.
     */
    public String canonicalForm() {
        StringBuilder sb = new StringBuilder(512);
        sb.append(emptyIfNull(chainIndex)).append('|');
        sb.append(emptyIfNull(dokumentId)).append('|');
        sb.append(aktion != null ? aktion.name() : "").append('|');
        sb.append(emptyIfNull(dokumentNummer)).append('|');
        sb.append(typ != null ? typ.name() : "").append('|');
        sb.append(datum != null ? datum.toString() : "").append('|');
        sb.append(emptyIfNull(betreff)).append('|');
        sb.append(plain(betragNetto)).append('|');
        sb.append(plain(betragBrutto)).append('|');
        sb.append(plain(mwstSatz)).append('|');
        sb.append(emptyIfNull(abschlagsNummer)).append('|');
        sb.append(emptyIfNull(projektId)).append('|');
        sb.append(emptyIfNull(anfrageId)).append('|');
        sb.append(emptyIfNull(kundeId)).append('|');
        sb.append(emptyIfNull(vorgaengerId)).append('|');
        sb.append(versandDatum != null ? versandDatum.toString() : "").append('|');
        sb.append(gebucht).append('|');
        sb.append(gebuchtAm != null ? gebuchtAm.toString() : "").append('|');
        sb.append(storniert).append('|');
        sb.append(storniertAm != null ? storniertAm.toString() : "").append('|');
        sb.append(digitalAngenommen).append('|');
        sb.append(emptyIfNull(inhaltHash)).append('|');
        sb.append(geaendertVon != null ? String.valueOf(geaendertVon.getId()) : "").append('|');
        sb.append(geaendertAm != null ? geaendertAm.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "").append('|');
        sb.append(emptyIfNull(aenderungsgrund)).append('|');
        sb.append(emptyIfNull(ipAdresse));
        return sb.toString();
    }

    /**
     * Berechnet entry_hash = SHA-256(canonicalForm | previousHash).
     * Ändert sich auch nur ein Bit am Eintrag oder am Vorgänger-Hash, ist das Ergebnis
     * ein komplett anderer Hash → Manipulation sofort sichtbar.
     */
    public String computeEntryHash() {
        String input = canonicalForm() + "|" + emptyIfNull(previousHash);
        return sha256(input);
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
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
