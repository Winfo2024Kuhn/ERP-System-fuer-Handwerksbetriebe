package org.example.kalkulationsprogramm.service.zeitbericht;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository;
import org.example.kalkulationsprogramm.domain.MonatsSaldo;
import org.example.kalkulationsprogramm.dto.ZeitkontoStatusDto;
import org.example.kalkulationsprogramm.dto.ZeitkontoVersionDto;
import org.example.kalkulationsprogramm.dto.zeitbericht.SteuerberaterBerichtDto;
import org.example.kalkulationsprogramm.dto.zeitbericht.SteuerberaterBerichtDto.Stundenart;
import org.example.kalkulationsprogramm.dto.zeitbericht.SteuerberaterBerichtDto.Zeile;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.MonatsSaldoService;
import org.example.kalkulationsprogramm.service.UrlaubskontoService;
import org.example.kalkulationsprogramm.service.ZeitkontoWechselService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stellt die Stundenaufstellung eines Monats für den Steuerberater zusammen: alle Mitarbeiter
 * mit eingerichtetem Zeitkonto, ohne Geschäftsführer (wie bisher die Steuerberater-Seite).
 * Rechnet nichts neu, sondern nimmt den Monatssaldo (gleicher Rechenweg wie der Monatsabschluss),
 * das Stundenkonto und das Urlaubskonto.
 */
@Service
@RequiredArgsConstructor
public class SteuerberaterBerichtService {

    private final ZeitkontoWechselService zeitkontoWechselService;
    private final MonatsSaldoService monatsSaldoService;
    private final UrlaubskontoService urlaubskontoService;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final AbwesenheitRepository abwesenheitRepository;
    private final SteuerberaterStundenartEinstellung stundenartEinstellung;

    @Transactional(readOnly = true)
    public SteuerberaterBerichtDto erstelle(int jahr, int monat, List<Long> mitarbeiterIds, Stundenart gewaehlt) {
        Stundenart stundenart = gewaehlt == null ? stundenartEinstellung.oderStandard() : gewaehlt;
        BerichtEingaben.pruefeMonat(jahr, monat);
        List<Long> auswahl = BerichtEingaben.mitarbeiterIds(mitarbeiterIds, false);
        YearMonth ym = YearMonth.of(jahr, monat);

        List<ZeitkontoStatusDto> konten = zeitkontoWechselService.alle().stream()
                .filter(k -> k.fuehrtZeitkonto() && k.aktuell() != null && !k.istGeschaeftsfuehrer())
                .filter(k -> auswahl.isEmpty() || auswahl.contains(k.mitarbeiterId()))
                .toList();
        Map<Long, Mitarbeiter> mitarbeiter = mitarbeiterRepository
                .findAllById(konten.stream().map(ZeitkontoStatusDto::mitarbeiterId).toList()).stream()
                .collect(Collectors.toMap(Mitarbeiter::getId, Function.identity()));

        Map<Long, Map<AbwesenheitsTyp, BigDecimal>> abwesenheiten = abwesenheitenJeMitarbeiter(konten, ym);

        List<Zeile> zeilen = new ArrayList<>();
        for (ZeitkontoStatusDto konto : konten) {
            zeilen.add(zeile(konto, mitarbeiter.get(konto.mitarbeiterId()), ym,
                    abwesenheiten.getOrDefault(konto.mitarbeiterId(), Map.of()), stundenart));
        }
        boolean alleFest = zeilen.stream().allMatch(Zeile::festgeschrieben);
        return new SteuerberaterBerichtDto(jahr, monat, stundenart, zeilen, alleFest);
    }

    /**
     * Stunden je Abwesenheitsart aus den Abwesenheiten selbst: Der Monatssaldo trägt die
     * Aufteilung nach Art erst beim Monatsabschluss ein, offene Monate hätten sonst 0 Urlaub.
     */
    private Map<Long, Map<AbwesenheitsTyp, BigDecimal>> abwesenheitenJeMitarbeiter(List<ZeitkontoStatusDto> konten,
            YearMonth ym) {
        if (konten.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<AbwesenheitsTyp, BigDecimal>> ergebnis = new HashMap<>();
        for (var summe : abwesenheitRepository.sumStundenNachMonatUndTyp(
                konten.stream().map(ZeitkontoStatusDto::mitarbeiterId).toList(), ym.atDay(1), ym.atEndOfMonth())) {
            if (summe.getTyp() != null && summe.getStunden() != null) {
                ergebnis.computeIfAbsent(summe.getMitarbeiterId(), k -> new EnumMap<>(AbwesenheitsTyp.class))
                        .merge(summe.getTyp(), summe.getStunden(), BigDecimal::add);
            }
        }
        return ergebnis;
    }

    private Zeile zeile(ZeitkontoStatusDto konto, Mitarbeiter mitarbeiter, YearMonth ym,
            Map<AbwesenheitsTyp, BigDecimal> abwesenheit, Stundenart stundenart) {
        Long id = konto.mitarbeiterId();
        MonatsSaldo saldo = monatsSaldoService.berechneOhneSpeichern(id, ym.getYear(), ym.getMonthValue());
        boolean fest = Boolean.TRUE.equals(saldo.getFestgeschrieben()) && saldo.getUrlaubStunden() != null;
        BigDecimal soll = saldo.getSollStunden();
        BigDecimal feiertage = saldo.getFeiertagsStunden();
        BigDecimal urlaub = fest ? saldo.getUrlaubStunden() : abwesenheit.get(AbwesenheitsTyp.URLAUB);
        BigDecimal krankheit = fest
                ? summe(saldo.getKrankheitStunden(), saldo.getKrankengeldStunden(), saldo.getWiedereingliederungStunden())
                : abwesenheit.get(AbwesenheitsTyp.KRANKHEIT);
        BigDecimal fortbildung = fest ? saldo.getFortbildungStunden() : abwesenheit.get(AbwesenheitsTyp.FORTBILDUNG);
        BigDecimal stand = monatsSaldoService.berechneGesamtsaldo(id, ym.atEndOfMonth());
        BigDecimal resturlaub = mitarbeiter == null ? BigDecimal.ZERO
                : urlaubskontoService.berechne(mitarbeiter, ym.getYear()).verbleibend();
        BigDecimal arbeit = stundenart == Stundenart.IST ? saldo.getIstStunden()
                : SteuerberaterStunden.arbeitsstunden(soll, urlaub, feiertage, krankheit, fortbildung);
        return new Zeile(id, konto.mitarbeiterName(), SteuerberaterStunden.tagessoll(versionen(konto), ym),
                r(arbeit), r(urlaub), r(feiertage), r(krankheit), r(fortbildung), r(stand), r(resturlaub),
                Boolean.TRUE.equals(saldo.getFestgeschrieben()));
    }

    private static List<ZeitkontoVersionDto> versionen(ZeitkontoStatusDto konto) {
        List<ZeitkontoVersionDto> alle = new ArrayList<>();
        if (konto.historie() != null) {
            alle.addAll(konto.historie());
        }
        alle.add(konto.aktuell());
        return alle;
    }

    private static BigDecimal summe(BigDecimal... werte) {
        BigDecimal ergebnis = BigDecimal.ZERO;
        for (BigDecimal w : werte) {
            ergebnis = w == null ? ergebnis : ergebnis.add(w);
        }
        return ergebnis;
    }

    private static BigDecimal r(BigDecimal wert) {
        return SteuerberaterStunden.runde(wert);
    }
}
