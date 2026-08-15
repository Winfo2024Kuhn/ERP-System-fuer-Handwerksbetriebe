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

    /**
     * Preis je Verrechnungseinheit bzw. eingefrorene Gesamtsumme - siehe
     * {@link org.example.kalkulationsprogramm.mapper.ProjektMapper} fuer die
     * beiden Bedeutungen dieses Felds.
     *
     * <p>Seit Migration V361 auf vier Nachkommastellen (vorher zwei), analog zu
     * V359 fuer {@code LieferantenArtikelPreise.preis}: Kleinteile wie Schrauben
     * kosten teils nur 0,0183 EUR je Stueck, und mit zwei Nachkommastellen
     * rundete die Datenbank das auf 0,02 EUR - 9 % Aufschlag auf jede
     * Kalkulation mit solchen Teilen.
     */
    @Column(precision = 19, scale = 4)
    private BigDecimal preisProStueck;

    @Column(nullable = false)
    private LocalDate hinzugefuegtAm;

    /**
     * Steuert die offene Bestellliste: false heisst "muss noch bestellt
     * werden" ({@code findByBestelltFalse}), true heisst erledigt - entweder
     * weil der Einkaeufer die Bestellung abgehakt hat oder weil die Ware aus
     * dem eigenen Lager kam.
     */
    @Column(nullable = false)
    private boolean bestellt = false;

    /**
     * Kam die Ware aus dem eigenen Lager statt von einer Bestellung?
     *
     * Nur diese Positionen zaehlen sofort als Materialkosten des Projekts.
     * Bestellte Ware kommt spaeter ueber die Lieferantenrechnung herein und
     * wuerde sonst doppelt in der Nachkalkulation stehen.
     */
    @Column(nullable = false)
    private boolean ausLager = false;

    String anschnittWinkelLinks;
    String anschnittWinkelRechts;
    String schnittForm;
    String kommentar;

    private LocalDate bestelltAm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id")
    private Lieferanten lieferant;

    /**
     * Der Preisstand, mit dem diese Position kalkuliert wurde.
     *
     * <p>Zeigt seit Migration V339 auf einen konkreten Eintrag der Preishistorie
     * statt auf die Kombination aus Artikel und Lieferant. Damit bleibt
     * nachvollziehbar, welcher Preis der Kalkulation zugrunde lag - auch wenn der
     * Lieferant seine Preise seitdem geaendert hat.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferanten_artikel_preis_id")
    private LieferantenArtikelPreise lieferantenArtikelPreis;
}
