package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Entity für Feiertage.
 * Feiertage werden automatisch für Bayern (inkl. Mariä Himmelfahrt) generiert.
 * Halbe Feiertage (z.B. Heiligabend, Silvester) werden mit halbTag=true
 * markiert.
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
    private String bundesland = "BY"; // Bayern als Standard

    /**
     * True wenn nur halber Arbeitstag frei (z.B. Heiligabend, Silvester).
     * Bei halbTag=true werden nur 50% der Sollstunden abgezogen.
     */
    @Column(nullable = false)
    private boolean halbTag = false;

    public Feiertag(LocalDate datum, String bezeichnung) {
        this.datum = datum;
        this.bezeichnung = bezeichnung;
        this.bundesland = "BY";
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
     * Convenience-Konstruktor für halbe Feiertage in Bayern.
     */
    public static Feiertag halberFeiertag(LocalDate datum, String bezeichnung) {
        return new Feiertag(datum, bezeichnung, "BY", true);
    }
}
