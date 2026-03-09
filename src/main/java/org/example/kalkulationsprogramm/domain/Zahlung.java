package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entity für Zahlungen zu Geschäftsdokumenten.
 * Ermöglicht Tracking von Teilzahlungen/Abschlägen.
 */
@Entity
@Table(name = "zahlung")
@Getter
@Setter
public class Zahlung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "geschaeftsdokument_id", nullable = false)
    private Geschaeftsdokument geschaeftsdokument;

    @Column(nullable = false)
    private LocalDate zahlungsdatum;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal betrag;

    @Column(length = 50)
    private String zahlungsart; // Überweisung, Bar, PayPal, etc.

    @Column(length = 255)
    private String verwendungszweck;

    @Column(length = 500)
    private String notiz;
}
