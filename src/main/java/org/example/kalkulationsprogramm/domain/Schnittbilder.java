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

    @Column(name = "bild_url_schnittbild", nullable = false, unique = true)
    private String bildUrlSchnittbild;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schnitt_achse_id", nullable = false)
    private SchnittAchse schnittAchse;
}
