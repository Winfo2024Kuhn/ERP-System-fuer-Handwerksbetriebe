package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
public class Angebot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String bauvorhaben;
    private BigDecimal betrag;

    private LocalDate emailVersandDatum;

    private LocalDate anlegedatum;

    // Optionales Profilbild für Angebots-Kacheln
    private String bildUrl;

    private String projektStrasse;
    private String projektPlz;
    private String projektOrt;

    @Column(length = 1000)
    private String kurzbeschreibung;

    @OneToMany(mappedBy = "angebot", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AngebotDokument> dokumente = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "angebot_kunden_emails", joinColumns = @JoinColumn(name = "angebot_id"))
    @Column(name = "email")
    private List<String> kundenEmails = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "kunde_id")
    private Kunde kunde;

    @ManyToOne
    @JoinColumn(name = "projekt_id")
    private Projekt projekt;

    @Column(nullable = false, columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt = LocalDateTime.now();

    private boolean abgeschlossen = false;

    @OneToMany(mappedBy = "angebot", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AngebotNotiz> notizen = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (anlegedatum == null) {
            anlegedatum = LocalDate.now();
        }
    }
}
