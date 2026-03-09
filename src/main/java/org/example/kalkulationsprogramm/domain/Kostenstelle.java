package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Kostenstelle für die Kostenverteilung von Lieferantenrechnungen.
 * Ermöglicht die Zuordnung von Kosten zu:
 * - LAGER: Investitionen (keine echten Kosten)
 * - GEMEINKOSTEN: Fixkosten für Gemeinkostensatz-Berechnung
 * - PROJEKT: Projektzuordnung (bestehende Logik)
 */
@Getter
@Setter
@Entity(name = "FirmaKostenstelle")
@Table(name = "firma_kostenstelle")
public class Kostenstelle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String bezeichnung; // z.B. "Lager", "Gemeinkosten", "Werkstatt"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KostenstellenTyp typ;

    @Column(length = 500)
    private String beschreibung;

    /**
     * Ob diese Kostenstelle zu den Fixkosten zählt.
     * Nur relevant für Auswertungen.
     */
    @Column(nullable = false)
    private boolean istFixkosten = false;

    /**
     * Ob es sich um Investitionen handelt (keine echten Kosten).
     * Bei true werden Kosten nicht in Erfolgsrechnung berücksichtigt.
     */
    @Column(nullable = false)
    private boolean istInvestition = false;

    @Column(nullable = false)
    private boolean aktiv = true;

    /**
     * Sortierreihenfolge für UI-Anzeige.
     */
    private Integer sortierung = 0;
}
