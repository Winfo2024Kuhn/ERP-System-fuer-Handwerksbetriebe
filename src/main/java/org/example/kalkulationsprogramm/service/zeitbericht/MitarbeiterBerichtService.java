package org.example.kalkulationsprogramm.service.zeitbericht;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Abwesenheit;
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp;
import org.example.kalkulationsprogramm.domain.BuchungsTyp;
import org.example.kalkulationsprogramm.domain.Feiertag;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.service.FeiertagService;
import org.example.kalkulationsprogramm.service.MonatsSaldoService;
import org.example.kalkulationsprogramm.service.TagesSollService;
import org.example.kalkulationsprogramm.service.UrlaubskontoService;
import org.example.kalkulationsprogramm.service.zeitbericht.MitarbeiterBerichtDaten.Summen;
import org.example.kalkulationsprogramm.service.zeitbericht.MitarbeiterBerichtDaten.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Stellt den Stundenzettel eines Mitarbeiters für einen Zeitraum zusammen: Tag für Tag
 * Soll, Arbeit, Abwesenheit und Feiertag, dazu Stundenkonto und Urlaubskonto.
 * Soll und Feiertagsgutschrift kommen aus dem {@link TagesSollService} – derselbe Rechenweg
 * wie im Kalender der Zeiterfassung.
 */
@Service
@RequiredArgsConstructor
public class MitarbeiterBerichtService {

    private static final BigDecimal SECHZIG = new BigDecimal("60");

    private final MitarbeiterRepository mitarbeiterRepository;
    private final ZeitbuchungRepository zeitbuchungRepository;
    private final AbwesenheitRepository abwesenheitRepository;
    private final FeiertagService feiertagService;
    private final TagesSollService tagesSollService;
    private final MonatsSaldoService monatsSaldoService;
    private final UrlaubskontoService urlaubskontoService;

