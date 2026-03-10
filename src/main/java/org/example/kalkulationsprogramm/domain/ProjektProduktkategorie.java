package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
@Entity
public class ProjektProduktkategorie {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projekt_id")
    private Projekt projekt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produktkategorie_id")
    private Produktkategorie produktkategorie;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal menge = BigDecimal.ZERO;

}
