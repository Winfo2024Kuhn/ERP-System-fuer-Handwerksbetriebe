package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Counter für die Dokumentnummern im Format YYYY/MM/NNNNN.
 * Separater Counter um Konflikte mit Projektnummern zu vermeiden.
 */
@Entity
@Table(name = "ausgangs_geschaeftsdokument_counter", 
       uniqueConstraints = @UniqueConstraint(columnNames = "monat_key"))
@Getter
@Setter
public class AusgangsGeschaeftsDokumentCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Schlüssel im Format "YYYY/MM", z.B. "2025/01"
     */
    @Column(name = "monat_key", nullable = false, length = 10)
    private String monatKey;

    /**
     * Fortlaufender Zähler für diesen Monat
     */
    @Column(nullable = false)
    private Long zaehler = 0L;
}
