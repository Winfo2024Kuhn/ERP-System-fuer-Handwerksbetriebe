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

    // EN 1090: Pflicht-Zeugnis je EXC-Klasse (vererbt von parentKategorie wenn null)
    @Enumerated(EnumType.STRING)
    @Column(name = "zeugnis_exc1", columnDefinition = "varchar(30)")
    private ZeugnisTyp zeugnisExc1;

    @Enumerated(EnumType.STRING)
    @Column(name = "zeugnis_exc2", columnDefinition = "varchar(30)")
    private ZeugnisTyp zeugnisExc2;

    @Enumerated(EnumType.STRING)
    @Column(name = "zeugnis_exc3", columnDefinition = "varchar(30)")
    private ZeugnisTyp zeugnisExc3;

    @Enumerated(EnumType.STRING)
    @Column(name = "zeugnis_exc4", columnDefinition = "varchar(30)")
    private ZeugnisTyp zeugnisExc4;
}
