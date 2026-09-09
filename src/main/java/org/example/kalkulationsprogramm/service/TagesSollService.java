package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Feiertag;
import org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungPhase;
import org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungPhaseTyp;
import org.example.kalkulationsprogramm.dto.ZeitkontenmodellDto;
import org.example.kalkulationsprogramm.repository.LangzeitkrankmeldungPhaseRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Buendelt die Tagessoll-Berechnung, die heute an drei Stellen dupliziert
 * ist ({@code ZeitkontoService.berechneSollstundenFuerZeitraum},
 * {@code MonatsSaldoService.berechneFeiertagsStunden},
 * {@code ZeiterfassungApiService.berechneFeiertagsStunden}) - mit
 * unterschiedlicher Feiertagsbehandlung, die ein naives "einer fuer alles"
 * zerstoeren wuerde (siehe E2 im Plan "Langzeitkrankmeldung"). Deshalb drei
 * benannte Groessen statt einer:
 *
 * <ul>
 *   <li>{@code periodenSoll} - was der Mitarbeiter laut Vertrag/Stufenplan
 *       schuldet. Feiertag zaehlt als bezahlter Arbeitstag, halber Feiertag
 *       50 %.</li>
 *   <li>{@code feiertagsGutschrift} - bezahlte Feiertagsstunden auf der
 *       Ist-Seite. 0, wenn kein Feiertag oder kein Arbeitstag.</li>
 *   <li>{@code arbeitsSoll} - was tatsaechlich zu leisten ist:
 *       {@code periodenSoll - feiertagsGutschrift}.</li>
 * </ul>
 *
 * <p><b>Wer {@code periodenSoll} senkt, muss {@code feiertagsGutschrift}
 * mitsenken - sonst entstehen Phantom-Ueberstunden.</b>
 *
 * <p>Laeuft am Tag eine Wiedereingliederungsphase, ersetzt deren
 * {@code stundenProTag} (gedeckelt auf das Zeitkonto-Soll) die Tagesbasis;
 * die Feiertagsbehandlung laeuft danach unveraendert weiter - der
 * Stufenplan-Wert ist kein fester Override ueber den Feiertag hinweg (E3 im
 * Plan). Ohne laufende Langzeitkrankmeldung ist das Ergebnis bitgleich zum
 * heutigen Bestand.
 */
@Service
@RequiredArgsConstructor
public class TagesSollService {

    private final FeiertagService feiertagService;
    private final LangzeitkrankmeldungPhaseRepository phaseRepository;
    private final org.example.kalkulationsprogramm.repository.ZeitkontoVersionRepository versionRepository;

    public BigDecimal periodenSoll(Long mitarbeiterId, LocalDate tag) {
        return periodenSollJeTag(mitarbeiterId, tag, tag).get(tag);
    }

