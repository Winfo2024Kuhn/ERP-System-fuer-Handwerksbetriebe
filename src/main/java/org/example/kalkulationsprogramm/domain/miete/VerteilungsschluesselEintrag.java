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
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "verteilungsschluessel_eintrag")
public class VerteilungsschluesselEintrag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verteilungsschluessel_id", nullable = false)
    private Verteilungsschluessel verteilungsschluessel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mietpartei_id", nullable = false)
    private Mietpartei mietpartei;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verbrauchsgegenstand_id")
    private Verbrauchsgegenstand verbrauchsgegenstand;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal anteil;

    private String kommentar;
}
