package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Entity
public class Arbeitsgang {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Original: ArbeitsgangID

    @Column(nullable = false, unique = true)
    private String beschreibung;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "abteilung_id", nullable = false)
    private Abteilung abteilung;

    @OneToMany(mappedBy = "arbeitsgang", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Zeitbuchung> ZeitbuchungList = new ArrayList<>();

    @OneToMany(mappedBy = "arbeitsgang", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ArbeitsgangStundensatz> stundensaetze = new ArrayList<>();
}
