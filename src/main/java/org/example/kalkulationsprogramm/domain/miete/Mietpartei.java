package org.example.kalkulationsprogramm.domain.miete;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "mietpartei", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"mietobjekt_id", "name"})
})
public class Mietpartei {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mietobjekt_id", nullable = false)
    private Mietobjekt mietobjekt;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MietparteiRolle rolle;

    private String email;

    private String telefon;

    @Column(name = "monatlicher_vorschuss", precision = 19, scale = 2)
    private BigDecimal monatlicherVorschuss;
}
