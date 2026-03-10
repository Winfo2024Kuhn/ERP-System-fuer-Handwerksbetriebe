package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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
}
