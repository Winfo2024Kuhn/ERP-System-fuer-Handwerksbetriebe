package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Singleton-Tabelle (eine einzige Zeile mit id=1) für die atomare Vergabe
 * fortlaufender Kundennummern. Wird per PESSIMISTIC_WRITE-Lock gelesen, damit
 * konkurrierende Inserts serialisiert werden.
 */
@Getter
@Setter
@Entity
@Table(name = "kunden_zaehler")
public class KundenZaehler {

    @Id
    private Integer id;

    @Column(name = "naechste_nummer", nullable = false)
    private Long naechsteNummer;
}
