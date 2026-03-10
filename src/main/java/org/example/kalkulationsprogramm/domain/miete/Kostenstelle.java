package org.example.kalkulationsprogramm.domain.miete;

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

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity(name = "MieteKostenstelle")
@Table(name = "miete_kostenstelle", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"mietobjekt_id", "name"})
})
public class Kostenstelle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mietobjekt_id", nullable = false)
    private Mietobjekt mietobjekt;

    @Column(nullable = false)
    private String name;

    private String beschreibung;

    @Column(nullable = false)
    private boolean umlagefaehig = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "standard_schluessel_id")
    private Verteilungsschluessel standardSchluessel;

    @OneToMany(mappedBy = "kostenstelle")
    private List<Kostenposition> kostenpositionen = new ArrayList<>();
}
