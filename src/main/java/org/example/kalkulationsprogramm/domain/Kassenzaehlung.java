package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ein Kassensturz: das tatsaechlich in der Kasse liegende Bargeld wurde
 * gezaehlt und mit dem Stand verglichen, den das Kassenbuch ausweist.
 *
 * <p>Das Finanzamt erwartet, dass eine Barkasse jederzeit "kassensturzfaehig"
 * ist -- gezaehltes Geld und gefuehrtes Kassenbuch muessen zusammenpassen.
 * Weicht etwas ab, ist nicht die Abweichung das Problem, sondern eine
 * Abweichung, die niemand dokumentiert hat.</p>
 *
 * <p>Datensaetze dieser Tabelle werden nur angelegt, nie geaendert und nie
 * geloescht. Eine Zaehlung, die man hinterher zurechtbiegen kann, beweist
 * nichts. Der Service bietet deshalb bewusst keinen Update- oder
 * Delete-Pfad an; jede Zaehlung haengt zusaetzlich als Eintrag in der
 * {@link BelegAudit}-Hash-Kette.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "kassenzaehlung")
public class Kassenzaehlung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Wann tatsaechlich gezaehlt wurde (Systemzeit der Erfassung). */
    @Column(name = "gezaehlt_am", nullable = false)
    private LocalDateTime gezaehltAm;

    /**
     * Tag, auf den sich die Zaehlung bezieht. Getrennt von {@link #gezaehltAm},
     * weil abends um 18 Uhr gezaehlt und erst am naechsten Morgen eingetippt
     * werden darf -- der Bestand gehoert dann trotzdem zum Vortag.
     */
    @Column(nullable = false)
    private LocalDate stichtag;

    /** Was tatsaechlich in der Kassenlade lag. */
    @Column(name = "gezaehlter_bestand", nullable = false, precision = 15, scale = 2)
    private BigDecimal gezaehlterBestand;

    /** Was das Kassenbuch zum Stichtag ausweist. */
    @Column(name = "rechnerischer_bestand", nullable = false, precision = 15, scale = 2)
    private BigDecimal rechnerischerBestand;

    /** gezaehlt minus rechnerisch. Positiv = zu viel Geld in der Kasse. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal differenz;

    /**
     * Zaehlzettel als JSON, z.B. {@code {"50":2,"20":3,"2":7}} fuer zwei
     * Fuenfziger, drei Zwanziger und sieben Zwei-Euro-Stuecke. Optional --
     * wer nur die Endsumme eintippt, laesst das Feld leer.
     */
    @Column(name = "stueckelung_json", columnDefinition = "TEXT")
    private String stueckelungJson;

    /** Pflicht, sobald eine Differenz besteht: woran lag es. */
    @Column(length = 1000)
    private String bemerkung;

    /**
     * Beleg, der eine Differenz in der Kasse ausgleicht (Kassenfehlbetrag
     * oder Kassenueberschuss). NULL, wenn es keine Differenz gab oder der
     * Buchhalter sie bewusst nur dokumentiert und nicht gebucht hat.
     */
    @Column(name = "ausgleich_beleg_id")
    private Long ausgleichBelegId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "erfasst_von_id")
    private Mitarbeiter erfasstVon;
}
