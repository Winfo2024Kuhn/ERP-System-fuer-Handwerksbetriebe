package org.example.kalkulationsprogramm.domain.miete;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "mietobjekt")
public class Mietobjekt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String strasse;

    private String plz;

    private String ort;

    @OneToMany(mappedBy = "mietobjekt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Mietpartei> mietparteien = new ArrayList<>();

    @OneToMany(mappedBy = "mietobjekt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Raum> raeume = new ArrayList<>();

    @OneToMany(mappedBy = "mietobjekt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Kostenstelle> kostenstellen = new ArrayList<>();

    @OneToMany(mappedBy = "mietobjekt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Verteilungsschluessel> verteilungsschluessel = new ArrayList<>();
}
