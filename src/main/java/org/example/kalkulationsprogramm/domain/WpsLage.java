package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Eine Schweißlage innerhalb einer WPS (Wurzel-, Füll- oder Decklage).
 * Nach EN ISO 15609-1 werden für jede Lage eigene Parameterbereiche
 * dokumentiert, weil sich Stromstärke, Spannung und Zusatzwerkstoff
 * je nach Lage unterscheiden.
 */
@Getter
@Setter
@Entity
@Table(name = "wps_lage")
public class WpsLage {

    public enum Typ {
        WURZEL,
        FUELL,
        DECK
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "wps_id", nullable = false)
    private Wps wps;

    /** Reihenfolge ab 1 (1 = erste zu schweißende Lage, typischerweise Wurzel). */
    @Column(nullable = false)
    private Integer nummer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Typ typ;

    /** Zielwert Stromstärke in A. Der auf dem Ausdruck gezeigte Bereich ist ±10 %. */
    @Column(name = "current_a", precision = 6, scale = 1)
    private BigDecimal currentA;

    /** Zielwert Spannung in V. Bereich ±10 %. */
    @Column(name = "voltage_v", precision = 5, scale = 2)
    private BigDecimal voltageV;

    /** Drahtvorschub in m/min (nur MAG/MIG). Bereich ±10 %. */
    @Column(name = "wire_speed", precision = 5, scale = 2)
    private BigDecimal wireSpeed;

    /** Zusatzwerkstoff-Durchmesser in mm. */
    @Column(name = "filler_dia_mm", precision = 4, scale = 2)
    private BigDecimal fillerDiaMm;

    /** Schutzgasmenge in l/min. Bereich ±15 %. */
    @Column(name = "gas_flow", precision = 5, scale = 1)
    private BigDecimal gasFlow;

    @Column(length = 500)
    private String bemerkung;
}
