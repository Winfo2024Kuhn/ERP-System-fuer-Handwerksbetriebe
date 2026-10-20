package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Entity für Feiertage.
 * Gesetzliche Feiertage lädt {@code FeiertagService} für das Bundesland aus den
 * Firmendaten. Der Betrieb kann sie anpassen (halber Tag, abgeschaltet) und eigene
 * Feiertage anlegen; beides überlebt das Neu-Laden.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "datum", "bundesland" }))
public class Feiertag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate datum;

    @Column(nullable = false)
    private String bezeichnung;

    @Column(nullable = false, length = 10)
    private String bundesland = Bundesland.STANDARD.name();

    /**
     * True wenn nur halber Arbeitstag frei (z.B. Heiligabend, Silvester).
     * Bei halbTag=true werden nur 50% der Sollstunden abgezogen.
     */
    @Column(nullable = false)
    private boolean halbTag = false;

    /** False = abgeschaltet (gilt in der Gemeinde nicht); zählt dann nirgends als Feiertag. */
    @Column(nullable = false)
    private boolean aktiv = true;

    /** Vom Betrieb selbst angelegt (z. B. Heiligabend als halber Tag). Nur solche lassen sich löschen. */
    @Column(nullable = false)
    private boolean eigener = false;

    /** Vom Betrieb geändert (halber Tag / abgeschaltet). Neu-Laden lässt die Zeile stehen. */
    @Column(nullable = false)
    private boolean angepasst = false;

    public Feiertag(LocalDate datum, String bezeichnung) {
        this.datum = datum;
        this.bezeichnung = bezeichnung;
        this.bundesland = Bundesland.STANDARD.name();
        this.halbTag = false;
    }

    public Feiertag(LocalDate datum, String bezeichnung, String bundesland) {
        this.datum = datum;
        this.bezeichnung = bezeichnung;
        this.bundesland = bundesland;
        this.halbTag = false;
    }

    public Feiertag(LocalDate datum, String bezeichnung, String bundesland, boolean halbTag) {
        this.datum = datum;
        this.bezeichnung = bezeichnung;
        this.bundesland = bundesland;
        this.halbTag = halbTag;
    }

    /**
     * Convenience-Konstruktor für halbe Feiertage im Standard-Bundesland.
     */
    public static Feiertag halberFeiertag(LocalDate datum, String bezeichnung) {
        return new Feiertag(datum, bezeichnung, Bundesland.STANDARD.name(), true);
    }
}
