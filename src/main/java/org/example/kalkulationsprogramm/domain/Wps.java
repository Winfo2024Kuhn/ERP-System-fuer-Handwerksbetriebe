package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Schweißanweisung (WPS – Welding Procedure Specification) nach EN ISO 15614-1.
 * Bestandteil der EN 1090 EXC 2 Dokumentation.
 */
@Getter
@Setter
@Entity
@Table(name = "wps")
public class Wps {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** z.B. WPS-2024-001 */
    @Column(nullable = false, unique = true, length = 100)
    private String wpsNummer;

    @Column(length = 255)
    private String bezeichnung;

    /** Norm z.B. EN ISO 15614-1 */
    @Column(nullable = false, length = 100)
    private String norm;

    /** Schweißprozess z.B. 135 MAG, 141 WIG */
    @Column(name = "schweiss_prozess", nullable = false, length = 50)
    private String schweissProzes;

    /** Grundwerkstoff z.B. S235, S355 */
    @Column(length = 100)
    private String grundwerkstoff;

    /** Zusatzwerkstoff / Schweißzusatz */
    @Column(length = 200)
    private String zusatzwerkstoff;

    /** z.B. Stumpfnaht, Kehlnaht */
    @Column(length = 100)
    private String nahtart;

    @Column(precision = 6, scale = 2)
    private BigDecimal blechdickeMin;

    @Column(precision = 6, scale = 2)
    private BigDecimal blechdickeMax;

    private LocalDate revisionsdatum;

    private LocalDate gueltigBis;

    @Column(length = 500)
    private String gespeicherterDateiname;

    @Column(length = 500)
    private String originalDateiname;

    @ManyToMany
    @JoinTable(
        name = "wps_projekt",
        joinColumns = @JoinColumn(name = "wps_id"),
        inverseJoinColumns = @JoinColumn(name = "projekt_id")
    )
    private Set<Projekt> projekte = new HashSet<>();

    @Column(nullable = false)
    private LocalDateTime erstelltAm = LocalDateTime.now();
}
