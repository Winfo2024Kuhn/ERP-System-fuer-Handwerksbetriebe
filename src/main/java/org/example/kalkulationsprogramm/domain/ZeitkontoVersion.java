package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.LocalDate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Explizit gepflegte Arbeitszeit; neue Datensätze unterstellen keine 40-Stunden-Woche. */
@Getter
@Setter
@Entity
@Table(name = "zeitkonto_version", uniqueConstraints = @UniqueConstraint(
        name = "uk_zeitkonto_version_mitarbeiter_von", columnNames = {"mitarbeiter_id", "gueltig_von"}))
public class ZeitkontoVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mitarbeiter_id", nullable = false)
    private Mitarbeiter mitarbeiter;

    /** Inklusive Grenzen; null bei gueltigBis bedeutet zeitlich offen. */
    @Column(nullable = false)
    private LocalDate gueltigVon;

    private LocalDate gueltigBis;

    /** Nur Herkunft: Stunden und Zeitfenster bleiben eigenständige Kopien. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vorlage_id")
    private Zeitkontenmodell vorlage;

    @Column(precision = 4, scale = 2)
    private BigDecimal montagStunden = BigDecimal.ZERO;

    @Column(precision = 4, scale = 2)
    private BigDecimal dienstagStunden = BigDecimal.ZERO;

    @Column(precision = 4, scale = 2)
    private BigDecimal mittwochStunden = BigDecimal.ZERO;

    @Column(precision = 4, scale = 2)
    private BigDecimal donnerstagStunden = BigDecimal.ZERO;

    @Column(precision = 4, scale = 2)
    private BigDecimal freitagStunden = BigDecimal.ZERO;

    @Column(precision = 4, scale = 2)
    private BigDecimal samstagStunden = BigDecimal.ZERO;

    @Column(precision = 4, scale = 2)
    private BigDecimal sonntagStunden = BigDecimal.ZERO;

    /** Null = keine Einschränkung; bei Zuweisung ausdrücklich kopieren. */
    private LocalTime buchungStartZeit;

    /** Null = kein automatisches Ende. */
    private LocalTime buchungEndeZeit;

    public BigDecimal getWochenstunden() {
        BigDecimal summe = BigDecimal.ZERO;
        for (int tag = 1; tag <= 7; tag++) {
            summe = summe.add(getSollstundenFuerTag(tag));
        }
        return summe;
    }

    /** ISO-Wochentag: 1 = Montag, 7 = Sonntag; wie beim Altzeitkonto null-sicher. */
    public BigDecimal getSollstundenFuerTag(int dayOfWeek) {
        BigDecimal stunden = switch (dayOfWeek) {
            case 1 -> montagStunden;
            case 2 -> dienstagStunden;
            case 3 -> mittwochStunden;
            case 4 -> donnerstagStunden;
            case 5 -> freitagStunden;
            case 6 -> samstagStunden;
            case 7 -> sonntagStunden;
            default -> BigDecimal.ZERO;
        };
        return stunden == null ? BigDecimal.ZERO : stunden;
    }
}
