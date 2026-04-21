package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Leistung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String bezeichnung;

    @Column(columnDefinition = "TEXT")
    private String beschreibung;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Verrechnungseinheit einheit;

    @Column(precision = 19, scale = 2)
    private BigDecimal preis;

    @ManyToOne(fetch = FetchType.LAZY)
    private Produktkategorie kategorie;

    /**
     * Schweißanweisungen (WPS), die diese Leistung fachlich benötigt.
     * Wird beim Speichern eines Dokuments ausgewertet, um die WPS
     * automatisch dem Projekt zuzuordnen (EN 1090 EXC 2).
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "leistung_wps",
        joinColumns = @JoinColumn(name = "leistung_id"),
        inverseJoinColumns = @JoinColumn(name = "wps_id")
    )
    private Set<Wps> verknuepfteWps = new HashSet<>();
}
