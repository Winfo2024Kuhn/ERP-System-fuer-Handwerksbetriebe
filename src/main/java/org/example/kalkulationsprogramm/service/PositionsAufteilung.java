package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.PositionsArt;

/**
 * Rechnet eine Aufteilung nach Positionen in Beträge je Projekt bzw.
 * Kostenstelle um.
 *
 * <p>Regeln:
 * <ul>
 * <li>Jede Warenposition gehört genau einem Ziel.</li>
 * <li>Gehört eine Nebenkosten- oder Rabattzeile zu bestimmten Warenpositionen
 * („Legierungszuschlag zu Pos. 3“), landet sie nur bei deren Zielen – bei
 * mehreren nach deren Warenwert (Issue #242). Haben diese Positionen kein Ziel
 * oder keinen Warenwert, zählt die Zeile wie eine ohne Bezug.</li>
 * <li>Alles Übrige – Nebenkosten ohne Bezug, Rabatte und eine Abweichung
 * zwischen Positionssumme und Belegbetrag – verteilt sich nach dem Anteil eines
 * Ziels am gesamten Warenwert.</li>
 * <li>Daraus ergibt sich der Anteil jedes Ziels am Belegbetrag: Projekte brutto,
 * Kostenstellen netto (wie bei der Prozent-Aufteilung, siehe
 * {@code LieferantDokumentProjektAnteil#berechneAnteil}).</li>
 * <li>Rundungsreste gehen an das größte Ziel – die Summe geht immer exakt auf.</li>
 * </ul>
 */
final class PositionsAufteilung {

    private static final MathContext GENAU = MathContext.DECIMAL64;

    private PositionsAufteilung() {
    }

    /** Projekt ODER Kostenstelle. */
    record Ziel(Long projektId, Long kostenstelleId) {
        boolean istKostenstelle() {
            return kostenstelleId != null;
        }

        boolean gueltig() {
            return (projektId == null) != (kostenstelleId == null);
        }
    }

    /**
     * Eine Position mit ihrem gewählten Ziel ({@code null} = nicht zugeordnet).
     *
     * @param bezug nur bei Nebenkosten/Rabatten: IDs der Warenpositionen, zu denen die Zeile
     *              gehört; leer = gilt für alle
     */
    record Eingabe(Long id, PositionsArt art, BigDecimal gesamtpreisNetto, Ziel ziel, Set<Long> bezug) {
        Eingabe {
            bezug = bezug == null ? Set.of() : Set.copyOf(bezug);
        }

        Eingabe(PositionsArt art, BigDecimal gesamtpreisNetto, Ziel ziel) {
            this(null, art, gesamtpreisNetto, ziel, Set.of());
        }

        boolean istWare() {
            return art == null || art == PositionsArt.WARE;
        }

        BigDecimal wert() {
            return gesamtpreisNetto != null ? gesamtpreisNetto : BigDecimal.ZERO;
        }
    }

    /**
     * @param anteil              Anteil am Warenwert (0..1)
     * @param betragNetto         Anteil am Nettobetrag des Belegs
     * @param betragBrutto        Anteil am Bruttobetrag des Belegs
     * @param betrag              der verbuchte Betrag: brutto für Projekte, netto für Kostenstellen
     * @param nebenkostenMitBezug Nebenkosten/Rabatte netto, die über ihren Bezug bei diesem Ziel landen
     */
    record ZielBetrag(Ziel ziel, BigDecimal warenwert, BigDecimal anteil, BigDecimal betragNetto,
            BigDecimal betragBrutto, BigDecimal betrag, BigDecimal nebenkostenMitBezug) {
    }

    /**
     * @param nichtZugeordnet     Warenpositionen ohne Ziel
     * @param nebenkosten         Summe aller Nebenkosten- und Rabattzeilen
     * @param nebenkostenMitBezug davon die Zeilen, die nur bei den Zielen ihrer Positionen landen
     * @param abweichung          Belegnetto minus Summe aller Positionen ({@code null} ohne Belegnetto)
     */
    record Ergebnis(List<ZielBetrag> ziele, int nichtZugeordnet, BigDecimal warenwert, BigDecimal nebenkosten,
            BigDecimal nebenkostenMitBezug, BigDecimal abweichung) {
    }

