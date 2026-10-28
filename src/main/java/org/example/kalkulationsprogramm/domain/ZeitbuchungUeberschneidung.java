package org.example.kalkulationsprogramm.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Meldung: Eine Handy-Buchung hat einen Eintrag überlappt, der schon im Büro stand, und
 * hatte Vorrang ("Handy gewinnt"). Eine Zeile je Büro-Eintrag – spätere Handy-Buchungen,
 * die denselben Eintrag (oder seinen abgeteilten Rest) weiter kürzen, aktualisieren sie.
 *
 * <p>Die Buchungs-IDs haben keinen Fremdschlüssel: Ein entfernter Eintrag soll die Meldung
 * nicht mitlöschen. Den Stand vorher halten {@link #bueroVon}/{@link #bueroBis} fest.
 */
@Getter
@Setter
@Entity
@Table(name = "zeitbuchung_ueberschneidung")
public class ZeitbuchungUeberschneidung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mitarbeiter_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Mitarbeiter mitarbeiter;

    /** Die Handy-Buchung, die Vorrang hatte (null, solange sie beim Anstechen noch nicht gespeichert ist). */
    @Column(name = "handy_buchung_id")
    private Long handyBuchungId;

    @Column(name = "handy_von", nullable = false)
    private LocalDateTime handyVon;

    @Column(name = "handy_bis")
    private LocalDateTime handyBis;

    /** Der ursprüngliche Büro-Eintrag (kann inzwischen entfernt sein). */
    @Column(name = "buero_buchung_id")
    private Long bueroBuchungId;

    /** Abgeteilter Rest nach der Handy-Buchung (nur bei {@link UeberschneidungErgebnis#GETEILT}). */
    @Column(name = "rest_buchung_id")
    private Long restBuchungId;

    /** Zeitraum des Büro-Eintrags, bevor das Handy ihn verändert hat. */
    @Column(name = "buero_von", nullable = false)
    private LocalDateTime bueroVon;

    @Column(name = "buero_bis")
    private LocalDateTime bueroBis;

    /** "Projekt · Arbeitsgang" bzw. "Pause" – damit die Meldung auch nach dem Entfernen lesbar bleibt. */
    @Column(name = "buero_bezeichnung", length = 500)
    private String bueroBezeichnung;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UeberschneidungErgebnis ergebnis;

    @Column(name = "erstellt_am", nullable = false)
    private LocalDateTime erstelltAm;

    @Column(name = "geaendert_am", nullable = false)
    private LocalDateTime geaendertAm;

    /** Wann der Mitarbeiter die Meldung am Handy als gelesen markiert hat. */
    @Column(name = "mitarbeiter_gesehen_am")
    private LocalDateTime mitarbeiterGesehenAm;
}
