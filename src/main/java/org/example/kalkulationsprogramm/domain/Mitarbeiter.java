package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Entity
public class Mitarbeiter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistisches Sperren: schuetzt gegen paralleles Speichern (auch aus
     * der Mobile-App, die keine Sperr-Oberflaeche hat).
     */
    @Version
    private Long version;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "beschaeftigungsart", nullable = false)
    private Beschaeftigungsart beschaeftigungsart = Beschaeftigungsart.REGULAER;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "krankenkasse_id")
    private Krankenkasse krankenkasse;

    @Column(nullable = false)
    private Boolean kinderlos = false;

    @Column(name = "ist_geschaeftsfuehrer", nullable = false)
    private Boolean istGeschaeftsfuehrer = false;

    @Column(name = "kalkulatorischer_lohn_monat", precision = 12, scale = 2)
    private BigDecimal kalkulatorischerLohnMonat;

    @Column(name = "geldwert_vorteil_monat", precision = 12, scale = 2)
    private BigDecimal geldwertVorteilMonat;

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

    @OneToMany(mappedBy = "mitarbeiter", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MitarbeiterDokument> dokumente = new ArrayList<>();

    @OneToMany(mappedBy = "mitarbeiter", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MitarbeiterNotiz> notizen = new ArrayList<>();

    @OneToMany(mappedBy = "mitarbeiter", cascade = CascadeType.ALL)
    private List<Lohnabrechnung> lohnabrechnungen = new ArrayList<>();
}
