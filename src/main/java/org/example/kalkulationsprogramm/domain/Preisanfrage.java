package org.example.kalkulationsprogramm.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Eine Preisanfrage ist eine Einkaufs-Anfrage an mehrere Lieferanten parallel.
 * Nicht zu verwechseln mit {@link Anfrage} (Kunden-Anfrage).
 */
@Entity
@Table(name = "preisanfrage")
@Getter
@Setter
@NoArgsConstructor
public class Preisanfrage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String nummer;

    @Column(length = 255)
    private String bauvorhaben;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projekt_id")
    private Projekt projekt;

    @Column(name = "erstellt_am", nullable = false)
    private LocalDateTime erstelltAm;

    @Column(name = "antwort_frist")
    private LocalDate antwortFrist;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PreisanfrageStatus status = PreisanfrageStatus.OFFEN;

    @Column(columnDefinition = "TEXT")
    private String notiz;

    /**
     * Nach der Vergabe: welcher Lieferant-Eintrag bekam den Auftrag.
     * {@code null}, solange noch nicht vergeben.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vergeben_an_preisanfrage_lieferant_id")
    private PreisanfrageLieferant vergebenAn;

    @OneToMany(mappedBy = "preisanfrage", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PreisanfrageLieferant> lieferanten = new ArrayList<>();

    @OneToMany(mappedBy = "preisanfrage", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PreisanfragePosition> positionen = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (erstelltAm == null) {
            erstelltAm = LocalDateTime.now();
        }
        if (status == null) {
            status = PreisanfrageStatus.OFFEN;
        }
    }

    public void addLieferant(PreisanfrageLieferant pal) {
        lieferanten.add(pal);
        pal.setPreisanfrage(this);
    }

    public void addPosition(PreisanfragePosition pos) {
        positionen.add(pos);
        pos.setPreisanfrage(this);
    }
}
