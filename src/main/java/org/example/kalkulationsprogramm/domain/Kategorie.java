package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "kategorie")
@Inheritance(strategy = InheritanceType.JOINED)
public class Kategorie
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String beschreibung;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_kategorie_id")
    private Kategorie parentKategorie;

    /**
     * Typische Liefer-Rollen dieser Kategorie. Leer = erbt von der Oberkategorie.
     * Steuert den Lieferanten-Vorschlag beim Preis-Eintragen am Artikel.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "kategorie_rollen", joinColumns = @JoinColumn(name = "kategorie_id"))
    @Column(name = "rolle")
    @Enumerated(EnumType.STRING)
    private Set<LieferantRolle> typischeRollen = new HashSet<>();
}
