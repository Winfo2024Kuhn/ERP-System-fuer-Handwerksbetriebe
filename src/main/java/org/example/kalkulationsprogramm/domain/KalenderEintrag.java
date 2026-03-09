package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Entity für Kalendereinträge/Termine im Projektmanagement.
 * Kann optional mit Projekt, Kunde, Lieferant oder Angebot verknüpft werden.
 * Jeder Eintrag hat einen Ersteller (Owner) und kann Teilnehmer (Eingeladene) haben.
 */
@Getter
@Setter
@Entity
@Table(name = "kalender_eintrag")
public class KalenderEintrag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titel;

    @Column(length = 2000)
    private String beschreibung;

    @Column(nullable = false)
    private LocalDate datum;

    private LocalTime startZeit;

    private LocalTime endeZeit;

    @Column(nullable = false)
    private boolean ganztaegig = false;

    /**
     * Hex-Farbcode für die Kalenderanzeige (z.B. "#dc2626")
     */
    private String farbe;

    /**
     * Ersteller/Owner des Termins. Null für "Firmenkalender"-Einträge.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ersteller_id")
    private Mitarbeiter ersteller;

    /**
     * Eingeladene Mitarbeiter (Teilnehmer). Termin erscheint auch in deren Kalendern.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "kalender_eintrag_teilnehmer",
        joinColumns = @JoinColumn(name = "kalender_eintrag_id"),
        inverseJoinColumns = @JoinColumn(name = "mitarbeiter_id")
    )
    private Set<Mitarbeiter> teilnehmer = new HashSet<>();

    // Optionale Verknüpfungen

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projekt_id")
    private Projekt projekt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kunde_id")
    private Kunde kunde;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id")
    private Lieferanten lieferant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "angebot_id")
    private Angebot angebot;

    @Column(nullable = false)
    private LocalDateTime erstelltAm;

    private LocalDateTime aktualisiertAm;

    @PrePersist
    protected void onCreate() {
        erstelltAm = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        aktualisiertAm = LocalDateTime.now();
    }
}
