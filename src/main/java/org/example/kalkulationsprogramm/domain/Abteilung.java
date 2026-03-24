package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Entity
public class Abteilung {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @OneToMany(mappedBy = "abteilung", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Arbeitsgang> arbeitsgaenge = new ArrayList<>();

    /**
     * Darf Eingangsrechnungen sehen und genehmigen (Büro-Rolle).
     * Wenn true: sieht ALLE offenen Rechnungen und darf genehmigen.
     */
    @Column(nullable = false)
    private Boolean darfRechnungenGenehmigen = false;

    /**
     * Darf genehmigte Eingangsrechnungen sehen (Buchhaltungs-Rolle).
     * Wenn true (und darfRechnungenGenehmigen=false): sieht NUR genehmigte Rechnungen.
     */
    @Column(nullable = false)
    private Boolean darfRechnungenSehen = false;
}
