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

    @Column(name = "dokument_id", nullable = false)
    private Long dokumentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AusgangsGeschaeftsDokumentAuditAktion aktion;

    // ============== Snapshot der Daten zum Zeitpunkt der Aktion ==============

    @Column(name = "dokument_nummer", nullable = false, length = 20)
    private String dokumentNummer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
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

    /** SHA-256-Hash des HTML-Inhalts; erkennt nachträgliche Manipulation. */
    @Column(name = "inhalt_hash", length = 64)
    private String inhaltHash;

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

    private static String sha256(String input) {
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
