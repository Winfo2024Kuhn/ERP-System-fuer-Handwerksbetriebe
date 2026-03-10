package org.example.kalkulationsprogramm.domain.miete;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "verteilungsschluessel", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"mietobjekt_id", "name"})
})
public class Verteilungsschluessel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mietobjekt_id", nullable = false)
    private Mietobjekt mietobjekt;

    @Column(nullable = false)
    private String name;

    private String beschreibung;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerteilungsschluesselTyp typ = VerteilungsschluesselTyp.PROZENTUAL;

    @OneToMany(mappedBy = "verteilungsschluessel", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VerteilungsschluesselEintrag> eintraege = new ArrayList<>();
}
