package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(
    name = "werkstoff",
    uniqueConstraints = @UniqueConstraint(name = "uk_werkstoff_name", columnNames = "name")
)
public class Werkstoff {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Technische Bezeichnung, z.B. "1.4301" oder "EN AW-6060". */
    @Column(name = "name", unique = true)
    private String name;

    /** Alltagstauglicher Name fuer die Oberflaeche, z.B. "Edelstahl 1.4301". */
    @Column(name = "anzeigename")
    private String anzeigename;

    /**
     * Der Begriff aus der Werkstatt, z.B. "V2A" fuer 1.4301 oder "Baustahl" fuer
     * S235JR. Der Schlosser tippt ihn in die Artikelsuche - die Werkstoffnummer
     * steht auf dem Lieferschein, im Kopf hat er den Kurznamen.
     */
    @Column(name = "werkstattname")
    private String werkstattname;

    /** Dichte in g/cm3 - Grundlage fuer Gewicht je Meter und Flaechengewicht. */
    @Column(name = "dichte", precision = 6, scale = 3)
    private BigDecimal dichte;

    @Column(name = "werkstoffnorm")
    private String werkstoffnorm;

    /**
     * Standard-Eignung fuer Feuerverzinken. Der Artikel kann davon abweichen,
     * siehe {@link Artikel#istVerzinkungsgeeignet()}.
     */
    @Column(name = "verzinkungsgeeignet", nullable = false)
    private boolean verzinkungsgeeignet;

    /** Standard-Eignung fuer Pulverbeschichten. */
    @Column(name = "pulverbeschichtungsgeeignet", nullable = false)
    private boolean pulverbeschichtungsgeeignet;

    /** Erklaerung fuer den Anwender, warum eine Beschichtung geht oder nicht. */
    @Column(name = "beschichtungshinweis")
    private String beschichtungshinweis;

    @OneToMany(mappedBy = "werkstoff")
    private List<Artikel> artikel = new ArrayList<>();

    /** Fuer die Anzeige: Anzeigename, sonst der technische Name. */
    public String getAnzeigenameOderName() {
        return anzeigename != null && !anzeigename.isBlank() ? anzeigename : name;
    }
}

