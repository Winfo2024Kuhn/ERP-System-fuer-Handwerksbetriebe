package org.example.kalkulationsprogramm.domain;

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
 * Schweißer-Qualifikationszertifikat nach EN ISO 9606-1 / EN ISO 14732.
 * Bestandteil der EN 1090 EXC 2 Dokumentation.
 */
@Getter
@Setter
@Entity
@Table(name = "schweisser_zertifikat")
public class SchweisserZertifikat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mitarbeiter_id", nullable = false)
    private Mitarbeiter mitarbeiter;

    @Column(nullable = false)
    private String zertifikatsnummer;

    /** z.B. EN ISO 9606-1, EN ISO 14732 */
    @Column(nullable = false)
    private String norm;

    /** Schweißprozess-Nummer z.B. 111 MMA, 135 MAG, 141 WIG */
    @Column(name = "schweiss_prozess", nullable = false)
    private String schweissProzes;

    /** Grundwerkstoff z.B. S355, 1.4301 */
    private String grundwerkstoff;

    /** Name der Prüfstelle / Überwachungsorganisation */
    private String pruefstelle;

    @Column(nullable = false)
    private LocalDate ausstellungsdatum;

    /** NULL = unbegrenzt (z.B. nach Meisterprüfung) */
    private LocalDate ablaufdatum;

    /** UUID-Dateiname des Zertifikat-PDFs */
    @Column(length = 500)
    private String gespeicherterDateiname;

    @Column(length = 500)
    private String originalDateiname;

    @Column(nullable = false)
    private LocalDateTime erstelltAm = LocalDateTime.now();
}
