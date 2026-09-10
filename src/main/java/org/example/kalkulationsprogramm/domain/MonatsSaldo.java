package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Performance-Cache für monatliche Saldo-Daten pro Mitarbeiter.
 * 
 * Offene Monate dienen als Berechnungscache; abgeschlossene Monate bewahren
 * den vom Büro geprüften Stand. Die Abschlusssperre wird im Service durchgesetzt.
 * Die rechtsgültigen Quelldaten (Zeitbuchung, Abwesenheit, ZeitkontoKorrektur)
 * bleiben unverändert bestehen. Bei jeder Änderung an den Quelldaten wird
 * der Cache für den betroffenen Monat invalidiert (gueltig=false) und
 * bei der nächsten Abfrage neu berechnet.
 * 
 * Gespeicherte Komponenten pro Monat:
 * - istStunden: Summe aller Zeitbuchungen (ohne PAUSE)
 * - sollStunden: Berechnete Soll-Stunden aus dem Zeitkonto
 * - abwesenheitsStunden: Summe aller Abwesenheiten (Urlaub, Krankheit, etc.)
 * - feiertagsStunden: Bezahlte Feiertagsstunden
 * - korrekturStunden: Manuelle Zeitkonto-Korrekturen
 */
@Getter
@Setter
@Entity
@Table(name = "monats_saldo",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_monats_saldo_mitarbeiter_jahr_monat",
           columnNames = {"mitarbeiter_id", "jahr", "monat"}
       ))
public class MonatsSaldo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private Boolean festgeschrieben = false;

    private LocalDateTime festgeschriebenAm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "festgeschrieben_von_mitarbeiter_id")
    private Mitarbeiter festgeschriebenVon;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mitarbeiter_id", nullable = false)
    private Mitarbeiter mitarbeiter;

    @Column(nullable = false)
    private Integer jahr;

    @Column(nullable = false)
    private Integer monat;

    /** Summe der Arbeitsstunden aus Zeitbuchungen (ohne PAUSE) */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal istStunden = BigDecimal.ZERO;

    /** Berechnete Soll-Stunden (aus Zeitkonto, abzgl. halbe Feiertage) */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal sollStunden = BigDecimal.ZERO;

    /** Summe der Abwesenheitsstunden (Urlaub, Krankheit, Fortbildung, Zeitausgleich) */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal abwesenheitsStunden = BigDecimal.ZERO;

    /** Historischer Abwesenheitsstand: null bei Abschlüssen vor Einführung der Aufschlüsselung. */
    @Column(precision = 10, scale = 2)
    private BigDecimal urlaubStunden;

    @Column(precision = 10, scale = 2)
    private BigDecimal krankheitStunden;

    @Column(precision = 10, scale = 2)
    private BigDecimal fortbildungStunden;

    @Column(precision = 10, scale = 2)
    private BigDecimal zeitausgleichStunden;

    @Column(precision = 10, scale = 2)
    private BigDecimal krankengeldStunden;

    @Column(precision = 10, scale = 2)
    private BigDecimal wiedereingliederungStunden;

    /** Bezahlte Feiertagsstunden (an Arbeitstagen) */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal feiertagsStunden = BigDecimal.ZERO;

    /** Manuelle Zeitkonto-Korrekturen für diesen Monat (aus ZeitkontoKorrektur, Datum im Monat) */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal korrekturStunden = BigDecimal.ZERO;

    /** Ist der Cache gültig? Wird bei Datenänderungen auf false gesetzt. */
    @Column(nullable = false)
    private Boolean gueltig = true;

    /** Zeitpunkt der letzten Berechnung */
    @Column(nullable = false)
    private LocalDateTime berechnetAm;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        if (berechnetAm == null) {
            berechnetAm = LocalDateTime.now();
        }
    }

    /**
     * Gibt das Gesamtergebnis für diesen Monat zurück:
     * Gesamtist = istStunden + abwesenheitsStunden + feiertagsStunden + korrekturStunden
     */
    public BigDecimal getGesamtIst() {
        return istStunden.add(abwesenheitsStunden).add(feiertagsStunden).add(korrekturStunden);
    }

    /**
     * Monatsdifferenz: GesamtIst - Soll
     */
    public BigDecimal getDifferenz() {
        return getGesamtIst().subtract(sollStunden);
    }
}
