package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "artikel_preis_historie")
public class ArtikelPreisHistorie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artikel_id", nullable = false)
    private Artikel artikel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id")
    private Lieferanten lieferant;

    @Column(name = "preis", nullable = false, precision = 12, scale = 4)
    private BigDecimal preis;

    @Column(name = "menge", precision = 18, scale = 3)
    private BigDecimal menge;

    @Enumerated(EnumType.STRING)
    @Column(name = "einheit", nullable = false, length = 32)
    private Verrechnungseinheit einheit;

    @Enumerated(EnumType.STRING)
    @Column(name = "quelle", nullable = false, length = 32)
    private PreisQuelle quelle;

    @Column(name = "externe_nummer", length = 255)
    private String externeNummer;

    @Column(name = "beleg_referenz", length = 255)
    private String belegReferenz;

    @Column(name = "erfasst_am", nullable = false)
    private LocalDateTime erfasstAm;

    @Column(name = "bemerkung", length = 500)
    private String bemerkung;
}
