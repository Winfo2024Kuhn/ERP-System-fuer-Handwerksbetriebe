package org.example.kalkulationsprogramm.service.zeitbericht;

import org.example.kalkulationsprogramm.dto.ZeitkontenmodellDto;
import org.example.kalkulationsprogramm.dto.ZeitkontoVersionDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Rechenregeln der Stundenaufstellung für den Steuerberater, ohne Datenbank.
 */
public final class SteuerberaterStunden {

    private static final BigDecimal FUENF_TAGE = new BigDecimal("5");

    private SteuerberaterStunden() {
    }

    /**
     * Bezahlt wird nach Sollstunden: Arbeitsstunden = Soll − Urlaub − Feiertage − Krankheit −
     * Fortbildung. Überstunden landen im Stundenkonto, nicht im Lohn. Nie unter 0.
     */
    public static BigDecimal arbeitsstunden(BigDecimal soll, BigDecimal urlaub, BigDecimal feiertage,
            BigDecimal krankheit, BigDecimal fortbildung) {
        BigDecimal ergebnis = wert(soll).subtract(wert(urlaub)).subtract(wert(feiertage))
                .subtract(wert(krankheit)).subtract(wert(fortbildung));
        return ergebnis.signum() < 0 ? BigDecimal.ZERO : ergebnis;
    }

    /**
     * Tagessoll wie bisher auf der Steuerberater-Seite: Wochenstunden der am Tag gültigen
     * Zeitkonto-Version geteilt durch fünf, gemittelt über alle Tage des Monats.
     */
    public static BigDecimal tagessoll(List<ZeitkontoVersionDto> versionen, YearMonth monat) {
        BigDecimal summe = BigDecimal.ZERO;
        for (LocalDate tag = monat.atDay(1); !tag.isAfter(monat.atEndOfMonth()); tag = tag.plusDays(1)) {
            summe = summe.add(wochenstundenAm(versionen, tag).divide(FUENF_TAGE, 6, RoundingMode.HALF_UP));
        }
        return runde(summe.divide(BigDecimal.valueOf(monat.lengthOfMonth()), 6, RoundingMode.HALF_UP));
    }

    public static BigDecimal runde(BigDecimal wert) {
        return wert(wert).setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal wochenstundenAm(List<ZeitkontoVersionDto> versionen, LocalDate tag) {
        return versionen.stream()
                .filter(v -> v.gueltigVon() != null && !tag.isBefore(v.gueltigVon()))
                .filter(v -> v.gueltigBis() == null || !tag.isAfter(v.gueltigBis()))
                .map(ZeitkontoVersionDto::arbeitszeit)
                .filter(Objects::nonNull)
                .findFirst()
                .map(SteuerberaterStunden::wochensumme)
                .orElse(BigDecimal.ZERO);
    }

    private static BigDecimal wochensumme(ZeitkontenmodellDto.Arbeitszeit a) {
        return Stream.of(a.montagStunden(), a.dienstagStunden(), a.mittwochStunden(), a.donnerstagStunden(),
                a.freitagStunden(), a.samstagStunden(), a.sonntagStunden())
                .map(SteuerberaterStunden::wert)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal wert(BigDecimal wert) {
        return wert == null ? BigDecimal.ZERO : wert;
    }
}
