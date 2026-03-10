package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;

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
}
