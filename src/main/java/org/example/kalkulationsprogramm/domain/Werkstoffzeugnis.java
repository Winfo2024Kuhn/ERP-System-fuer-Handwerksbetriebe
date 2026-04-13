package org.example.kalkulationsprogramm.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Werkstoffzeugnis nach EN 10204 (Typen 3.1 / 3.2).
 * Weist Materialeigenschaften für den Stahlbau nach.
 * Bestandteil der EN 1090 EXC 2 Dokumentation.
 */
@Getter
@Setter
@Entity
@Table(name = "werkstoffzeugnis")
public class Werkstoffzeugnis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id")
    private Lieferanten lieferant;

    /** Schmelznummer / Charge */
    @Column(length = 100)
    private String schmelzNummer;

    /** Werkstoffgüte z.B. S355J2, 1.4301, St37-2 */
    @Column(length = 100)
    private String materialGuete;

    /** EN 10204 Zeugnistyp: 2.1, 2.2, 3.1, 3.2 */
    @Column(nullable = false, length = 10)
    private String normTyp = "3.1";

    private LocalDate pruefDatum;

    @Column(length = 200)
    private String pruefstelle;

    /** Verknüpfung zum gescannten/hochgeladenen Dokument */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_dokument_id", unique = true)
    private LieferantDokument lieferantDokument;

    /** Zugehöriger Lieferschein (1 Lieferschein : N Werkstoffzeugnisse) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferschein_dokument_id")
    private LieferantDokument lieferscheinDokument;

    @Column(length = 500)
    private String gespeicherterDateiname;

    @Column(length = 500)
    private String originalDateiname;

    @ManyToMany
    @JoinTable(
        name = "werkstoffzeugnis_projekt",
        joinColumns = @JoinColumn(name = "werkstoffzeugnis_id"),
        inverseJoinColumns = @JoinColumn(name = "projekt_id")
    )
    private Set<Projekt> projekte = new HashSet<>();

    @Column(nullable = false)
    private LocalDateTime erstelltAm = LocalDateTime.now();
}
