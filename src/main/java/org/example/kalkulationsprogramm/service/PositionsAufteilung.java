package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.PositionsArt;

/**
 * Rechnet eine Aufteilung nach Positionen in Beträge je Projekt bzw.
 * Kostenstelle um.
 *
 * <p>Regeln:
 * <ul>
 * <li>Jede Warenposition gehört genau einem Ziel.</li>
 * <li>Der Anteil eines Ziels ist sein Warenwert geteilt durch den gesamten
 * Warenwert. Damit landen Nebenkosten (Fracht, Zuschläge), Rabatte und eine
 * Abweichung zwischen Positionssumme und Belegbetrag automatisch anteilig bei
 * den Zielen.</li>
 * <li>Die Anteile werden auf den Belegbetrag angewendet: Projekte brutto,
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

    /** Eine Position mit ihrem gewählten Ziel ({@code null} = nicht zugeordnet). */
    record Eingabe(PositionsArt art, BigDecimal gesamtpreisNetto, Ziel ziel) {
    }

    /**
     * @param anteil       Anteil am Warenwert (0..1)
     * @param betragNetto  Anteil am Nettobetrag des Belegs
     * @param betragBrutto Anteil am Bruttobetrag des Belegs
     * @param betrag       der verbuchte Betrag: brutto für Projekte, netto für Kostenstellen
     */
    record ZielBetrag(Ziel ziel, BigDecimal warenwert, BigDecimal anteil, BigDecimal betragNetto,
            BigDecimal betragBrutto, BigDecimal betrag) {
    }

    /**
     * @param nichtZugeordnet Warenpositionen ohne Ziel
     * @param nebenkosten     Summe der Nebenkosten- und Rabattzeilen
     * @param abweichung      Belegnetto minus Summe aller Positionen ({@code null} ohne Belegnetto)
     */
    record Ergebnis(List<ZielBetrag> ziele, int nichtZugeordnet, BigDecimal warenwert, BigDecimal nebenkosten,
            BigDecimal abweichung) {
    }

    static Ergebnis berechne(List<Eingabe> positionen, BigDecimal belegNetto, BigDecimal belegBrutto) {
        Map<Ziel, BigDecimal> warenwerte = new LinkedHashMap<>();
        BigDecimal warenwert = BigDecimal.ZERO;
        BigDecimal nebenkosten = BigDecimal.ZERO;
        int nichtZugeordnet = 0;
        for (Eingabe p : positionen) {
            BigDecimal wert = p.gesamtpreisNetto() != null ? p.gesamtpreisNetto() : BigDecimal.ZERO;
            if (p.art() != null && p.art() != PositionsArt.WARE) {
                nebenkosten = nebenkosten.add(wert);
                continue;
            }
            if (p.ziel() == null) {
                nichtZugeordnet++;
                continue;
            }
            warenwerte.merge(p.ziel(), wert, BigDecimal::add);
            warenwert = warenwert.add(wert);
        }

        BigDecimal summeAlle = warenwert.add(nebenkosten);
        BigDecimal netto = belegNetto != null ? belegNetto : summeAlle;
        BigDecimal brutto = belegBrutto != null ? belegBrutto : netto;
        BigDecimal abweichung = belegNetto != null ? belegNetto.subtract(summeAlle) : null;

        if (warenwerte.isEmpty() || warenwert.signum() == 0) {
            List<ZielBetrag> leer = warenwerte.keySet().stream()
                    .map(z -> new ZielBetrag(z, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null))
                    .toList();
            return new Ergebnis(leer, nichtZugeordnet, warenwert, nebenkosten, abweichung);
        }

        List<Ziel> zielListe = new ArrayList<>(warenwerte.keySet());
        List<BigDecimal> anteile = new ArrayList<>();
        for (Ziel z : zielListe) {
            anteile.add(warenwerte.get(z).divide(warenwert, GENAU));
        }
        List<BigDecimal> nettoBetraege = verteile(netto, anteile);
        List<BigDecimal> bruttoBetraege = verteile(brutto, anteile);

        List<ZielBetrag> ergebnis = new ArrayList<>();
        for (int i = 0; i < zielListe.size(); i++) {
            Ziel z = zielListe.get(i);
            BigDecimal betrag = z.istKostenstelle() ? nettoBetraege.get(i) : bruttoBetraege.get(i);
            ergebnis.add(new ZielBetrag(z, warenwerte.get(z), anteile.get(i).setScale(4, RoundingMode.HALF_UP),
                    nettoBetraege.get(i), bruttoBetraege.get(i), betrag));
        }
        return new Ergebnis(ergebnis, nichtZugeordnet, warenwert, nebenkosten, abweichung);
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
