package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Achse eines Profils, entlang derer ein Sonderzuschnitt erfolgt.
 * Pro Kategorie gibt es mehrere Achsen (z.B. zwei bei U-Stahl), und
 * jede Achse bündelt die Schnittbilder, die auf ihr möglich sind.
 */
@Entity
@Getter
@Setter
@Table(name = "schnitt_achse")
public class SchnittAchse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bild_url", nullable = false)
    private String bildUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kategorie_id", nullable = false)
    private Kategorie kategorie;
}
