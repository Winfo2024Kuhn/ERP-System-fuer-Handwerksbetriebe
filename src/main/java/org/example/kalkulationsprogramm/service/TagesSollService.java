package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Feiertag;
import org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungPhase;
import org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungPhaseTyp;
import org.example.kalkulationsprogramm.domain.Zeitkonto;
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

    public BigDecimal periodenSoll(Long mitarbeiterId, Zeitkonto konto, LocalDate tag) {
        return berechneEinzeltag(mitarbeiterId, konto, tag).periodenSoll();
    }

    public BigDecimal feiertagsGutschrift(Long mitarbeiterId, Zeitkonto konto, LocalDate tag) {
        return berechneEinzeltag(mitarbeiterId, konto, tag).feiertagsGutschrift();
    }

    public BigDecimal arbeitsSoll(Long mitarbeiterId, Zeitkonto konto, LocalDate tag) {
        TagesWerte werte = berechneEinzeltag(mitarbeiterId, konto, tag);
        return werte.periodenSoll().subtract(werte.feiertagsGutschrift());
    }

    public BigDecimal periodenSollSumme(Long mitarbeiterId, Zeitkonto konto, LocalDate von, LocalDate bis) {
        return summiere(mitarbeiterId, konto, von, bis, TagesWerte::periodenSoll);
    }

    public BigDecimal feiertagsGutschriftSumme(Long mitarbeiterId, Zeitkonto konto, LocalDate von, LocalDate bis) {
        return summiere(mitarbeiterId, konto, von, bis, TagesWerte::feiertagsGutschrift);
    }

    /**
     * Wie {@link #periodenSoll}, aber fuer einen ganzen Zeitraum auf einmal:
     * Phasen und Feiertage werden EINMAL geladen statt einmal pro Tag. Fuer
     * Aufrufer, die den Wert je Tag brauchen (Kalenderansicht,
     * Stufenplan-Tabelle) statt nur der Summe - sonst waeren
     * {@code periodenSollSumme}/{@code feiertagsGutschriftSumme} keine Option,
     * weil sie die Tageswerte nicht einzeln herausgeben.
     *
     * <p>Gemessen (Abschnitt 4, Befund 2): ein 31-Tage-Kalendermonat kam vorher
     * auf 249 Repository-Aufrufe (62 x {@code findImZeitraum} + 187 gegen
     * {@code FeiertagRepository}), weil {@code ZeitverwaltungController} pro
     * Tag einzeln {@link #arbeitsSoll} und {@link #feiertagsGutschrift} rief.
     * Ueber diese Methode sind es 2 (eine {@code findImZeitraum}- und eine
     * {@code getFeiertageZwischen}-Ladung je Aufruf).
     */
    public Map<LocalDate, BigDecimal> periodenSollJeTag(Long mitarbeiterId, Zeitkonto konto, LocalDate von,
            LocalDate bis) {
        return jeTag(mitarbeiterId, konto, von, bis, TagesWerte::periodenSoll);
    }

    /** Zeitraum-Geschwister von {@link #feiertagsGutschrift} - siehe {@link #periodenSollJeTag}. */
    public Map<LocalDate, BigDecimal> feiertagsGutschriftJeTag(Long mitarbeiterId, Zeitkonto konto, LocalDate von,
            LocalDate bis) {
        return jeTag(mitarbeiterId, konto, von, bis, TagesWerte::feiertagsGutschrift);
    }

    /** Zeitraum-Geschwister von {@link #arbeitsSoll} - siehe {@link #periodenSollJeTag}. */
    public Map<LocalDate, BigDecimal> arbeitsSollJeTag(Long mitarbeiterId, Zeitkonto konto, LocalDate von,
            LocalDate bis) {
        return jeTag(mitarbeiterId, konto, von, bis, w -> w.periodenSoll().subtract(w.feiertagsGutschrift()));
    }

    private BigDecimal summiere(Long mitarbeiterId, Zeitkonto konto, LocalDate von, LocalDate bis,
            Function<TagesWerte, BigDecimal> ausgewaehlterWert) {
        return jeTag(mitarbeiterId, konto, von, bis, ausgewaehlterWert).values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Laedt Phasen und Feiertage fuer den Zeitraum EINMAL und liefert den
     * gewaehlten Wert je Tag - Grundlage sowohl fuer {@link #summiere} als
     * auch fuer die drei oeffentlichen Je-Tag-Methoden.
     */
    private Map<LocalDate, BigDecimal> jeTag(Long mitarbeiterId, Zeitkonto konto, LocalDate von, LocalDate bis,
            Function<TagesWerte, BigDecimal> ausgewaehlterWert) {
        List<LangzeitkrankmeldungPhase> phasen = phaseRepository.findImZeitraum(mitarbeiterId, von, bis);
        Map<LocalDate, Feiertag> feiertage = feiertageNachBundeslandBY(von, bis);

        Map<LocalDate, BigDecimal> ergebnis = new LinkedHashMap<>();
        for (LocalDate tag = von; !tag.isAfter(bis); tag = tag.plusDays(1)) {
            TagesWerte werte = berechneTag(tagesBasis(phasen, konto, tag), feiertage.get(tag));
            ergebnis.put(tag, ausgewaehlterWert.apply(werte));
        }
        return ergebnis;
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

    /**
     * Einzeltag-Variante: fragt FeiertagService direkt ab (kein Batch-Kontext
     * vorhanden). Nutzt {@code getFeiertagInfo} statt getrennter
     * {@code istFeiertag}/{@code istHalberFeiertag}-Aufrufe - beide Fragen
     * (ist es ein Feiertag? ist er halb?) stecken in derselben Zeile, ein
     * zweiter Zugriff auf denselben Datensatz war unnoetig (Befund 2,
     * Abschnitt 4).
     */
    private TagesWerte berechneEinzeltag(Long mitarbeiterId, Zeitkonto konto, LocalDate tag) {
        List<LangzeitkrankmeldungPhase> phasen = phaseRepository.findImZeitraum(mitarbeiterId, tag, tag);
        BigDecimal basis = tagesBasis(phasen, konto, tag);
        if (basis.signum() == 0) {
            return new TagesWerte(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        Feiertag feiertagAmTag = feiertagService.getFeiertagInfo(tag).orElse(null);
        return berechneTag(basis, feiertagAmTag);
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
    private BigDecimal tagesBasis(List<LangzeitkrankmeldungPhase> phasen, Zeitkonto konto, LocalDate tag) {
        BigDecimal roh = konto.getSollstundenFuerTag(tag.getDayOfWeek().getValue());
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

    private static BigDecimal halbieren(BigDecimal wert) {
        return wert.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private record TagesWerte(BigDecimal periodenSoll, BigDecimal feiertagsGutschrift) {
    }
}
