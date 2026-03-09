package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Notiz zu einem Angebot.
 * Kann von Mitarbeitern sowohl über die Mobile-App als auch über die PC-App
 * erstellt werden. Wird bei Umwandlung in ein Projekt mit transferiert.
 */
@Getter
@Setter
@Entity
@Table(name = "angebot_notiz")
public class AngebotNotiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "angebot_id", nullable = false)
    private Angebot angebot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mitarbeiter_id", nullable = false)
    private Mitarbeiter mitarbeiter;

    @Column(length = 4000, nullable = false)
    private String notiz;

    @Column(nullable = false)
    private boolean mobileSichtbar = true;

    @Column(nullable = false)
    private boolean nurFuerErsteller = false;

    @Column(nullable = false)
    private LocalDateTime erstelltAm;

    @OneToMany(mappedBy = "notiz", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AngebotNotizBild> bilder = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (erstelltAm == null) {
            erstelltAm = LocalDateTime.now();
        }
    }
}
