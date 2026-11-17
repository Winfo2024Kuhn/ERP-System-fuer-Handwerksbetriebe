package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Ein festgehaltener Stand eines Ausgangsdokuments („Version wie bei git“), entsteht bei jedem
 * Export mit geändertem Inhalt und vor dem Zurückholen eines älteren Stands.
 *
 * <p>Stände werden nie verändert oder gelöscht. Nur das Löschen eines Entwurfs nimmt sie über
 * {@code ON DELETE CASCADE} mit (der GoBD-Audit bleibt). Die Quelle für die Steuerprüfung ist
 * weiterhin {@link AusgangsGeschaeftsDokumentAudit} mit seiner Hash-Kette – der Stand ist die
 * lesbare Fassung für den Betrieb.</p>
 *
 * <p>{@code aenderungen} (JSON-Array aus Sätzen) wird beim Festhalten gegen den vorigen Stand
 * berechnet, {@code bearbeitetVonIds} (JSON-Array aus Benutzer-IDs) aus der offenen Bearbeitung
 * übernommen.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "ausgangs_dokument_stand", uniqueConstraints = @UniqueConstraint(
        name = "uk_ausgangs_dokument_stand_nummer", columnNames = { "dokument_id", "nummer" }))
public class AusgangsDokumentStand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dokument_id", nullable = false)
    private Long dokumentId;

    /** Laufende Nummer je Dokument, ab 1. */
    @Column(nullable = false)
    private Integer nummer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AusgangsDokumentStandArt art;

    @Column(name = "festgehalten_am", nullable = false)
    private LocalDateTime festgehaltenAm;

    @Column(name = "festgehalten_von_id")
    private Long festgehaltenVonId;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "bearbeitet_von_ids")
    private String bearbeitetVonIds;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String aenderungen;

    @Column(name = "wiederhergestellt_aus_nummer")
    private Integer wiederhergestelltAusNummer;

    /** SHA-256 des Inhalts ({@code DokumentStandInhalt#fingerabdruck}). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "inhalt_fingerabdruck", nullable = false, length = 64, columnDefinition = "char(64)")
    private String inhaltFingerabdruck;

    /** Datei des exportierten PDFs im Upload-Ordner, wenn es mit diesem Stand gespeichert wurde. */
    @Column(name = "pdf_dateiname")
    private String pdfDateiname;

    @Column(length = 500)
    private String betreff;

    private LocalDate datum;

    @Column(name = "betrag_netto", precision = 12, scale = 2)
    private BigDecimal betragNetto;

    @Column(name = "mwst_satz", precision = 5, scale = 4)
    private BigDecimal mwstSatz;

    @Column(name = "zahlungsziel_tage")
    private Integer zahlungszielTage;

    @Column(name = "rechnungsadresse_override", length = 500)
    private String rechnungsadresseOverride;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "positionen_json")
    private String positionenJson;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "html_inhalt")
    private String htmlInhalt;
}
