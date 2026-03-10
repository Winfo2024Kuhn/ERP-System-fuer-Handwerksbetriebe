package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;

/**
 * Arbeitszeitart mit Stundensatz für Stundenabrechnung.
 * 
 * Beispiele:
 * - Monteurstunde (65,00 €/h)
 * - Meisterstunde (85,00 €/h)
 * - Helferstunde (45,00 €/h)
 * - Fahrtzeit (55,00 €/h)
 * 
 * WICHTIG: Bei Dokumenterstellung werden die aktuellen Werte als Snapshot
 * in positionenJson gespeichert. Spätere Preisänderungen haben keinen
 * Einfluss auf bereits erstellte Dokumente.
 */
@Entity
@Table(name = "arbeitszeitart")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Arbeitszeitart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Bezeichnung der Arbeitszeitart (z.B. "Monteurstunde", "Meisterstunde")
     */
    @Column(nullable = false, length = 100)
    private String bezeichnung;

    /**
     * Optionale Beschreibung (HTML für Rich-Text)
     */
    @Column(columnDefinition = "TEXT")
    private String beschreibung;

    /**
     * Stundensatz in Euro (z.B. 65.00)
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal stundensatz;

    /**
     * Aktiv = kann für neue Dokumente verwendet werden
     */
    @Column(nullable = false)
    private boolean aktiv = true;

    /**
     * Sortierreihenfolge für Anzeige
     */
    @Column(nullable = false)
    private int sortierung = 0;
}
