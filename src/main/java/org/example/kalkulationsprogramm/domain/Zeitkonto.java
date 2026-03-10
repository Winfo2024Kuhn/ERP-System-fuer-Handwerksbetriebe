package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Zeitkonto für einen Mitarbeiter.
 * Speichert die Sollstunden pro Wochentag.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
public class Zeitkonto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "mitarbeiter_id", nullable = false, unique = true)
    private Mitarbeiter mitarbeiter;

    // Sollstunden pro Wochentag (in Stunden, z.B. 8.0 für 8 Stunden)
    @Column(precision = 4, scale = 2)
    private BigDecimal montagStunden = new BigDecimal("8.00");

    @Column(precision = 4, scale = 2)
    private BigDecimal dienstagStunden = new BigDecimal("8.00");

    @Column(precision = 4, scale = 2)
    private BigDecimal mittwochStunden = new BigDecimal("8.00");

    @Column(precision = 4, scale = 2)
    private BigDecimal donnerstagStunden = new BigDecimal("8.00");

    @Column(precision = 4, scale = 2)
    private BigDecimal freitagStunden = new BigDecimal("8.00");

    @Column(precision = 4, scale = 2)
    private BigDecimal samstagStunden = new BigDecimal("0.00");

    @Column(precision = 4, scale = 2)
    private BigDecimal sonntagStunden = new BigDecimal("0.00");

    /**
     * Früheste erlaubte Zeit für den Buchungsstart (z.B. 06:00).
     * Null = keine Einschränkung.
     */
    @Column
    private LocalTime buchungStartZeit = LocalTime.of(5, 0);

    /**
     * Späteste erlaubte Zeit – offene Buchungen werden danach automatisch gestoppt (z.B. 20:00).
     * Null = keine automatische Beendigung.
     */
    @Column
    private LocalTime buchungEndeZeit = LocalTime.of(20, 0);

    public Zeitkonto(Mitarbeiter mitarbeiter) {
        this.mitarbeiter = mitarbeiter;
    }

    /**
     * Berechnet die Wochensollstunden.
     */
    public BigDecimal getWochenstunden() {
        return montagStunden
                .add(dienstagStunden)
                .add(mittwochStunden)
                .add(donnerstagStunden)
                .add(freitagStunden)
                .add(samstagStunden)
                .add(sonntagStunden);
    }

    /**
     * Gibt die Sollstunden für einen bestimmten Wochentag zurück.
     * 
     * @param dayOfWeek 1=Montag, 7=Sonntag
     */
    public BigDecimal getSollstundenFuerTag(int dayOfWeek) {
        BigDecimal hours = switch (dayOfWeek) {
            case 1 -> montagStunden;
            case 2 -> dienstagStunden;
            case 3 -> mittwochStunden;
            case 4 -> donnerstagStunden;
            case 5 -> freitagStunden;
            case 6 -> samstagStunden;
            case 7 -> sonntagStunden;
            default -> BigDecimal.ZERO;
        };
        return hours != null ? hours : BigDecimal.ZERO;
    }
}
