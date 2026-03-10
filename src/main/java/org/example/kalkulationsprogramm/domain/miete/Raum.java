package org.example.kalkulationsprogramm.domain.miete;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "raum", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"mietobjekt_id", "name"})
})
public class Raum {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mietobjekt_id", nullable = false)
    private Mietobjekt mietobjekt;

    @Column(nullable = false)
    private String name;

    private String beschreibung;

    private BigDecimal flaecheQuadratmeter;

    @OneToMany(mappedBy = "raum", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Verbrauchsgegenstand> verbrauchsgegenstaende = new ArrayList<>();
}
