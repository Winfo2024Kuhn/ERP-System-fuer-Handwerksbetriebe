package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ein abgeschlossener Kassenbuch-Monat.
 *
 * <p>Der Abschluss ist der Moment, in dem aus vorlaeufigen Aufzeichnungen
 * eine Buchhaltung wird: alle Belege des Monats bekommen ihre dauerhafte
 * laufende Nummer, werden festgeschrieben und lassen sich ab dann nur noch
 * per Storno korrigieren.</p>
 *
 * <p>Ein Monat kann genau einmal abgeschlossen werden und wird nie wieder
 * geoeffnet. Ein "Monat wieder aufmachen"-Knopf waere exakt die Hintertuer,
 * die § 146 Abs. 4 AO ausschliessen soll -- wer sich vertan hat, storniert
 * im laufenden Monat und bucht neu.</p>
 *
 * <p>{@link #chainIndex} und {@link #entryHash} halten fest, an welcher
 * Stelle der Hash-Kette der Abschluss stand. Wer spaeter behauptet, der
 * Monat habe anders ausgesehen, muesste diesen Hash reproduzieren koennen.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "kassenbuch_monatsabschluss")
public class KassenbuchMonatsabschluss {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer jahr;

    /** 1..12 */
    @Column(nullable = false)
    private Integer monat;

    @Column(name = "abgeschlossen_am", nullable = false)
    private LocalDateTime abgeschlossenAm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "abgeschlossen_von_id")
    private Mitarbeiter abgeschlossenVon;

    /** Kassenstand am Monatsanfang -- muss dem Endbestand des Vormonats entsprechen. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal anfangsbestand;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal endbestand;

    @Column(name = "summe_einnahmen", nullable = false, precision = 15, scale = 2)
    private BigDecimal summeEinnahmen;

    @Column(name = "summe_ausgaben", nullable = false, precision = 15, scale = 2)
    private BigDecimal summeAusgaben;

    /** Wie viele Belege der Abschluss festgeschrieben hat. */
    @Column(name = "anzahl_belege", nullable = false)
    private Integer anzahlBelege;

    @Column(name = "erste_laufende_nummer")
    private Long ersteLaufendeNummer;

    @Column(name = "letzte_laufende_nummer")
    private Long letzteLaufendeNummer;

    @Column(name = "chain_index")
    private Long chainIndex;

    @Column(name = "entry_hash", columnDefinition = "CHAR(64)")
    private String entryHash;

    @Column(length = 1000)
    private String bemerkung;
}
