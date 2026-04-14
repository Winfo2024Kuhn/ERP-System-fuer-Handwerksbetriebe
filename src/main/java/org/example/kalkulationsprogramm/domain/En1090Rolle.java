package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "en1090_rolle")
public class En1090Rolle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String kurztext;

    @Column(columnDefinition = "TEXT")
    private String beschreibung;

    @Column(nullable = false)
    private Integer sortierung = 0;

    @Column(nullable = false)
    private Boolean aktiv = true;
}
