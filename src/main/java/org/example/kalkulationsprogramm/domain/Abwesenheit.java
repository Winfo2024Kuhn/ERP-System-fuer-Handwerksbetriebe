package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entity für Abwesenheiten (Urlaub, Krankheit, Fortbildung).
 * 
 * Ersetzt Zeitbuchung-Einträge mit typ != null.
 * Jede Abwesenheit ist ein einzelner Tag mit Sollstunden.
 */
@Getter
@Setter
@Entity
@Table(name = "abwesenheit", uniqueConstraints = @UniqueConstraint(name = "uk_abwesenheit_mitarbeiter_datum_typ", columnNames = {
        "mitarbeiter_id", "datum", "typ" }))
public class Abwesenheit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mitarbeiter_id", nullable = false)
    private Mitarbeiter mitarbeiter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "urlaubsantrag_id")
    private Urlaubsantrag urlaubsantrag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AbwesenheitsTyp typ;

    @Column(nullable = false)
    private LocalDate datum;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal stunden;

    @Column(length = 500)
    private String notiz;
}