    static Ergebnis berechne(List<Eingabe> positionen, BigDecimal belegNetto, BigDecimal belegBrutto) {
        Map<Ziel, BigDecimal> warenwerte = new LinkedHashMap<>();
        Map<Long, Eingabe> zugeordneteWare = new HashMap<>();
        List<Eingabe> nebenkostenZeilen = new ArrayList<>();
        BigDecimal warenwert = BigDecimal.ZERO;
        BigDecimal nebenkosten = BigDecimal.ZERO;
        int nichtZugeordnet = 0;
        for (Eingabe p : positionen) {
            if (!p.istWare()) {
                nebenkosten = nebenkosten.add(p.wert());
                nebenkostenZeilen.add(p);
                continue;
            }
            if (p.ziel() == null) {
                nichtZugeordnet++;
                continue;
            }
            warenwerte.merge(p.ziel(), p.wert(), BigDecimal::add);
            warenwert = warenwert.add(p.wert());
            if (p.id() != null) {
                zugeordneteWare.put(p.id(), p);
            }
        }

        // Nebenkosten mit Bezug landen nur bei den Zielen ihrer Positionen.
        Map<Ziel, BigDecimal> gebunden = new HashMap<>();
        BigDecimal nebenkostenMitBezug = BigDecimal.ZERO;
        for (Eingabe zeile : nebenkostenZeilen) {
            Map<Ziel, BigDecimal> basis = warenwertJeZiel(zeile.bezug(), zugeordneteWare);
            BigDecimal basisSumme = basis.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (basisSumme.signum() == 0) {
                continue;
            }
            nebenkostenMitBezug = nebenkostenMitBezug.add(zeile.wert());
            basis.forEach((ziel, wert) -> gebunden.merge(ziel,
                    zeile.wert().multiply(wert, GENAU).divide(basisSumme, GENAU), BigDecimal::add));
        }

        BigDecimal summeAlle = warenwert.add(nebenkosten);
        BigDecimal netto = belegNetto != null ? belegNetto : summeAlle;
        BigDecimal brutto = belegBrutto != null ? belegBrutto : netto;
        BigDecimal abweichung = belegNetto != null ? belegNetto.subtract(summeAlle) : null;

        if (warenwerte.isEmpty() || warenwert.signum() == 0) {
            List<ZielBetrag> leer = warenwerte.keySet().stream()
                    .map(z -> new ZielBetrag(z, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, BigDecimal.ZERO))
                    .toList();
            return new Ergebnis(leer, nichtZugeordnet, warenwert, nebenkosten, BigDecimal.ZERO, abweichung);
        }

        // Rest (Nebenkosten ohne Bezug, Rabatte, Abweichung) nach Warenwert; daraus der Anteil am Beleg.
        BigDecimal rest = netto.subtract(warenwert).subtract(nebenkostenMitBezug);
        List<Ziel> zielListe = new ArrayList<>(warenwerte.keySet());
        List<BigDecimal> warenAnteile = new ArrayList<>();
        List<BigDecimal> belegAnteile = new ArrayList<>();
        for (Ziel z : zielListe) {
            BigDecimal warenAnteil = warenwerte.get(z).divide(warenwert, GENAU);
            warenAnteile.add(warenAnteil);
            BigDecimal nettoJeZiel = warenwerte.get(z)
                    .add(gebunden.getOrDefault(z, BigDecimal.ZERO))
                    .add(rest.multiply(warenAnteil, GENAU));
            belegAnteile.add(netto.signum() != 0 ? nettoJeZiel.divide(netto, GENAU) : warenAnteil);
        }
        List<BigDecimal> nettoBetraege = verteile(netto, belegAnteile);
        List<BigDecimal> bruttoBetraege = verteile(brutto, belegAnteile);

        List<ZielBetrag> ergebnis = new ArrayList<>();
        for (int i = 0; i < zielListe.size(); i++) {
            Ziel z = zielListe.get(i);
            BigDecimal betrag = z.istKostenstelle() ? nettoBetraege.get(i) : bruttoBetraege.get(i);
            ergebnis.add(new ZielBetrag(z, warenwerte.get(z), warenAnteile.get(i).setScale(4, RoundingMode.HALF_UP),
                    nettoBetraege.get(i), bruttoBetraege.get(i), betrag,
                    gebunden.getOrDefault(z, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)));
        }
        return new Ergebnis(ergebnis, nichtZugeordnet, warenwert, nebenkosten, nebenkostenMitBezug, abweichung);
    }

    /** Warenwert der Bezugspositionen je Ziel; Positionen ohne Ziel zählen nicht. */
    private static Map<Ziel, BigDecimal> warenwertJeZiel(Set<Long> bezug, Map<Long, Eingabe> zugeordneteWare) {
        Map<Ziel, BigDecimal> basis = new LinkedHashMap<>();
        for (Long id : bezug) {
            Eingabe ware = zugeordneteWare.get(id);
            if (ware != null) {
                basis.merge(ware.ziel(), ware.wert(), BigDecimal::add);
            }
        }
        return basis;
    }

    /** Verteilt einen Betrag nach Anteilen auf Cent; der Rest geht an den größten Anteil. */
    static List<BigDecimal> verteile(BigDecimal gesamt, List<BigDecimal> anteile) {
        List<BigDecimal> betraege = new ArrayList<>();
        BigDecimal summe = BigDecimal.ZERO;
        int groesster = 0;
        for (int i = 0; i < anteile.size(); i++) {
            BigDecimal betrag = gesamt.multiply(anteile.get(i), GENAU).setScale(2, RoundingMode.HALF_UP);
            betraege.add(betrag);
            summe = summe.add(betrag);
            if (anteile.get(i).abs().compareTo(anteile.get(groesster).abs()) > 0) {
                groesster = i;
            }
        }
        BigDecimal rest = gesamt.setScale(2, RoundingMode.HALF_UP).subtract(summe);
        if (rest.signum() != 0 && !betraege.isEmpty()) {
            betraege.set(groesster, betraege.get(groesster).add(rest));
        }
        return betraege;
    }
}
