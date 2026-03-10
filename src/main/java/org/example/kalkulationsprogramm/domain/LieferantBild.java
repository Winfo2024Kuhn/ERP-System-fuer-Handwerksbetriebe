package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Bilddatei, die zu einem Lieferanten gehört.
 * Verwendet für Reklamationsbilder und andere Dokumentation.
 */
@Getter
@Setter
@Entity
public class LieferantBild {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id", nullable = false)
    private Lieferanten lieferant;

    @Column(nullable = false)
    private String originalDateiname;

    @Column(nullable = false)
    private String gespeicherterDateiname;

    private String beschreibung;

    @Column(name = "erstellt_am")
    private LocalDateTime erstelltAm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mitarbeiter_id")
    private Mitarbeiter hochgeladenVon;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reklamation_id")
    private LieferantReklamation reklamation;
}
