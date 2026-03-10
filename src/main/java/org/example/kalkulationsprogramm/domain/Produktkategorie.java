package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter

@Entity
public class Produktkategorie
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Original: ProduktkategorieID

    @Column(nullable = false)
    private String bezeichnung;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Verrechnungseinheit verrechnungseinheit;

    private String bildUrl;

    @Column(columnDefinition = "TEXT")
    private String beschreibung;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_kategorie_id") // Original: Unterkategorie
    private Produktkategorie uebergeordneteKategorie;

    @OneToMany(mappedBy = "uebergeordneteKategorie")
    private List<Produktkategorie> unterkategorien = new ArrayList<>();

    @OneToMany(mappedBy = "produktkategorie")
    private List<ProjektProduktkategorie> projektProduktkategorien = new ArrayList<>();

}
