package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Prüfprotokoll eines E-Checks (BGV A3 / DGUV Vorschrift 3).
 * Wird mobil erfasst und kann vom Elektriker am Desktop verifiziert werden.
 */
@Getter
@Setter
@Entity
@Table(name = "betriebsmittel_pruefung")
public class BetriebsmittelPruefung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "betriebsmittel_id", nullable = false)
    private Betriebsmittel betriebsmittel;

    /** Mitarbeiter der die Sichtprüfung / Messung mobil durchgeführt hat */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pruefer_id")
    private Mitarbeiter pruefer;

    @Column(nullable = false)
    private LocalDate pruefDatum;

    /** Nächstes Prüfdatum = pruefDatum + intervall */
    private LocalDate naechstesPruefDatum;

    @Column(nullable = false)
    private boolean bestanden = true;

    /** Schutzklasse: SK I, SK II, SK III */
    @Column(length = 20)
    private String schutzklasse;

    /** Schutzleiterwiderstand in Ohm */
    @Column(precision = 8, scale = 4)
    private BigDecimal messwertSchutzleiter;

    /** Isolationswiderstand in MΩ */
    @Column(precision = 8, scale = 4)
    private BigDecimal messwertIsolationswiderstand;

    /** Berührungsstrom / Ableitstrom in mA */
    @Column(precision = 8, scale = 4)
    private BigDecimal messwertAbleitstrom;

    @Column(columnDefinition = "TEXT")
    private String bemerkung;

    /**
     * TRUE nachdem ein Elektriker am Desktop die Messwerte geprüft und bestätigt hat.
     * Erst dann gilt das Protokoll als abgeschlossen (WPK-Nachweis).
     */
    @Column(nullable = false)
    private boolean vonElektrikerVerifiziert = false;

    @Column(nullable = false)
    private LocalDateTime erstelltAm = LocalDateTime.now();
}
