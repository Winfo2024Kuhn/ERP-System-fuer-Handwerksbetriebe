package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
public class ArtikelInProjekt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projekt_id")
    private Projekt projekt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artikel_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Artikel artikel;

    @Column
    private Integer stueckzahl;

    @Column(precision = 19, scale = 2)
    private BigDecimal meter;

    @Column(precision = 19, scale = 2)
    private BigDecimal kilogramm;

    @Column(precision = 19, scale = 2)
    private BigDecimal preisProStueck;

    @Column(nullable = false)
    private LocalDate hinzugefuegtAm;

    @Column(nullable = false)
    private boolean bestellt = false;

    String anschnittWinkelLinks;
    String anschnittWinkelRechts;
    String schnittForm;
    String kommentar;

    private LocalDate bestelltAm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id")
    private Lieferanten lieferant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "artikel_id", referencedColumnName = "artikel_id", insertable = false, updatable = false),
            @JoinColumn(name = "lieferant_id", referencedColumnName = "lieferant_id", insertable = false, updatable = false)
    })
    private LieferantenArtikelPreise lieferantenArtikelPreis;
}
