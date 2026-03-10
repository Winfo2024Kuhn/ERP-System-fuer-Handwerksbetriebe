package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Notiz zu einem Projekt.
 * Kann von Mitarbeitern sowohl über die Mobile-App als auch über die PC-App
 * erstellt werden.
 */
@Getter
@Setter
@Entity
@Table(name = "projekt_notiz")
public class ProjektNotiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projekt_id", nullable = false)
    private Projekt projekt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mitarbeiter_id", nullable = false)
    private Mitarbeiter mitarbeiter;

    @Column(length = 4000, nullable = false)
    private String notiz;

    @Column(nullable = false)
    private boolean nurFuerErsteller = false;

    @Column(nullable = false)
    private boolean mobileSichtbar = true;

    @Column(nullable = false)
    private LocalDateTime erstelltAm;

    @OneToMany(mappedBy = "notiz", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProjektNotizBild> bilder = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (erstelltAm == null) {
            erstelltAm = LocalDateTime.now();
        }
    }
}
