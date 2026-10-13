package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.Abwesenheit;
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.UrlaubUebertrag;
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository;
import org.example.kalkulationsprogramm.repository.UrlaubUebertragRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Rechnet das Urlaubskonto eines Mitarbeiters für ein Jahr – inklusive
 * Resturlaub aus dem Vorjahr (Issue #270, Punkt 4).
 *
 * <p><b>Übertrag ohne gespeicherte Kopie:</b> Der Rest des Vorjahres wird bei
 * jeder Abfrage aus den gebuchten Urlaubstagen des Vorjahres berechnet. Damit
 * gibt es keinen Jahreswechsel-Lauf, der doppelt oder gar nicht laufen könnte,
 * und eine nachträgliche Buchung im Vorjahr ist sofort im neuen Jahr sichtbar.
 * Gleiches gilt für eine Änderung am Jahresurlaub: sie ändert den berechneten
 * Übertrag rückwirkend mit. Gespeichert wird nur ein von Hand festgelegter
 * Übertrag ({@link UrlaubUebertrag}); er ersetzt die Rechnung für genau dieses
 * Jahr. Vor dem Startjahr ({@link UrlaubsverfallEinstellungService#uebertragAbJahr()})
 * und vor dem Eintrittsjahr gibt es keinen automatischen Übertrag. Ein
 * überzogenes Vorjahr ergibt Übertrag 0, nie einen negativen Wert.
 *
 * <p><b>Reihenfolge:</b> Urlaub im neuen Jahr zählt zuerst gegen den Übertrag.
 * Was bis zum Verfallstag (einschließlich) nicht genommen ist, verfällt danach –
 * sofern der Betrieb nicht „verfällt nicht“ eingestellt hat.
 *
 * <p>Gemeinsame Quelle für Saldo (PC und Handy), Antragsprüfung und
 * Verfall-Hinweis.
 */
@Service
@RequiredArgsConstructor
public class UrlaubskontoService {

    static final int STANDARD_ANSPRUCH = 30;
    /** Schutz gegen endlose Rückrechnung, falls Startjahr und Eintritt fehlen. */
    static final int MAX_JAHRE_ZURUECK = 30;
    private static final BigDecimal HALBER_TAG = new BigDecimal("0.5");

    private final AbwesenheitRepository abwesenheitRepository;
    private final ZeitkontoKorrekturService zeitkontoKorrekturService;
    private final TagesSollService tagesSollService;
    private final UrlaubstageRechner urlaubstageRechner;
    private final UrlaubUebertragRepository uebertragRepository;
    private final UrlaubsverfallEinstellungService einstellungen;
    private final Clock clock;

    /** Ein Urlaubstag mit seinem Anteil (0,5 oder 1). */
    record UrlaubsTag(LocalDate datum, BigDecimal anteil) {}

    /** Was für eine Abfrage einmal geladen wird. */
    private record Kontext(UrlaubsverfallEinstellungService.Verfallsregel regel, int abJahr, LocalDate heute) {}

    private record Uebertrag(BigDecimal tage, boolean manuell) {}

    /**
     * Übertrag in ein Jahr, ohne das ganze Konto des Jahres zu rechnen.
     *
     * @param tage          gültiger Übertrag (von Hand oder berechnet)
     * @param berechnet     was das Programm selbst rechnen würde
     * @param verfallsdatum letzter Tag, an dem der Übertrag genommen werden kann; {@code null} = verfällt nicht
     */
    public record UebertragStand(int jahr, BigDecimal tage, BigDecimal berechnet, boolean manuell,
            LocalDate verfallsdatum) {}

    /**
     * Ergebnis der Probe-Rechnung für einen neuen Urlaubsantrag.
     *
     * @param verbleibendVorher        Resturlaub zum ersten Urlaubstag, ohne den Antrag
     * @param restDanach               Rest nach dem Antrag (negativ = reicht nicht)
     * @param verfallsdatum            letzter Tag für den Vorjahresrest; {@code null} = verfällt nicht
     * @param vorjahresrestVerfaellt   {@code true}, wenn im Antragszeitraum Vorjahresurlaub verfällt
     */
    public record Antragspruefung(BigDecimal verbleibendVorher, BigDecimal restDanach, LocalDate verfallsdatum,
            boolean vorjahresrestVerfaellt) {

        public boolean reicht() {
            return restDanach.signum() >= 0;
        }
    }

    /**
     * Konto zum heutigen Stand. Für vergangene Jahre gilt der Stand am 31.12.,
     * für künftige der 1.1. (dann ist noch nichts verfallen).
     */
    @Transactional(readOnly = true)
    public Urlaubskonto berechne(Mitarbeiter mitarbeiter, int jahr) {
        Kontext kontext = kontext();
        return berechne(mitarbeiter, jahr, stichtagImJahr(kontext.heute(), jahr), kontext, 0, null, List.of());
    }

    /**
     * Wie {@link #berechne(Mitarbeiter, int)}, mit den schon geladenen Abwesenheiten
     * des Jahres (der Saldo lädt sie ohnehin für Krankheit und Fortbildung).
     */
    @Transactional(readOnly = true)
    public Urlaubskonto berechne(Mitarbeiter mitarbeiter, int jahr, List<Abwesenheit> abwesenheitenImJahr) {
        Kontext kontext = kontext();
        return berechne(mitarbeiter, jahr, stichtagImJahr(kontext.heute(), jahr), kontext, 0, abwesenheitenImJahr,
                List.of());
    }

    /**
     * Konto zu einem Stichtag – z. B. dem ersten Tag eines Urlaubsantrags: Liegt
     * er nach dem Verfallstag, steht der Übertrag dafür nicht mehr zur Verfügung.
     */
    @Transactional(readOnly = true)
    public Urlaubskonto berechne(Mitarbeiter mitarbeiter, int jahr, LocalDate stichtag) {
        return berechne(mitarbeiter, jahr, stichtagImJahr(stichtag, jahr), kontext(), 0, null, List.of());
    }

    /**
     * Probe-Rechnung für einen Urlaubsantrag: Die beantragten Arbeitstage werden
     * wie gebuchter Urlaub eingerechnet. Tage bis zum Verfallstag dürfen den
     * Vorjahresrest nutzen, Tage danach nur den Anspruch des Jahres – so passt
     * ein Antrag über den Verfallstag hinweg nur, wenn er wirklich reicht.
     *
     * @param tage beantragte Arbeitstage (je ein ganzer Tag)
     */
    @Transactional(readOnly = true)
    public Antragspruefung pruefeAntrag(Mitarbeiter mitarbeiter, int jahr, List<LocalDate> tage) {
        Kontext kontext = kontext();
        LocalDate heute = kontext.heute();
        LocalDate erster = tage.stream().min(LocalDate::compareTo).orElse(heute);
        LocalDate letzter = tage.stream().max(LocalDate::compareTo).orElse(heute);
        Urlaubskonto vorher = berechne(mitarbeiter, jahr, stichtagImJahr(spaeterer(erster, heute), jahr), kontext, 0,
                null, List.of());
        List<UrlaubsTag> beantragt = tage.stream().map(t -> new UrlaubsTag(t, BigDecimal.ONE)).toList();
        Urlaubskonto danach = berechne(mitarbeiter, jahr, stichtagImJahr(spaeterer(letzter, heute), jahr), kontext, 0,
                null, beantragt);
        return new Antragspruefung(vorher.verbleibend(), rohRest(danach), danach.verfallsdatum(),
                danach.verfallen().compareTo(vorher.verfallen()) > 0);
    }

    /** Übertrag in ein Jahr samt berechnetem Wert – rechnet höchstens das Vorjahr. */
    @Transactional(readOnly = true)
    public UebertragStand uebertragStand(Mitarbeiter mitarbeiter, int jahr) {
        Kontext kontext = kontext();
        BigDecimal berechnet = automatischerUebertrag(mitarbeiter, jahr, kontext, 0);
        LocalDate verfallsdatum = kontext.regel().verfallsdatum(jahr).orElse(null);
        return uebertragRepository.findByMitarbeiterIdAndJahr(mitarbeiter.getId(), jahr)
                .map(eintrag -> new UebertragStand(jahr, eintrag.getTage(), berechnet, true, verfallsdatum))
                .orElseGet(() -> new UebertragStand(jahr, berechnet, berechnet, false, verfallsdatum));
    }

    /** Übertrag, den das Programm selbst rechnen würde – ohne Handeintrag für dieses Jahr. */
    @Transactional(readOnly = true)
    public BigDecimal automatischerUebertrag(Mitarbeiter mitarbeiter, int jahr) {
        return automatischerUebertrag(mitarbeiter, jahr, kontext(), 0);
    }

    private Kontext kontext() {
        return new Kontext(einstellungen.regel(), einstellungen.uebertragAbJahr(), LocalDate.now(clock));
    }

    private static LocalDate spaeterer(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    static LocalDate stichtagImJahr(LocalDate tag, int jahr) {
        LocalDate anfang = LocalDate.of(jahr, 1, 1);
        LocalDate ende = LocalDate.of(jahr, 12, 31);
        if (tag.isBefore(anfang)) return anfang;
        return tag.isAfter(ende) ? ende : tag;
    }

    /**
     * Jahresanspruch eines Jahres – im Eintrittsjahr anteilig (Nutzerentscheidung,
     * angelehnt an § 5 Abs. 1 BUrlG): ein Zwölftel je vollem Beschäftigungsmonat
     * bis zum 31.12. Vor dem Eintrittsjahr gibt es keinen Anspruch.
     *
     * <p><b>Rundung (§ 5 Abs. 2 BUrlG):</b> Bruchteile von mindestens einem halben
     * Tag werden auf einen vollen Tag aufgerundet. Kleinere Bruchteile dürfen nach
     * der Rechtsprechung des BAG nicht abgerundet werden; weil das Programm nur
     * halbe Tage kennt, werden sie zugunsten des Mitarbeiters auf einen halben Tag
     * aufgerundet. Beispiele: 30 Tage, Eintritt 15.03. → 9 volle Monate → 22,5 →
     * 23 Tage; 28 Tage, Eintritt 01.11. → 2 Monate → 4,67 → 5 Tage; 30 Tage,
     * Eintritt 01.12. → 2,5 → 3 Tage; 20 Tage, Eintritt 01.12. → 1,67 → 2 Tage;
     * 26 Tage, Eintritt 01.12. → 2,17 → 2,5 Tage.
     */
    static BigDecimal anspruchImJahr(Integer jahresUrlaub, LocalDate eintritt, int jahr) {
        BigDecimal voll = BigDecimal.valueOf(jahresUrlaub != null ? jahresUrlaub : STANDARD_ANSPRUCH);
        if (eintritt == null || eintritt.getYear() < jahr) {
            return voll;
        }
        if (eintritt.getYear() > jahr) {
            return BigDecimal.ZERO;
        }
        long monate = Math.min(12, ChronoUnit.MONTHS.between(eintritt, LocalDate.of(jahr + 1, 1, 1)));
        if (monate >= 12) {
            return voll;
        }
        BigDecimal roh = voll.multiply(BigDecimal.valueOf(monate)).divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
        BigDecimal ganz = roh.setScale(0, RoundingMode.FLOOR);
        BigDecimal bruch = roh.subtract(ganz);
        if (bruch.signum() == 0) {
            return ganz;
        }
        return bruch.compareTo(HALBER_TAG) >= 0 ? ganz.add(BigDecimal.ONE) : ganz.add(HALBER_TAG);
    }

    private Urlaubskonto berechne(Mitarbeiter mitarbeiter, int jahr, LocalDate stichtag, Kontext kontext, int tiefe,
            List<Abwesenheit> geladen, List<UrlaubsTag> zusaetzlich) {
        BigDecimal anspruch = anspruchImJahr(mitarbeiter.getJahresUrlaub(), mitarbeiter.getEintrittsdatum(), jahr);
        List<Abwesenheit> abwesenheiten = geladen != null ? geladen
                : abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(
                        mitarbeiter.getId(), LocalDate.of(jahr, 1, 1), LocalDate.of(jahr, 12, 31));
        List<UrlaubsTag> tage = new ArrayList<>(urlaubsTage(mitarbeiter, abwesenheiten));
        tage.addAll(zusaetzlich);
        BigDecimal korrektur = urlaubstageRechner.korrekturTage(
                zeitkontoKorrekturService.summiereAktiveUrlaubsKorrekturen(mitarbeiter.getId(), jahr));
        Uebertrag uebertrag = uebertrag(mitarbeiter, jahr, kontext, tiefe);
        LocalDate verfallsdatum = kontext.regel().verfallsdatum(jahr).orElse(null);
        return rechne(jahr, anspruch, korrektur, uebertrag.tage(), uebertrag.manuell(), verfallsdatum, tage,
                kontext.heute(), stichtag);
    }

    private List<UrlaubsTag> urlaubsTage(Mitarbeiter mitarbeiter, Collection<Abwesenheit> abwesenheiten) {
        List<Abwesenheit> urlaub = abwesenheiten.stream()
                .filter(a -> a.getTyp() == AbwesenheitsTyp.URLAUB && a.getDatum() != null)
                .toList();
        // Tagessoll nur einmal für den ganzen Urlaubszeitraum laden (halbe Tage, Issue #269).
        Map<LocalDate, BigDecimal> sollJeTag = urlaubstageRechner.sollFuerUrlaub(urlaub,
                UrlaubstageRechner.sollLader(mitarbeiter, tagesSollService));
        return urlaub.stream()
                .map(a -> new UrlaubsTag(a.getDatum(), urlaubstageRechner.anteil(a, sollJeTag.get(a.getDatum()))))
                .toList();
    }

    private Uebertrag uebertrag(Mitarbeiter mitarbeiter, int jahr, Kontext kontext, int tiefe) {
        if (Boolean.TRUE.equals(mitarbeiter.getIstGeschaeftsfuehrer())) {
            // Die Geschäftsführung führt kein Urlaubskonto.
            return new Uebertrag(BigDecimal.ZERO, false);
        }
        return uebertragRepository.findByMitarbeiterIdAndJahr(mitarbeiter.getId(), jahr)
                .map(eintrag -> new Uebertrag(eintrag.getTage(), true))
                .orElseGet(() -> new Uebertrag(automatischerUebertrag(mitarbeiter, jahr, kontext, tiefe), false));
    }

    private BigDecimal automatischerUebertrag(Mitarbeiter mitarbeiter, int jahr, Kontext kontext, int tiefe) {
        LocalDate eintritt = mitarbeiter.getEintrittsdatum();
        if (Boolean.TRUE.equals(mitarbeiter.getIstGeschaeftsfuehrer())
                || jahr <= kontext.abJahr()
                || (eintritt != null && eintritt.getYear() >= jahr)
                || tiefe >= MAX_JAHRE_ZURUECK) {
            return BigDecimal.ZERO;
        }
        int vorjahr = jahr - 1;
        // verbleibend ist nie negativ: ein überzogenes Vorjahr ergibt Übertrag 0.
        return berechne(mitarbeiter, vorjahr, LocalDate.of(vorjahr, 12, 31), kontext, tiefe + 1, null, List.of())
                .verbleibend();
    }

    /** Rest ohne Untergrenze – negativ heißt: es reicht nicht. */
    static BigDecimal rohRest(Urlaubskonto konto) {
        return konto.jahresanspruch()
                .add(konto.uebertrag())
                .subtract(konto.verfallen())
                .subtract(konto.genommen())
                .subtract(konto.geplant())
                .add(konto.korrektur());
    }

    /**
     * Die eigentliche Rechnung, ohne Datenbank.
     *
     * @param verfallsdatum letzter Tag für den Übertrag; {@code null} = verfällt nicht
     * @param heute         trennt genommen (davor) und geplant (ab heute)
     * @param stichtag      ab dem Tag nach dem Verfallsdatum ist der offene Übertrag weg
     */
    static Urlaubskonto rechne(int jahr, BigDecimal anspruch, BigDecimal korrektur, BigDecimal uebertrag,
            boolean uebertragManuell, LocalDate verfallsdatum, Collection<UrlaubsTag> tage, LocalDate heute,
            LocalDate stichtag) {
        BigDecimal genommen = BigDecimal.ZERO;
        BigDecimal geplant = BigDecimal.ZERO;
        BigDecimal bisVerfall = BigDecimal.ZERO;
        for (UrlaubsTag tag : tage) {
            if (tag.datum().isBefore(heute)) {
                genommen = genommen.add(tag.anteil());
            } else {
                geplant = geplant.add(tag.anteil());
            }
            if (verfallsdatum == null || !tag.datum().isAfter(verfallsdatum)) {
                bisVerfall = bisVerfall.add(tag.anteil());
            }
        }
        BigDecimal uebertragTage = uebertrag == null ? BigDecimal.ZERO : uebertrag.max(BigDecimal.ZERO);
        // Urlaub zählt zuerst gegen den Übertrag – aber nur Tage bis zum Verfall.
        BigDecimal vorjahrOffen = uebertragTage.subtract(uebertragTage.min(bisVerfall));
        BigDecimal verfallen = BigDecimal.ZERO;
        if (verfallsdatum != null && stichtag.isAfter(verfallsdatum)) {
            verfallen = vorjahrOffen;
            vorjahrOffen = BigDecimal.ZERO;
        }
        BigDecimal verbleibend = anspruch
                .add(uebertragTage)
                .subtract(verfallen)
                .subtract(genommen)
                .subtract(geplant)
                .add(korrektur)
                .max(BigDecimal.ZERO);
        return new Urlaubskonto(jahr, anspruch, uebertragTage, uebertragManuell, verfallsdatum, verfallen,
                vorjahrOffen.min(verbleibend), genommen, geplant, korrektur, verbleibend);
    }
}
