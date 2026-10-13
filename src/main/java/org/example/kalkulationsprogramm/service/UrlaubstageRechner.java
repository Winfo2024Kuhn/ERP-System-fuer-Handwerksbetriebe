package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.example.kalkulationsprogramm.domain.Abwesenheit;
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.springframework.stereotype.Component;

/**
 * Zählt Urlaubstage aus gebuchten Abwesenheiten – inklusive halber Tage.
 *
 * <p>Ein halber Urlaubstag wird mit der Hälfte des Tagessolls gebucht
 * ({@link AbwesenheitService#bucheAbwesenheit}). Beim Zählen gilt deshalb:
 * höchstens die Hälfte des Solls gebucht → 0,5 Tage, sonst 1 Tag. Ohne
 * bekanntes Soll (z. B. Geschäftsführer mit 0-Stunden-Einträgen) zählt jeder
 * Eintrag als ganzer Tag – wie bisher.
 *
 * <p>Baustein des Urlaubskontos ({@link UrlaubskontoService}), das Saldo,
 * Antragsprüfung und Verfall-Hinweis gemeinsam nutzen (Issue #269, Punkt 5;
 * Issue #270, Punkt 4).
 */
@Component
public class UrlaubstageRechner {

    static final BigDecimal GANZER_TAG = BigDecimal.ONE;
    static final BigDecimal HALBER_TAG = new BigDecimal("0.5");

    /** Anteil eines einzelnen Urlaubseintrags am Urlaubskonto: 0,5 oder 1. */
    public BigDecimal anteil(Abwesenheit abwesenheit, BigDecimal tagesSoll) {
        BigDecimal stunden = abwesenheit.getStunden();
        if (stunden == null || tagesSoll == null || tagesSoll.signum() <= 0) {
            return GANZER_TAG;
        }
        BigDecimal halbesSoll = tagesSoll.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        return stunden.compareTo(halbesSoll) <= 0 ? HALBER_TAG : GANZER_TAG;
    }

    /**
     * Summe der Urlaubstage über alle Einträge vom Typ URLAUB. Andere Typen in
     * der Liste werden ignoriert, damit Aufrufer die Jahresliste ungefiltert
     * übergeben können.
     *
     * @param sollJeTag Tagessoll je Datum (Zeitkonto), fehlende Tage zählen als
     *                  ganzer Tag
     */
    public BigDecimal urlaubstage(Collection<Abwesenheit> abwesenheiten, Map<LocalDate, BigDecimal> sollJeTag) {
        BigDecimal summe = BigDecimal.ZERO;
        for (Abwesenheit a : abwesenheiten) {
            if (a.getTyp() != AbwesenheitsTyp.URLAUB) continue;
            BigDecimal soll = sollJeTag == null ? null : sollJeTag.get(a.getDatum());
            summe = summe.add(anteil(a, soll));
        }
        return summe;
    }

    /**
     * Wie {@link #urlaubstage(Collection, Map)}, lädt das Tagessoll aber nur, wenn
     * Urlaub gebucht ist – und nur für den Zeitraum vom ersten bis zum letzten
     * Urlaubstag. So kostet ein Saldo ohne Urlaub keine Soll-Berechnung.
     *
     * @param sollLader (von, bis) → Tagessoll je Datum, z. B. {@code TagesSollService::arbeitsSollJeTag}
     */
    public BigDecimal urlaubstage(Collection<Abwesenheit> abwesenheiten,
            BiFunction<LocalDate, LocalDate, Map<LocalDate, BigDecimal>> sollLader) {
        List<Abwesenheit> urlaub = nurUrlaub(abwesenheiten);
        return urlaub.isEmpty() ? BigDecimal.ZERO : urlaubstage(urlaub, sollFuerUrlaub(urlaub, sollLader));
    }

    /**
     * Tagessoll für den Zeitraum vom ersten bis zum letzten Urlaubstag – leer und
     * ohne Aufruf des Laders, wenn kein Urlaub gebucht ist. Für Aufrufer, die
     * mehrere Teilmengen (z. B. genommen und geplant) mit demselben Soll zählen.
     */
    public Map<LocalDate, BigDecimal> sollFuerUrlaub(Collection<Abwesenheit> abwesenheiten,
            BiFunction<LocalDate, LocalDate, Map<LocalDate, BigDecimal>> sollLader) {
        List<Abwesenheit> urlaub = nurUrlaub(abwesenheiten);
        if (urlaub.isEmpty()) {
            return Map.of();
        }
        LocalDate von = urlaub.stream().map(Abwesenheit::getDatum).min(LocalDate::compareTo).orElseThrow();
        LocalDate bis = urlaub.stream().map(Abwesenheit::getDatum).max(LocalDate::compareTo).orElseThrow();
        return sollLader.apply(von, bis);
    }

    private static List<Abwesenheit> nurUrlaub(Collection<Abwesenheit> abwesenheiten) {
        return abwesenheiten.stream()
                .filter(a -> a.getTyp() == AbwesenheitsTyp.URLAUB && a.getDatum() != null)
                .toList();
    }

    /**
     * Tagessoll-Lader für {@link #urlaubstage(Collection, BiFunction)}. Die
     * Geschäftsführung führt kein Soll (ihre Abwesenheiten stehen mit 0 Stunden
     * im Kalender) – für sie liefert der Lader nichts, jeder Urlaubseintrag zählt
     * dann als ganzer Tag. Gemeinsam genutzt von Saldo und Antragsprüfung, damit
     * beide dieselbe Zahl liefern.
     */
    public static BiFunction<LocalDate, LocalDate, Map<LocalDate, BigDecimal>> sollLader(Mitarbeiter mitarbeiter,
            TagesSollService tagesSollService) {
        if (Boolean.TRUE.equals(mitarbeiter.getIstGeschaeftsfuehrer())) {
            return (von, bis) -> Map.of();
        }
        Long mitarbeiterId = mitarbeiter.getId();
        return (von, bis) -> tagesSollService.arbeitsSollJeTag(mitarbeiterId, von, bis);
    }

    /**
     * Korrekturen (als Tage im Feld {@code stunden}) ohne Abschneiden: 0,5 bleibt
     * 0,5. {@code null} wird zu 0.
     */
    public BigDecimal korrekturTage(BigDecimal korrektur) {
        return korrektur == null ? BigDecimal.ZERO : korrektur;
    }

    /** „12,5" bzw. „12" – für Meldungen an die Nutzer. */
    public static String formatTage(BigDecimal tage) {
        BigDecimal gekuerzt = tage.stripTrailingZeros();
        if (gekuerzt.scale() <= 0) {
            return gekuerzt.toPlainString();
        }
        return gekuerzt.setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
