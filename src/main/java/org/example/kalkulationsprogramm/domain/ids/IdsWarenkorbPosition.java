package org.example.kalkulationsprogramm.domain.ids;

import java.math.BigDecimal;

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

/** Ein Artikel aus einem zurückgeschickten Shop-Warenkorb, so wie der Shop ihn geliefert hat. */
@Getter
@Setter
@Entity
@Table(name = "ids_warenkorb_position")
public class IdsWarenkorbPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vorgang_id", nullable = false)
    private IdsVorgang vorgang;

    /** Laufende Nummer im Warenkorb ab 0; damit verweist die Oberfläche auf eine Zeile. */
    @Column(name = "position_index", nullable = false)
    private int positionIndex;

    /** Artikelnummer des Lieferanten. */
    @Column(name = "artikelnummer", nullable = false, length = 100)
    private String artikelnummer;

    @Column(name = "bezeichnung", nullable = false, length = 255)
    private String bezeichnung;

    @Column(name = "langtext", length = 2000)
    private String langtext;

    @Column(name = "menge", nullable = false, precision = 15, scale = 3)
    private BigDecimal menge;

    /** Schon in deutscher Kurzform (Stk, m, kg …). */
    @Column(name = "einheit", nullable = false, length = 20)
    private String einheit;

    /** Netto-Einkaufspreis je einer Einheit; {@code null}, wenn der Shop keinen Preis geschickt hat. */
    @Column(name = "einkaufspreis", precision = 19, scale = 4)
    private BigDecimal einkaufspreis;

    /** Hinweis oder Fehlertext des Shops zu dieser Zeile; muss dem Nutzer gezeigt werden. */
    @Column(name = "hinweis", length = 1000)
    private String hinweis;

    @Column(name = "ean", length = 20)
    private String ean;
}