    @Transactional(readOnly = true)
    public List<MitarbeiterBerichtDaten> erstelle(List<Long> mitarbeiterIds, LocalDate von, LocalDate bis) {
        List<Long> auswahl = BerichtEingaben.mitarbeiterIds(mitarbeiterIds, true);
        BerichtEingaben.pruefeZeitraum(von, bis);
        Map<LocalDate, String> feiertage = feiertagService.getFeiertageZwischen(von, bis).stream()
                .collect(Collectors.toMap(Feiertag::getDatum, Feiertag::getBezeichnung, (a, b) -> a));
        List<MitarbeiterBerichtDaten> berichte = new ArrayList<>();
        for (Long id : auswahl) {
            Mitarbeiter mitarbeiter = mitarbeiterRepository.findById(id).orElseThrow(
                    () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diesen Mitarbeiter gibt es nicht mehr."));
            berichte.add(bericht(mitarbeiter, von, bis, feiertage));
        }
        return berichte;
    }

    private MitarbeiterBerichtDaten bericht(Mitarbeiter mitarbeiter, LocalDate von, LocalDate bis,
            Map<LocalDate, String> feiertage) {
        Long id = mitarbeiter.getId();
        Map<LocalDate, BigDecimal> soll = tagesSollService.periodenSollJeTag(id, von, bis);
        Map<LocalDate, BigDecimal> gutschrift = tagesSollService.feiertagsGutschriftJeTag(id, von, bis);
        Map<LocalDate, BigDecimal> arbeit = arbeitJeTag(
                zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(id, von.atStartOfDay(),
                        bis.plusDays(1).atStartOfDay().minusNanos(1)));
        Map<LocalDate, Abwesenheit> abwesenheiten = abwesenheitRepository
                .findByMitarbeiterIdAndDatumBetween(id, von, bis).stream()
                .collect(Collectors.toMap(Abwesenheit::getDatum, a -> a, (a, b) -> a));

        List<Tag> tage = new ArrayList<>();
        for (LocalDate datum = von; !datum.isAfter(bis); datum = datum.plusDays(1)) {
            tage.add(tag(datum, wert(soll.get(datum)), wert(arbeit.get(datum)), abwesenheiten.get(datum),
                    feiertage.get(datum), wert(gutschrift.get(datum))));
        }
        return new MitarbeiterBerichtDaten(name(mitarbeiter), von, bis, tage, summen(tage, abwesenheiten.values()),
                wert(monatsSaldoService.berechneGesamtsaldo(id, bis)),
                urlaubskontoService.berechne(mitarbeiter, bis.getYear()));
    }

    private static Tag tag(LocalDate datum, BigDecimal soll, BigDecimal arbeit, Abwesenheit abwesenheit,
            String feiertag, BigDecimal gutschrift) {
        BigDecimal abwesend = abwesenheit == null ? BigDecimal.ZERO : wert(abwesenheit.getStunden());
        String art = abwesenheit == null ? null : bezeichnung(abwesenheit.getTyp());
        BigDecimal ist = arbeit.add(abwesend).add(gutschrift);
        return new Tag(datum, soll, arbeit, art, abwesend, feiertag, gutschrift, ist);
    }

    private static Summen summen(List<Tag> tage, Collection<Abwesenheit> abwesenheiten) {
        Map<AbwesenheitsTyp, BigDecimal> jeTyp = new EnumMap<>(AbwesenheitsTyp.class);
        for (Abwesenheit a : abwesenheiten) {
            if (a.getTyp() != null) {
                jeTyp.merge(a.getTyp(), wert(a.getStunden()), BigDecimal::add);
            }
        }
        return new Summen(summe(tage.stream().map(Tag::soll)), summe(tage.stream().map(Tag::arbeit)),
                jeTyp.getOrDefault(AbwesenheitsTyp.URLAUB, BigDecimal.ZERO),
                jeTyp.getOrDefault(AbwesenheitsTyp.KRANKHEIT, BigDecimal.ZERO),
                jeTyp.getOrDefault(AbwesenheitsTyp.FORTBILDUNG, BigDecimal.ZERO),
                summe(tage.stream().map(Tag::feiertagStunden)), summe(tage.stream().map(Tag::ist)));
    }

    private static Map<LocalDate, BigDecimal> arbeitJeTag(List<Zeitbuchung> buchungen) {
        Map<LocalDate, BigDecimal> jeTag = new HashMap<>();
        for (Zeitbuchung b : buchungen) {
            if (b.getTyp() == BuchungsTyp.PAUSE || b.getStartZeit() == null || b.getEndeZeit() == null) {
                continue;
            }
            long minuten = Duration.between(b.getStartZeit(), b.getEndeZeit()).toMinutes();
            BigDecimal stunden = BigDecimal.valueOf(Math.max(minuten, 0)).divide(SECHZIG, 2, RoundingMode.HALF_UP);
            jeTag.merge(b.getStartZeit().toLocalDate(), stunden, BigDecimal::add);
        }
        return jeTag;
    }

    static String bezeichnung(AbwesenheitsTyp typ) {
        if (typ == null) {
            return "Abwesend";
        }
        return switch (typ) {
            case URLAUB -> "Urlaub";
            case KRANKHEIT -> "Krank";
            case FORTBILDUNG -> "Fortbildung";
            case ZEITAUSGLEICH -> "Zeitausgleich";
        };
    }

    private static String name(Mitarbeiter m) {
        String name = Stream.of(m.getVorname(), m.getNachname())
                .filter(t -> t != null && !t.isBlank()).map(String::trim).collect(Collectors.joining(" "));
        return name.isEmpty() ? "Mitarbeiter " + m.getId() : name;
    }

    private static BigDecimal summe(Stream<BigDecimal> werte) {
        return werte.reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal wert(BigDecimal wert) {
        return wert == null ? BigDecimal.ZERO : wert;
    }
}
