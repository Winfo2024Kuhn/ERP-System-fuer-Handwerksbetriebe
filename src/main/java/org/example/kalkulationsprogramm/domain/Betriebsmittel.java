package org.example.kalkulationsprogramm.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Elektrisches Betriebsmittel für E-Check (BGV A3 / DGUV Vorschrift 3).
 * Identifizierbar per Barcode/QR-Code im mobilen Prüfworkflow.
 */
@Getter
@Setter
@Entity
@Table(name = "betriebsmittel")
public class Betriebsmittel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String bezeichnung;

    @Column(unique = true, length = 200)
    private String seriennummer;

    /** Barcode / QR-Code-Wert für mobile Identifikation */
    @Column(unique = true, length = 300)
    private String barcode;

    @Column(length = 200)
    private String hersteller;

    @Column(length = 200)
    private String modell;

    /** Standort z.B. "Lager", "Baustelle Musterstraße 1" */
    @Column(length = 200)
    private String standort;

    @Column(length = 500)
    private String bildDateiname;

    /** Wird nach jeder Prüfung automatisch neu berechnet */
    private LocalDate naechstesPruefDatum;

    /** Prüfintervall in Monaten gemäß DGUV V3 (Standard: 12) */
    @Column(nullable = false)
    private int pruefIntervallMonate = 12;

    @Column(nullable = false)
    private boolean ausserBetrieb = false;

    @Column(nullable = false)
    private LocalDateTime erstelltAm = LocalDateTime.now();
}
