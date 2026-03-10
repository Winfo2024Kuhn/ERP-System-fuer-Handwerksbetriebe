package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Schnittbilder
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    @Column(nullable = false, unique = true)
    private String bildUrlSchnittbild;
    @Column(nullable = false, unique = true)
    private String form;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Kategorie kategorie;
}
