package org.example.kalkulationsprogramm.domain.miete;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "zaehlerstand", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"verbrauchsgegenstand_id", "abrechnungs_jahr"})
})
public class Zaehlerstand {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verbrauchsgegenstand_id", nullable = false)
    private Verbrauchsgegenstand verbrauchsgegenstand;

    @Column(name = "abrechnungs_jahr", nullable = false)
    private Integer abrechnungsJahr;

    @Column(nullable = false)
    private LocalDate stichtag;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal stand;

    @Column(precision = 19, scale = 4)
    private BigDecimal verbrauch;

    private OffsetDateTime erfasstAm = OffsetDateTime.now();

    private String kommentar;
}
