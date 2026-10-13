package org.example.kalkulationsprogramm.service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Einstellungen → Zeiterfassung → Resturlaub: Verfällt übertragener Resturlaub,
 * und wenn ja, an welchem Tag?
 *
 * <p>Voreinstellung ist die gesetzliche Regel: Der Rest wird übertragen und
 * verfällt am 31. März des Folgejahres (§ 7 Abs. 3 BUrlG). Der Betrieb kann einen
 * anderen Tag wählen oder festlegen, dass Resturlaub gar nicht verfällt.
 *
 * <p>Außerdem merkt sich der Dienst das erste Jahr, dessen Rest automatisch
 * weitergegeben wird ({@link #uebertragAbJahr()}): Für Jahre davor fehlen oft
 * die Urlaubsdaten.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UrlaubsverfallEinstellungService {

    static final String VERFAELLT = "urlaub.verfall.aktiv";
    static final String VERFALL_TAG = "urlaub.verfall.tag";
    static final String AB_JAHR = "urlaub.uebertrag.ab-jahr";
    static final String MEHRURLAUB_ANTEILIG = "urlaub.eintrittsjahr.mehrurlaub-anteilig";

    /** Gesetzlicher Standard: 31. März. */
    static final MonthDay STANDARD_TAG = MonthDay.of(3, 31);

    private final SystemSettingsService settings;
    private final Clock clock;

    /**
     * Die Regel des Betriebs.
     *
     * @param verfaellt   {@code false} = Resturlaub bleibt unbegrenzt erhalten
     * @param verfallsTag Tag und Monat im Folgejahr, ab dem nicht genommener Rest weg ist
     *                    (der Tag selbst zählt noch)
     */
    public record Verfallsregel(boolean verfaellt, MonthDay verfallsTag) {

        public static final Verfallsregel STANDARD = new Verfallsregel(true, STANDARD_TAG);

        /** Letzter Tag, an dem Resturlaub im Jahr {@code jahr} noch genommen werden kann; leer = verfällt nicht. */
        public Optional<LocalDate> verfallsdatum(int jahr) {
            // 29.02. in einem Jahr ohne Schalttag wird zum 28.02.
            return verfaellt ? Optional.of(verfallsTag.atYear(jahr)) : Optional.empty();
        }
    }

    public Verfallsregel regel() {
        boolean verfaellt = !"false".equalsIgnoreCase(settings.get(VERFAELLT, "true").trim());
        return new Verfallsregel(verfaellt, gespeicherterTag());
    }

    private MonthDay gespeicherterTag() {
        String wert = settings.get(VERFALL_TAG, "");
        if (wert == null || wert.isBlank()) {
            return STANDARD_TAG;
        }
        try {
            String[] teile = wert.trim().split("-", 2);
            return MonthDay.of(Integer.parseInt(teile[0]), Integer.parseInt(teile[1]));
        } catch (RuntimeException ex) {
            log.warn("Gespeicherter Verfallstag für Resturlaub ist ungültig, es gilt der 31. März");
            return STANDARD_TAG;
        }
    }

    /**
     * Speichert die Regel. Tag und Monat werden auch bei „verfällt nicht“ geprüft
     * und gespeichert, damit sie beim Zurückschalten wieder dastehen.
     *
     * <p>Ein Tag vor dem 31. März wird angenommen (Tarifverträge dürfen das
     * vorsehen); die Oberfläche fragt dafür vorher mit einem Rechtshinweis nach.
     */
    @Transactional
    public Verfallsregel speichern(Boolean verfaellt, Integer tag, Integer monat) {
        if (verfaellt == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bitte angeben, ob Resturlaub verfällt oder erhalten bleibt.");
        }
        MonthDay verfallsTag = pruefeTag(tag, monat);
        settings.save(VERFAELLT, Boolean.toString(verfaellt),
                "Verfällt übertragener Resturlaub (true) oder bleibt er erhalten (false)?");
        settings.save(VERFALL_TAG, String.format("%02d-%02d", verfallsTag.getMonthValue(), verfallsTag.getDayOfMonth()),
                "Tag im Folgejahr, bis zu dem Resturlaub genommen werden kann (MM-TT)");
        log.info("Regel für Resturlaub gespeichert: verfällt={}, Tag={}", verfaellt, verfallsTag);
        return new Verfallsregel(verfaellt, verfallsTag);
    }

    private static MonthDay pruefeTag(Integer tag, Integer monat) {
        if (tag == null || monat == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte Tag und Monat für den Verfall angeben.");
        }
        try {
            return MonthDay.of(monat, tag);
        } catch (DateTimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Diesen Tag gibt es nicht. Bitte ein gültiges Datum wählen.");
        }
    }

    /**
     * Wird vertraglicher Mehrurlaub im Eintrittsjahr auch bei erfüllter Wartezeit
     * gezwölftelt? Voreinstellung {@code false} = gesetzliche Regel: Wer bis zum
     * 01.07. eintritt, bekommt den vollen Vertragsurlaub (Gleichlauf, BAG
     * 07.08.2012 – 9 AZR 353/10). {@code true} nur, wenn Arbeitsvertrag oder Tarif
     * das Zwölfteln vorsehen; der gesetzliche Mindesturlaub bleibt dann trotzdem.
     */
    public boolean mehrurlaubImEintrittsjahrAnteilig() {
        return "true".equalsIgnoreCase(settings.get(MEHRURLAUB_ANTEILIG, "false").trim());
    }

    @Transactional
    public boolean speichereMehrurlaubImEintrittsjahrAnteilig(Boolean anteilig) {
        if (anteilig == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bitte angeben, ob der Urlaub im Eintrittsjahr anteilig gerechnet wird.");
        }
        settings.save(MEHRURLAUB_ANTEILIG, Boolean.toString(anteilig),
                "Vertraglichen Mehrurlaub im Eintrittsjahr auch bei Eintritt bis 01.07. zwölfteln (laut Vertrag/Tarif)?");
        log.info("Regel für Urlaub im Eintrittsjahr gespeichert: anteilig={}", anteilig);
        return anteilig;
    }

    /**
     * Erstes Jahr, dessen Rest automatisch ins Folgejahr geht. Die Migration V427
     * trägt es beim Update ein (Jahr des Updates). Fehlt der Wert trotzdem, gilt
     * das laufende Jahr – ohne Speichern, weil die Aufrufer nur lesen.
     */
    public int uebertragAbJahr() {
        String wert = settings.get(AB_JAHR, "");
        if (wert != null && !wert.isBlank()) {
            try {
                return Integer.parseInt(wert.trim());
            } catch (NumberFormatException ex) {
                log.warn("Gespeichertes Startjahr für den Resturlaub-Übertrag ist ungültig, es gilt das laufende Jahr");
            }
        }
        return LocalDate.now(clock).getYear();
    }
}
