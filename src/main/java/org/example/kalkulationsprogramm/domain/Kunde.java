package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
public class Kunde {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String kundennummer;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Anrede anrede;

    private String ansprechspartner;

    private String strasse;
    private String plz;
    private String ort;

    private String telefon;
    private String mobiltelefon;

    private Integer zahlungsziel = 8;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "kunden_emails", joinColumns = @JoinColumn(name = "kunden_id"))
    @Column(name = "email", nullable = false, unique = true)
    private List<String> kundenEmails = new ArrayList<>();

    @OneToMany(mappedBy = "kundenId", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Projekt> projekts = new ArrayList<>();

}
