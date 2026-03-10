package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
public class ArbeitsgangStundensatz {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "arbeitsgang_id")
    private Arbeitsgang arbeitsgang;

    private int jahr;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal satz;
}
