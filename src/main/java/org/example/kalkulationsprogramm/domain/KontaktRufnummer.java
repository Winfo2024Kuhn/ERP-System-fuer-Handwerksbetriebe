package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Zusätzliche Rufnummer eines Kunden oder Lieferanten ("Weitere Rufnummern"),
 * z.B. beim Zuordnen eines Anrufs gemerkt. Genau einer von kunde/lieferant ist gesetzt.
 */
@Getter
@Setter
@Entity
@Table(name = "kontakt_rufnummer")
public class KontaktRufnummer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kunde_id")
    private Kunde kunde;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id")
    private Lieferanten lieferant;

    @Column(name = "nummer_roh", nullable = false, length = 40)
    private String nummerRoh;

    @Column(name = "nummer_normalisiert", nullable = false, length = 40)
    private String nummerNormalisiert;

    @Column(name = "angelegt_am", nullable = false)
    private LocalDateTime angelegtAm;
}