    public BigDecimal periodenSollSumme(Long mitarbeiterId, LocalDate von, LocalDate bis) {
        return periodenSollJeTag(mitarbeiterId, von, bis).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Map<LocalDate, BigDecimal> periodenSollJeTag(Long mitarbeiterId, LocalDate von, LocalDate bis) {
        return versioniertJeTag(mitarbeiterId, von, bis, TagesWerte::periodenSoll);
    }

    public BigDecimal feiertagsGutschrift(Long mitarbeiterId, LocalDate tag) {
        return feiertagsGutschriftJeTag(mitarbeiterId, tag, tag).get(tag);
    }

    public BigDecimal feiertagsGutschriftSumme(Long mitarbeiterId, LocalDate von, LocalDate bis) {
        return feiertagsGutschriftJeTag(mitarbeiterId, von, bis).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Map<LocalDate, BigDecimal> feiertagsGutschriftJeTag(Long mitarbeiterId, LocalDate von, LocalDate bis) {
        return versioniertJeTag(mitarbeiterId, von, bis, TagesWerte::feiertagsGutschrift);
    }

    public BigDecimal arbeitsSoll(Long mitarbeiterId, LocalDate tag) {
        return arbeitsSollJeTag(mitarbeiterId, tag, tag).get(tag);
    }

    public BigDecimal arbeitsSollSumme(Long mitarbeiterId, LocalDate von, LocalDate bis) {
        return arbeitsSollJeTag(mitarbeiterId, von, bis).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Map<LocalDate, BigDecimal> arbeitsSollJeTag(Long mitarbeiterId, LocalDate von, LocalDate bis) {
        return versioniertJeTag(mitarbeiterId, von, bis, w -> w.periodenSoll().subtract(w.feiertagsGutschrift()));
    }

    /**
     * Reine Vorschau für eine noch nicht gespeicherte Arbeitszeit. Verwendet
     * dieselbe Feiertags- und Stufenplan-Rechenengine wie die Versionen eines
     * Mitarbeiters, ohne eine hypothetische Entity zu erzeugen oder zu speichern.
     */
    public ArbeitszeitVorschau vorschau(Long mitarbeiterId, ZeitkontenmodellDto.Arbeitszeit arbeitszeit,
            LocalDate von, LocalDate bis) {
        if (arbeitszeit == null || von == null || bis == null || bis.isBefore(von)) {
            throw new IllegalArgumentException("Bitte eine gültige Arbeitszeit und einen gültigen Zeitraum angeben.");
        }
        var phasen = phaseRepository.findImZeitraum(mitarbeiterId, von, bis);
        var feiertage = feiertageNachBundeslandBY(von, bis);
        BigDecimal periodenSoll = BigDecimal.ZERO;
        BigDecimal feiertagsGutschrift = BigDecimal.ZERO;
        for (LocalDate tag = von; !tag.isAfter(bis); tag = tag.plusDays(1)) {
            TagesWerte werte = berechneTag(tagesBasis(phasen, sollFuerTag(arbeitszeit, tag), tag), feiertage.get(tag));
            periodenSoll = periodenSoll.add(werte.periodenSoll());
            feiertagsGutschrift = feiertagsGutschrift.add(werte.feiertagsGutschrift());
        }
        return new ArbeitszeitVorschau(periodenSoll, feiertagsGutschrift);
    }

    /** Versions-, Phasen- und Feiertagsdaten jeweils einmal je Zeitraum laden. */
    private Map<LocalDate, BigDecimal> versioniertJeTag(Long id, LocalDate von, LocalDate bis,
            Function<TagesWerte, BigDecimal> auswahl) {
        if (von == null || bis == null || bis.isBefore(von)) {
            throw new IllegalArgumentException("Bitte einen gültigen Zeitraum angeben.");
        }
        var versionen = versionRepository.findImZeitraum(id, von, bis);
        var phasen = phaseRepository.findImZeitraum(id, von, bis);
        var feiertage = feiertageNachBundeslandBY(von, bis);
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        int index = 0;
        for (LocalDate tag = von; !tag.isAfter(bis); tag = tag.plusDays(1)) {
            while (index < versionen.size() && versionen.get(index).getGueltigBis() != null
                    && versionen.get(index).getGueltigBis().isBefore(tag)) index++;
            BigDecimal roh = BigDecimal.ZERO;
            if (index < versionen.size() && !versionen.get(index).getGueltigVon().isAfter(tag)) {
                roh = versionen.get(index).getSollstundenFuerTag(tag.getDayOfWeek().getValue());
            }
            result.put(tag, auswahl.apply(berechneTag(tagesBasis(phasen, roh, tag), feiertage.get(tag))));
        }
        return result;
    }

    /**
     * Laedt Feiertage im Zeitraum und filtert auf Bayern.
     * {@link FeiertagService#getFeiertageZwischen} filtert - anders als
     * {@code istFeiertag}/{@code istHalberFeiertag}, die intern auf "BY"
     * filtern - NICHT nach Bundesland. Ohne diesen Filter wuerden die
     * Summenmethoden bei einem Datensatz mit mehreren Bundeslaendern anders
     * rechnen als die Einzeltag-Methoden.
     */
    private Map<LocalDate, Feiertag> feiertageNachBundeslandBY(LocalDate von, LocalDate bis) {
        return feiertagService.getFeiertageZwischen(von, bis).stream()
                .filter(f -> "BY".equals(f.getBundesland()))
                .collect(Collectors.toMap(Feiertag::getDatum, Function.identity(), (a, b) -> a));
    }

    /** Batch-Variante: nutzt die vorab geladene Feiertags-Map statt eigener FeiertagService-Abfragen. */
    private TagesWerte berechneTag(BigDecimal basis, Feiertag feiertagAmTag) {
        if (basis.signum() == 0) {
            return new TagesWerte(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        boolean istFeiertag = feiertagAmTag != null;
        boolean istHalberFeiertag = istFeiertag && feiertagAmTag.isHalbTag();
        return werte(basis, istFeiertag, istHalberFeiertag);
    }

    private TagesWerte werte(BigDecimal tagesBasis, boolean istFeiertag, boolean istHalberFeiertag) {
        BigDecimal periodenSoll = istHalberFeiertag ? halbieren(tagesBasis) : tagesBasis;
        BigDecimal feiertagsGutschrift = !istFeiertag ? BigDecimal.ZERO
                : (istHalberFeiertag ? halbieren(tagesBasis) : tagesBasis);
        return new TagesWerte(periodenSoll, feiertagsGutschrift);
    }

    /**
     * Tagesbasis fuer die Sollberechnung: das Zeitkonto-Soll, ersetzt durch
     * die Wiedereingliederungs-Stunden (gedeckelt auf das Zeitkonto-Soll),
     * wenn am Tag eine laufende Wiedereingliederungsphase existiert. Ein
     * Wochenende (Soll 0) bleibt immer 0 - der Stufenplan darf das Soll nie
     * erhoehen (Sicherheitsnetz, siehe E2 im Plan).
     */
    private BigDecimal tagesBasis(List<LangzeitkrankmeldungPhase> phasen, BigDecimal roh, LocalDate tag) {
        if (roh == null || roh.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        LangzeitkrankmeldungPhase wiedereingliederung = wiedereingliederungAm(phasen, tag);
        if (wiedereingliederung != null && wiedereingliederung.getStundenProTag() != null) {
            return wiedereingliederung.getStundenProTag().min(roh);
        }
        return roh;
    }

    private LangzeitkrankmeldungPhase wiedereingliederungAm(List<LangzeitkrankmeldungPhase> phasen, LocalDate tag) {
        return phasen.stream()
                .filter(p -> p.getTyp() == LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG)
                .filter(p -> !p.getVonDatum().isAfter(tag))
                .filter(p -> p.getBisDatum() == null || !p.getBisDatum().isBefore(tag))
                .findFirst()
                .orElse(null);
    }

    private static BigDecimal sollFuerTag(ZeitkontenmodellDto.Arbeitszeit arbeitszeit, LocalDate tag) {
        return switch (tag.getDayOfWeek()) {
            case MONDAY -> arbeitszeit.montagStunden();
            case TUESDAY -> arbeitszeit.dienstagStunden();
            case WEDNESDAY -> arbeitszeit.mittwochStunden();
            case THURSDAY -> arbeitszeit.donnerstagStunden();
            case FRIDAY -> arbeitszeit.freitagStunden();
            case SATURDAY -> arbeitszeit.samstagStunden();
            case SUNDAY -> arbeitszeit.sonntagStunden();
        };
    }

    private static BigDecimal halbieren(BigDecimal wert) {
        return wert.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private record TagesWerte(BigDecimal periodenSoll, BigDecimal feiertagsGutschrift) {
    }

    public record ArbeitszeitVorschau(BigDecimal periodenSoll, BigDecimal feiertagsGutschrift) {
    }
}
