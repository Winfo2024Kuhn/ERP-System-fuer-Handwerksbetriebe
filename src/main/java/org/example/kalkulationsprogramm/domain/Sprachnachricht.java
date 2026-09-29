package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Eine Nachricht vom Anrufbeantworter. Die Audiodatei liegt unter
 * {@code uploads/sprachnachrichten/<dateiName>}; der Name wird vom ERP erzeugt.
 * "Abgehört" ist ein gemeinsamer Status aller berechtigten Benutzer.
 */
@Getter
@Setter
@Entity
@Table(name = "sprachnachricht")
public class Sprachnachricht implements TelefonKontaktZuordenbar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int anrufbeantworter;

    @Column(nullable = false)
    private LocalDateTime zeitpunkt;

    @Column(name = "nummer_roh", nullable = false, length = 40)
    private String nummerRoh = "";

    @Column(name = "nummer_normalisiert", length = 40)
    private String nummerNormalisiert;

    @Column(name = "dauer_sekunden", nullable = false)
    private int dauerSekunden;

    @Column(name = "datei_name", nullable = false, length = 100)
    private String dateiName;

    @Column(name = "abgehoert_am")
    private LocalDateTime abgehoertAm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "abgehoert_von")
    private FrontendUserProfile abgehoertVon;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anruf_id")
    private TelefonAnruf anruf;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kunde_id")
    private Kunde kunde;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id")
    private Lieferanten lieferant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TelefonZuordnung zuordnung = TelefonZuordnung.KEINE;

    @Column(name = "angelegt_am", nullable = false)
    private LocalDateTime angelegtAm;
}
