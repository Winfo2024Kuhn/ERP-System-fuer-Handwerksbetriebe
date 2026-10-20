package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Nachweis einer Löschung bzw. Sperre aus Datenschutzgründen (Rechenschaftspflicht,
 * Art. 5 Abs. 2 DSGVO): was, wann, durch wen, warum. Enthält bewusst keine Kundendaten außer
 * Kunden-ID und Kundennummer und keinen Fremdschlüssel – der Kunde kann danach fehlen.
 */
@Getter
@Setter
@Entity
@Table(name = "kunde_loeschprotokoll")
public class KundeLoeschprotokoll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kunde_id", nullable = false)
    private Long kundeId;

    private String kundennummer;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private KundeLoeschAktion aktion;

    @Column(length = 500)
    private String grund;

    @Column(name = "aufbewahren_bis")
    private LocalDate aufbewahrenBis;

    /** Benutzername des Admins. */
    @Column(name = "ausgefuehrt_von")
    private String ausgefuehrtVon;

    @Column(name = "ausgefuehrt_am", nullable = false)
    private LocalDateTime ausgefuehrtAm;
}
