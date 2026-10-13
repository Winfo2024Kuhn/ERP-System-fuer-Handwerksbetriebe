package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Abwesenheit;
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.UrlaubUebertrag;
import org.example.kalkulationsprogramm.domain.ZeitkontoVersion;
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository;
import org.example.kalkulationsprogramm.repository.UrlaubUebertragRepository;
import org.example.kalkulationsprogramm.repository.ZeitkontoVersionRepository;
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
    private final ZeitkontoVersionRepository zeitkontoVersionRepository;

    /** Ein Urlaubstag mit seinem Anteil (0,5 oder 1). */
    record UrlaubsTag(LocalDate datum, BigDecimal anteil) {}

    /** Was für eine Abfrage einmal geladen wird. */
    /**
     * Was eine Rechnung braucht, einmal geladen. {@code ansprueche} merkt sich den
     * Jahresanspruch je Jahr, damit die Kette über die Vorjahre ihn nicht mehrfach lädt.
     */
    private record Kontext(UrlaubsverfallEinstellungService.Verfallsregel regel, int abJahr, LocalDate heute,
            boolean mehrurlaubAnteilig, Map<Integer, Anspruch> ansprueche) {}

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
     * @param beantragteTage           neu beantragte Arbeitstage (ohne schon gebuchte Urlaubstage)
     */
    public record Antragspruefung(BigDecimal verbleibendVorher, BigDecimal restDanach, LocalDate verfallsdatum,
            boolean vorjahresrestVerfaellt, BigDecimal beantragteTage) {

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
        List<Abwesenheit> abwesenheiten = abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(
                mitarbeiter.getId(), LocalDate.of(jahr, 1, 1), LocalDate.of(jahr, 12, 31));
        // Tage mit schon gebuchtem Urlaub (auch einem halben) zählen nicht: Die
        // Genehmigung legt dort keinen zweiten Eintrag an (UrlaubsantragService),
        // Prüfung und Buchung rechnen so gleich.
        Set<LocalDate> schonGebucht = abwesenheiten.stream()
                .filter(a -> a.getTyp() == AbwesenheitsTyp.URLAUB && a.getDatum() != null)
                .map(Abwesenheit::getDatum)
                .collect(Collectors.toSet());
        List<UrlaubsTag> beantragt = tage.stream()
                .filter(t -> !schonGebucht.contains(t))
                .map(t -> new UrlaubsTag(t, BigDecimal.ONE))
                .toList();
        Urlaubskonto vorher = berechne(mitarbeiter, jahr, stichtagImJahr(spaeterer(erster, heute), jahr), kontext, 0,
                abwesenheiten, List.of());
        Urlaubskonto danach = berechne(mitarbeiter, jahr, stichtagImJahr(spaeterer(letzter, heute), jahr), kontext, 0,
                abwesenheiten, beantragt);
        return new Antragspruefung(vorher.verbleibend(), rohRest(danach), danach.verfallsdatum(),
                danach.verfallen().compareTo(vorher.verfallen()) > 0,
                beantragt.stream().map(UrlaubsTag::anteil).reduce(BigDecimal.ZERO, BigDecimal::add));
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
        return new Kontext(einstellungen.regel(), einstellungen.uebertragAbJahr(), LocalDate.now(clock),
                einstellungen.mehrurlaubImEintrittsjahrAnteilig(), new HashMap<>());
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

    /** Wie der Jahresanspruch zustande kommt – für Hinweise in der Oberfläche. */
    public enum Anspruchsart {
        /** Voller Vertragsurlaub. */
        VOLL,
        /** Vertragsurlaub liegt unter dem gesetzlichen Mindesturlaub – es gilt das Minimum. */
        MINDESTURLAUB,
        /** Eintrittsjahr: ein Zwölftel je vollem Monat. */
        ANTEILIG_EINTRITT,
        /** Eintrittsjahr mit erfüllter Wartezeit: angehoben auf den gesetzlichen Mindesturlaub. */
        MINDESTURLAUB_EINTRITT,
        /** Jahr vor dem Eintritt. */
        VOR_EINTRITT
    }

    /** Jahresanspruch samt Herkunft. */
    public record Anspruch(BigDecimal tage, Anspruchsart art) {}

    /** Werktage (Mo–Sa) je Woche, auf die sich der gesetzliche Mindesturlaub bezieht (§ 3 BUrlG). */
    static final int WERKTAGE_JE_WOCHE = 6;
    /** Gesetzlicher Mindesturlaub in Werktagen bei 6-Tage-Woche (§ 3 Abs. 1 BUrlG). */
    static final int MINDESTURLAUB_WERKTAGE = 24;
    /** Ohne Arbeitszeit im Zeitkonto: übliche 5-Tage-Woche. */
    static final int STANDARD_ARBEITSTAGE = 5;

    /**
     * Gesetzlicher Mindesturlaub in Arbeitstagen: 24 Werktage bei 6-Tage-Woche,
     * umgerechnet auf die Arbeitstage des Mitarbeiters (24 × Tage ÷ 6, also 20 bei
     * 5 Tagen, 16 bei 4 Tagen).
     */
    static BigDecimal gesetzlicherMindesturlaub(int arbeitstageJeWoche) {
        int tage = Math.max(1, Math.min(WERKTAGE_JE_WOCHE, arbeitstageJeWoche));
        return BigDecimal.valueOf((long) MINDESTURLAUB_WERKTAGE * tage / WERKTAGE_JE_WOCHE);
    }

    /** Jahresanspruch des Mitarbeiters; Arbeitstage je Woche aus seinem Zeitkonto. */
    @Transactional(readOnly = true)
    public Anspruch anspruch(Mitarbeiter mitarbeiter, int jahr) {
        return anspruch(mitarbeiter, jahr, kontext());
    }

    private Anspruch anspruch(Mitarbeiter mitarbeiter, int jahr, Kontext kontext) {
        return kontext.ansprueche().computeIfAbsent(jahr, j -> anspruchImJahr(mitarbeiter.getJahresUrlaub(),
                mitarbeiter.getEintrittsdatum(), j, arbeitstageJeWoche(mitarbeiter, j), kontext.mehrurlaubAnteilig()));
    }

    /**
     * Arbeitstage je Woche laut Zeitkonto (Tage mit Soll &gt; 0): die höchste Zahl
     * aller Versionen, die im Jahr gelten – so bekommt niemand weniger als den
     * Mindesturlaub, auch wenn das Zeitkonto erst im Laufe des Jahres angelegt
     * wurde. Ohne Zeitkonto im Jahr gilt die 5-Tage-Woche.
     */
    private int arbeitstageJeWoche(Mitarbeiter mitarbeiter, int jahr) {
        return zeitkontoVersionRepository.findImZeitraum(mitarbeiter.getId(), LocalDate.of(jahr, 1, 1),
                        LocalDate.of(jahr, 12, 31)).stream()
                .mapToInt(ZeitkontoVersion::getArbeitstageProWoche)
                .filter(tage -> tage > 0)
                .max()
                .orElse(STANDARD_ARBEITSTAGE);
    }

    /**
     * Jahresanspruch nach dem Bundesurlaubsgesetz (Nutzerentscheidung: „was das Gesetz vorschreibt“).
     *
     * <ul>
     *   <li>Liegt der Vertragsurlaub unter dem gesetzlichen Mindesturlaub, gilt das Minimum (§ 3, § 13 BUrlG).</li>
     *   <li><b>Eintritt bis 01.07.:</b> Die Wartezeit von sechs Monaten (§ 4 BUrlG) ist im Eintrittsjahr
     *       erfüllt – nach BAG-Lesart auch bei Eintritt am 01.07., denn sie endet dann mit dem 31.12. Ohne
     *       abweichende Vereinbarung gilt für den vertraglichen Mehrurlaub dasselbe (Gleichlauf, BAG 07.08.2012 –
     *       9 AZR 353/10): voller Vertragsurlaub. Nur wenn der Betrieb eingestellt hat, dass Vertrag oder Tarif den
     *       Mehrurlaub zwölfteln ({@code mehrurlaubAnteilig}), gibt es ein Zwölftel je vollem Monat – aber mindestens
     *       den gesetzlichen Mindesturlaub.</li>
     *   <li><b>Eintritt nach dem 01.07.:</b> Wartezeit nicht erfüllt, nur ein Zwölftel je vollem Monat
     *       (§ 5 Abs. 1 a BUrlG).</li>
     *   <li>Vor dem Eintrittsjahr: kein Anspruch.</li>
     * </ul>
     *
     * <p>Ausscheiden im Jahr (§ 5 Abs. 1 b/c BUrlG) rechnet das Programm nicht, weil der Mitarbeiter kein
     * Austrittsdatum hat; das Büro korrigiert dann über eine Urlaubskorrektur. Ebenso Urlaub, den ein früherer
     * Arbeitgeber im selben Jahr schon gewährt hat (§ 6 BUrlG).
     *
     * <p><b>Rundung (§ 5 Abs. 2 BUrlG):</b> Bruchteile von mindestens einem halben Tag werden auf einen vollen
     * Tag aufgerundet. Kleinere Bruchteile dürfen nach der Rechtsprechung des BAG nicht abgerundet werden; weil das
     * Programm nur halbe Tage kennt, werden sie zugunsten des Mitarbeiters auf einen halben Tag aufgerundet.
     * Beispiele (30 Tage, 5-Tage-Woche): Eintritt 15.03. → 30 (gezwölftelt: 22,5 → 23); Eintritt 01.06. → 30
     * (gezwölftelt: 17,5 → 18, angehoben auf 20); Eintritt 01.08. → 12,5 → 13.
     */
    static Anspruch anspruchImJahr(Integer jahresUrlaub, LocalDate eintritt, int jahr, int arbeitstageJeWoche,
            boolean mehrurlaubAnteilig) {
        BigDecimal mindest = gesetzlicherMindesturlaub(arbeitstageJeWoche);
        BigDecimal laut = BigDecimal.valueOf(jahresUrlaub != null ? jahresUrlaub : STANDARD_ANSPRUCH);
        BigDecimal vertrag = laut.max(mindest);
        Anspruchsart vollArt = laut.compareTo(mindest) < 0 ? Anspruchsart.MINDESTURLAUB : Anspruchsart.VOLL;
        if (eintritt == null || eintritt.getYear() < jahr) {
            return new Anspruch(vertrag, vollArt);
        }
        if (eintritt.getYear() > jahr) {
            return new Anspruch(BigDecimal.ZERO, Anspruchsart.VOR_EINTRITT);
        }
        long monate = Math.min(12, ChronoUnit.MONTHS.between(eintritt, LocalDate.of(jahr + 1, 1, 1)));
        boolean wartezeitErfuellt = !eintritt.isAfter(LocalDate.of(jahr, 7, 1));
        if (monate >= 12 || (wartezeitErfuellt && !mehrurlaubAnteilig)) {
            return new Anspruch(vertrag, vollArt);
        }
        BigDecimal anteilig = aufHalbeTageAufgerundet(
                vertrag.multiply(BigDecimal.valueOf(monate)).divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP));
        if (wartezeitErfuellt && anteilig.compareTo(mindest) < 0) {
            return new Anspruch(mindest.min(vertrag), Anspruchsart.MINDESTURLAUB_EINTRITT);
        }
        return new Anspruch(anteilig.min(vertrag), Anspruchsart.ANTEILIG_EINTRITT);
    }

    private static BigDecimal aufHalbeTageAufgerundet(BigDecimal roh) {
        BigDecimal ganz = roh.setScale(0, RoundingMode.FLOOR);
        BigDecimal bruch = roh.subtract(ganz);
        if (bruch.signum() == 0) {
            return ganz;
        }
        return bruch.compareTo(HALBER_TAG) >= 0 ? ganz.add(BigDecimal.ONE) : ganz.add(HALBER_TAG);
    }

    private Urlaubskonto berechne(Mitarbeiter mitarbeiter, int jahr, LocalDate stichtag, Kontext kontext, int tiefe,
            List<Abwesenheit> geladen, List<UrlaubsTag> zusaetzlich) {
        BigDecimal anspruch = anspruch(mitarbeiter, jahr, kontext).tage();
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
