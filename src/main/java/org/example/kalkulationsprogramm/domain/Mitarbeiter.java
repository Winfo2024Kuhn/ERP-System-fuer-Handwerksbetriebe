package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Mitarbeiter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String vorname;

    @Column(nullable = false)
    private String nachname;

    private String strasse;
    private String plz;
    private String ort;

    @Column
    private String email;

    @Column
    private String telefon; // Mobiltelefon

    @Column
    private String festnetz; // Festnetznummer

    @Enumerated(EnumType.STRING)
    @Column
    private Qualifikation qualifikation;

    @Column
    private LocalDate geburtstag;

    @Column
    private LocalDate eintrittsdatum;

    @Column(nullable = false)
    private Boolean aktiv = true;

    @Column(precision = 10, scale = 2)
    private BigDecimal stundenlohn;

    @Column
    private Integer jahresUrlaub;

    /**
     * Resturlaub aus dem Vorjahr (in Tagen).
     * Verfällt am 1. Februar des aktuellen Jahres.
     */
    @Column
    private Integer resturlaubVorjahr;

    /**
     * Manuelle Urlaubskorrektur (in Tagen).
     * Positiv = zusätzliche Tage, Negativ = weniger Tage.
     */
    @Column
    private Integer urlaubsKorrektur;

    @Column(unique = true)
    private String loginToken;

    // N:M Beziehung - Mitarbeiter kann mehreren Abteilungen angehören
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "mitarbeiter_abteilung", joinColumns = @JoinColumn(name = "mitarbeiter_id"), inverseJoinColumns = @JoinColumn(name = "abteilung_id"))
    private Set<Abteilung> abteilungen = new HashSet<>();

    // N:M – EN-1090-Rollen (WPK-Leiter, Schweißaufsicht, etc.)
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "mitarbeiter_en1090_rolle", joinColumns = @JoinColumn(name = "mitarbeiter_id"), inverseJoinColumns = @JoinColumn(name = "rolle_id"))
    private Set<En1090Rolle> en1090Rollen = new HashSet<>();

    @OneToMany(mappedBy = "mitarbeiter", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MitarbeiterQualifikation> qualifikationen = new ArrayList<>();

    @OneToMany(mappedBy = "mitarbeiter", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MitarbeiterDokument> dokumente = new ArrayList<>();

    @OneToMany(mappedBy = "mitarbeiter", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MitarbeiterNotiz> notizen = new ArrayList<>();

    @OneToMany(mappedBy = "mitarbeiter", cascade = CascadeType.ALL)
    private List<Lohnabrechnung> lohnabrechnungen = new ArrayList<>();
}
