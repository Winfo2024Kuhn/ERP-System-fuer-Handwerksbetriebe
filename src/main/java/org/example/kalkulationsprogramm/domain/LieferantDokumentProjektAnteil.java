package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Zuordnung eines Lieferanten-Dokuments zu einem Projekt ODER einer Kostenstelle.
 * Ermöglicht die Aufteilung einer Rechnung auf mehrere Ziele:
 * - Projekte (z.B. 60% Projekt A, 40% Projekt B)
 * - Kostenstellen (z.B. Lager, Gemeinkosten)
 * 
 * Unterstützt Kostenstreckung für periodische Kosten (z.B. Zertifizierung alle 4 Jahre).
 */
@Getter
@Setter
@Entity
@Table(name = "lieferant_dokument_projekt_anteil")
public class LieferantDokumentProjektAnteil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dokument_id", nullable = false)
    private LieferantDokument dokument;

    /**
     * Projekt-Zuordnung (optional).
     * Entweder Projekt ODER Kostenstelle muss gesetzt sein.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projekt_id")
    private Projekt projekt;

    /**
     * Kostenstellen-Zuordnung (optional).
     * Entweder Projekt ODER Kostenstelle muss gesetzt sein.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kostenstelle_id")
    private Kostenstelle kostenstelle;

    /**
     * Prozentuale Zuordnung (0-100).
     * Wird verwendet wenn absoluterBetrag null ist.
     */
    private Integer prozent;

    /**
     * Absoluter Betrag statt Prozent.
     * Wenn gesetzt, wird dieser Betrag verwendet statt Prozent.
     */
    @Column(precision = 12, scale = 2)
    private BigDecimal absoluterBetrag;

    /**
     * Automatisch berechnet: (betragBrutto * prozent / 100) oder absoluterBetrag.
     * Wird beim Speichern aktualisiert.
     */
    @Column(precision = 12, scale = 2)
    private BigDecimal berechneterBetrag;

    @Column(length = 255)
    private String beschreibung; // Optional: Kostenart/Beschreibung

    /**
     * Zeitpunkt der Zuordnung.
     * Nullable für bestehende Datensätze - neue Einträge bekommen via @PrePersist automatisch einen Wert.
     */
    @Column(nullable = true)
    private java.time.LocalDateTime zugeordnetAm;

    /**
     * Welcher Frontend-User hat diese Zuordnung vorgenommen?
     * Nullable für bestehende Datensätze.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zugeordnet_von_user_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private FrontendUserProfile zugeordnetVon;

    @PrePersist
    protected void onCreate() {
        if (zugeordnetAm == null) {
            zugeordnetAm = java.time.LocalDateTime.now();
        }
    }

    // === KOSTENSTRECKUNG für periodische Gemeinkosten ===

    /**
     * Über wie viele Jahre sollen die Kosten verteilt werden?
     * Default: 1 = keine Streckung.
     * Beispiel: Zertifizierung alle 4 Jahre -> streckungJahre = 4
     */
    @Column(nullable = false)
    private Integer streckungJahre = 1;

    /**
     * Ab welchem Jahr gilt die Streckung?
     * Normalerweise das Buchungsjahr der Rechnung.
     */
    private Integer streckungStartJahr;

    /**
     * Berechnet den Anteil basierend auf der Zuordnungs-Art.
     * <p>
     * REGEL: Kostenstellen werden IMMER netto verrechnet — der Vorsteuerabzug
     * landet beim Finanzamt, nicht beim Gemeinkostentopf. Nur Projekt-Anteile
     * werden brutto angesetzt, weil dort die Weiterverrechnung an den Kunden
     * inklusive MwSt erfolgt.
     * <p>
     * Wenn netto null ist (z.B. weil die KI den Nettobetrag nicht extrahieren
     * konnte), fallen wir auf brutto zurueck — besser ein leichter Overhead in
     * den Gemeinkosten als ein Anteil von 0,00 EUR.
     *
     * @param nettoBetrag  Nettobetrag des Dokuments (fuer Kostenstellen-Zuordnung)
     * @param bruttoBetrag Bruttobetrag des Dokuments (fuer Projekt-Zuordnung)
     */
    public void berechneAnteil(BigDecimal nettoBetrag, BigDecimal bruttoBetrag) {
        if (absoluterBetrag != null) {
            this.berechneterBetrag = absoluterBetrag;
            return;
        }
        if (prozent == null) {
            return;
        }
        BigDecimal basis = isKostenstellenZuordnung()
                ? (nettoBetrag != null ? nettoBetrag : bruttoBetrag)
                : bruttoBetrag;
        if (basis == null) {
            return;
        }
        this.berechneterBetrag = basis
                .multiply(BigDecimal.valueOf(prozent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
     * Rueckwaerts-kompatible Variante fuer Aufrufer, die nur einen Betrag haben.
     * Verwendet diesen Wert sowohl als Netto wie als Brutto — was bei reinen
     * Projekt-Zuordnungen das alte Verhalten exakt erhaelt, bei Kostenstellen-
     * Zuordnungen aber leicht zu hoch ist. Neue Aufrufer sollten die
     * (netto, brutto)-Variante verwenden.
     *
     * @deprecated Use {@link #berechneAnteil(BigDecimal, BigDecimal)} instead.
     */
    @Deprecated
    public void berechneAnteil(BigDecimal gesamtBetrag) {
        berechneAnteil(gesamtBetrag, gesamtBetrag);
    }

    /**
     * Berechnet den anteiligen Jahresbetrag bei Kostenstreckung.
     * Beispiel: 4.000€ über 4 Jahre = 1.000€ pro Jahr.
     *
     * @return Jahresanteil oder voller Betrag wenn keine Streckung
     */
    @Transient
    public BigDecimal getJahresanteil() {
        if (berechneterBetrag == null) {
            return BigDecimal.ZERO;
        }
        if (streckungJahre == null || streckungJahre <= 1) {
            return berechneterBetrag;
        }
        return berechneterBetrag.divide(
                BigDecimal.valueOf(streckungJahre), 2, RoundingMode.HALF_UP);
    }

    /**
     * Prüft ob die Kostenstreckung für das gegebene Jahr aktiv ist.
     *
     * @param jahr Das zu prüfende Jahr
     * @return true wenn die Kosten in diesem Jahr anteilig berücksichtigt werden sollen
     */
    @Transient
    public boolean isStreckungAktivFuerJahr(int jahr) {
        if (streckungStartJahr == null || streckungJahre == null || streckungJahre <= 1) {
            return streckungStartJahr == null || streckungStartJahr == jahr;
        }
        return jahr >= streckungStartJahr && jahr < streckungStartJahr + streckungJahre;
    }

    /**
     * @return true wenn Kostenstelle statt Projekt zugeordnet ist
     */
    @Transient
    public boolean isKostenstellenZuordnung() {
        return kostenstelle != null;
    }
}

