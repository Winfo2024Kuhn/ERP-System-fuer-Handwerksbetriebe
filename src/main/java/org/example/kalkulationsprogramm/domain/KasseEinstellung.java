package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Singleton-Konfiguration des Kassenbuchs (max. 1 Zeile).
 *
 * Steuert:
 *   - Kassen-Mindestbestand: BelegService verhindert Buchungen, die den Saldo
 *     unter diesen Wert fallen lassen wuerden. Default 0 EUR.
 *   - Ehegattengehalt-Automatik: Monatlicher Scheduler bucht am konfigurierten
 *     Tag den Betrag als Kassenausgabe. Das Buchungskonto ist hart "4120 Loehne
 *     & Gehaelter" (siehe V307) — Ehegattengehalt ist rein buchhalterisch, der
 *     Handwerker soll kein Konto auswaehlen muessen. Keine Kostenstelle.
 *     Wenn der Bar-Saldo nicht reicht, wird vorher automatisch eine Privatein-
 *     lage in genau passender Hoehe gebucht (Privateinlage-Sachkonto unten).
 *
 * letzteBuchungJahrmonat ist die Idempotenz-Sperre: pro YYYY-MM nur einmal
 * buchen, auch wenn der Scheduler mehrfach laeuft.
 */
@Getter
@Setter
@Entity
@Table(name = "kasse_einstellung")
public class KasseEinstellung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal mindestbestand = BigDecimal.ZERO;

    @Column(name = "ehegattengehalt_aktiv", nullable = false)
    private boolean ehegattengehaltAktiv = false;

    @Column(name = "ehegattengehalt_betrag", precision = 10, scale = 2)
    private BigDecimal ehegattengehaltBetrag;

    /**
     * Tag des Monats (1-28). Max. 28, damit jeder Monat denselben Stichtag
     * haben kann.
     */
    @Column(name = "ehegattengehalt_tag")
    private Integer ehegattengehaltTag;

    @Column(name = "ehegattengehalt_empfaenger_name", length = 120)
    private String ehegattengehaltEmpfaengerName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "privateinlage_sachkonto_id")
    private Sachkonto privateinlageSachkonto;

    @Column(name = "letzte_buchung_jahrmonat", length = 7)
    private String letzteBuchungJahrmonat;

    @Column(name = "aktualisiert_am")
    private LocalDateTime aktualisiertAm;

    @PreUpdate
    @PrePersist
    void onSave() {
        aktualisiertAm = LocalDateTime.now();
        if (mindestbestand == null) {
            mindestbestand = BigDecimal.ZERO;
        }
    }
}
