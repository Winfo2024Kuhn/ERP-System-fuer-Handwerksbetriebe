package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Termin-Kalender im Projektmanagement.
 *
 * <p>Wer welchen Termin sieht und ändern darf, entscheidet allein das Backend anhand der
 * Anmeldung (Issue #267, Nutzerentscheidung 19.10.2026):
 * <ul>
 *   <li>Sehen: Ersteller, Teilnehmer und Admins. Firmentermine (ohne Ersteller und ohne
 *       Teilnehmer) sehen alle.</li>
 *   <li>Ändern und löschen: Ersteller, Teilnehmer und Admins. Firmentermine nur Admins.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class KalenderService {

    /** Größter Zeitraum einer Abfrage – reicht für ein Monatsraster mit Vor- und Folgetagen. */
    static final int MAX_ZEITRAUM_TAGE = 100;
    static final int MAX_TITEL_LAENGE = 255;
    static final int MAX_BESCHREIBUNG_LAENGE = 2000;
    /** Hex-Farbe wie „#7c3aed“ – alles andere zeigt der Kalender nicht an. */
    private static final Pattern FARBE = Pattern.compile("#(?:[0-9A-Fa-f]{3}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})");
    static final int MAX_TEILNEHMER = 500;

    static final String NICHT_GEFUNDEN = "Diesen Termin gibt es nicht mehr oder er ist für Sie nicht sichtbar.";
    static final String KEIN_RECHT = "Diesen Termin können nur der Ersteller, die Teilnehmer und Admins ändern.";
    static final String FIRMENTERMIN_NUR_ADMIN = "Termine ohne Ersteller und ohne Teilnehmer sieht der ganze Betrieb – "
            + "die legen nur Admins an. Bitte mindestens einen Teilnehmer wählen.";

    private final KalenderEintragRepository kalenderEintragRepository;
    private final ProjektRepository projektRepository;
    private final KundeRepository kundeRepository;
    private final LieferantenRepository lieferantenRepository;
    private final AnfrageRepository anfrageRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final AngemeldeterMitarbeiterService angemeldeterMitarbeiterService;

    /**
     * Wer gerade in den Kalender schaut.
     *
     * @param mitarbeiterId zugeordneter Mitarbeiter, {@code null} ohne Zuordnung
     * @param admin         Benutzer mit der Rolle Admin
     */
    public record KalenderBetrachter(Long mitarbeiterId, boolean admin) {
    }

    /** Eingaben für einen neuen oder geänderten Termin. {@code teilnehmerIds == null} heißt „unverändert“. */
    public record TerminDaten(
            String titel,
            String beschreibung,
            LocalDate datum,
            LocalTime startZeit,
            LocalTime endeZeit,
            boolean ganztaegig,
            String farbe,
            Long projektId,
            Long kundeId,
            Long lieferantId,
            Long anfrageId,
            List<Long> teilnehmerIds) {
    }

    /** Betrachter aus der Anmeldung – nie aus Request-Werten. */
    public KalenderBetrachter betrachter(Authentication authentication) {
        FrontendUserPrincipal principal = FrontendUserPrincipal.aus(authentication);
        boolean admin = principal != null && principal.hasRole(FrontendUserRole.ADMIN);
        Long mitarbeiterId = angemeldeterMitarbeiterService.handelnderMitarbeiter(authentication)
                .map(Mitarbeiter::getId)
                .orElse(null);
        return new KalenderBetrachter(mitarbeiterId, admin);
    }

    /**
     * Termine im Zeitraum, die der Betrachter sehen darf. Nur Admins können über
     * {@code mitarbeiterId} den Kalender eines anderen Mitarbeiters ansehen; allen
     * anderen wird der Wert ignoriert.
     */
    @Transactional(readOnly = true)
    public List<KalenderEintrag> sichtbareEintraege(KalenderBetrachter betrachter, LocalDate von, LocalDate bis,
            Long mitarbeiterId) {
        pruefeZeitraum(von, bis);
        if (betrachter.admin()) {
            return mitarbeiterId != null
                    ? kalenderEintragRepository.findByMitarbeiterAndDatumBetween(mitarbeiterId, von, bis)
                    : kalenderEintragRepository.findByDatumBetween(von, bis);
        }
        if (betrachter.mitarbeiterId() != null) {
            return kalenderEintragRepository.findByMitarbeiterAndDatumBetween(betrachter.mitarbeiterId(), von, bis);
        }
        return kalenderEintragRepository.findFirmenEintraegeBetween(von, bis);
    }

    /** Erster bis letzter Tag eines Monats, mit verständlicher Meldung bei ungültigem Monat. */
    public static LocalDate[] monatsZeitraum(int jahr, int monat) {
        if (monat < 1 || monat > 12 || jahr < 1900 || jahr > 9999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte einen gültigen Monat wählen.");
        }
        YearMonth yearMonth = YearMonth.of(jahr, monat);
        return new LocalDate[] { yearMonth.atDay(1), yearMonth.atEndOfMonth() };
    }

    /**
     * Lädt alle Kalendereinträge für einen Mitarbeiter in einem Monat (Handy-App).
     * Zeigt Termine wo Mitarbeiter Ersteller oder Teilnehmer ist, plus Firmenkalender-Einträge.
     */
    public List<KalenderEintrag> getEintraegeForMitarbeiter(Long mitarbeiterId, int jahr, int monat) {
        LocalDate[] zeitraum = monatsZeitraum(jahr, monat);
        return kalenderEintragRepository.findByMitarbeiterAndDatumBetween(mitarbeiterId, zeitraum[0], zeitraum[1]);
    }

    /**
     * Lädt alle Kalendereinträge für einen Mitarbeiter an einem bestimmten Tag (Handy-App).
     */
    public List<KalenderEintrag> getEintraegeForMitarbeiterTag(Long mitarbeiterId, LocalDate datum) {
        return kalenderEintragRepository.findByMitarbeiterAndDatum(mitarbeiterId, datum);
    }

    /** Ein Termin, den der Betrachter sehen darf – sonst 404 (kein Hinweis, dass es ihn gibt). */
    @Transactional(readOnly = true)
    public KalenderEintrag sichtbarerEintrag(Long id, KalenderBetrachter betrachter) {
        KalenderEintrag eintrag = id == null || id <= 0 ? null : kalenderEintragRepository.findByIdWithTeilnehmer(id);
        if (eintrag == null || !istSichtbar(eintrag, betrachter)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NICHT_GEFUNDEN);
        }
        return eintrag;
    }

    public boolean istSichtbar(KalenderEintrag eintrag, KalenderBetrachter betrachter) {
        return betrachter.admin() || istFirmentermin(eintrag) || istBeteiligt(eintrag, betrachter.mitarbeiterId());
    }

    public boolean darfBearbeiten(KalenderEintrag eintrag, KalenderBetrachter betrachter) {
        return betrachter.admin() || istBeteiligt(eintrag, betrachter.mitarbeiterId());
    }

    /** Neuer Termin; Ersteller ist der angemeldete Mitarbeiter (ohne Zuordnung: keiner). */
    @Transactional
    public KalenderEintrag erstellen(TerminDaten daten, KalenderBetrachter betrachter) {
        pruefe(daten);
        KalenderEintrag eintrag = new KalenderEintrag();
        if (betrachter.mitarbeiterId() != null) {
            eintrag.setErsteller(mitarbeiterRepository.findById(betrachter.mitarbeiterId()).orElse(null));
        }
        return uebernehmeUndSpeichere(eintrag, daten, betrachter);
    }

    @Transactional
    public KalenderEintrag aktualisieren(Long id, TerminDaten daten, KalenderBetrachter betrachter) {
        KalenderEintrag eintrag = bearbeitbarerEintrag(id, betrachter);
        pruefe(daten);
        return uebernehmeUndSpeichere(eintrag, daten, betrachter);
    }

    @Transactional
    public void loeschen(Long id, KalenderBetrachter betrachter) {
        kalenderEintragRepository.delete(bearbeitbarerEintrag(id, betrachter));
    }

    /**
     * Lädt Einträge für ein bestimmtes Projekt.
     */
    public List<KalenderEintrag> getEintraegeForProjekt(Long projektId) {
        return kalenderEintragRepository.findByProjektIdOrderByDatumDesc(projektId);
    }

    private KalenderEintrag bearbeitbarerEintrag(Long id, KalenderBetrachter betrachter) {
        KalenderEintrag eintrag = sichtbarerEintrag(id, betrachter);
        if (!darfBearbeiten(eintrag, betrachter)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, KEIN_RECHT);
        }
        return eintrag;
    }

    private static boolean istFirmentermin(KalenderEintrag eintrag) {
        return eintrag.getErsteller() == null
                && (eintrag.getTeilnehmer() == null || eintrag.getTeilnehmer().isEmpty());
    }

    private static boolean istBeteiligt(KalenderEintrag eintrag, Long mitarbeiterId) {
        if (mitarbeiterId == null) {
            return false;
        }
        if (eintrag.getErsteller() != null && mitarbeiterId.equals(eintrag.getErsteller().getId())) {
            return true;
        }
        return eintrag.getTeilnehmer() != null
                && eintrag.getTeilnehmer().stream().anyMatch(t -> mitarbeiterId.equals(t.getId()));
    }

    private static void pruefeZeitraum(LocalDate von, LocalDate bis) {
        if (von == null || bis == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte einen Zeitraum angeben.");
        }
        if (bis.isBefore(von)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Das Ende des Zeitraums liegt vor dem Anfang.");
        }
        if (ChronoUnit.DAYS.between(von, bis) > MAX_ZEITRAUM_TAGE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Der Zeitraum ist zu groß. Bitte höchstens " + MAX_ZEITRAUM_TAGE + " Tage auf einmal laden.");
        }
    }

    private static void pruefe(TerminDaten daten) {
        if (daten == null || daten.titel() == null || daten.titel().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte geben Sie einen Titel ein.");
        }
        if (daten.titel().trim().length() > MAX_TITEL_LAENGE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Der Titel ist zu lang (höchstens " + MAX_TITEL_LAENGE + " Zeichen).");
        }
        if (daten.beschreibung() != null && daten.beschreibung().length() > MAX_BESCHREIBUNG_LAENGE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Die Beschreibung ist zu lang (höchstens " + MAX_BESCHREIBUNG_LAENGE + " Zeichen).");
        }
        if (daten.datum() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte ein Datum wählen.");
        }
        if (!daten.ganztaegig() && daten.startZeit() != null && daten.endeZeit() != null
                && daten.endeZeit().isBefore(daten.startZeit())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Das Ende liegt vor dem Beginn. Bitte prüfen Sie die Uhrzeiten.");
        }
        if (daten.farbe() != null && !daten.farbe().isEmpty() && !FARBE.matcher(daten.farbe()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Diese Farbe kennt der Kalender nicht.");
        }
        if (daten.teilnehmerIds() != null && daten.teilnehmerIds().size() > MAX_TEILNEHMER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zu viele Teilnehmer ausgewählt.");
        }
    }

    private KalenderEintrag uebernehmeUndSpeichere(KalenderEintrag eintrag, TerminDaten daten,
            KalenderBetrachter betrachter) {
        eintrag.setTitel(daten.titel().trim());
        eintrag.setBeschreibung(daten.beschreibung());
        eintrag.setDatum(daten.datum());
        eintrag.setGanztaegig(daten.ganztaegig());
        eintrag.setStartZeit(daten.ganztaegig() ? null : daten.startZeit());
        eintrag.setEndeZeit(daten.ganztaegig() ? null : daten.endeZeit());
        eintrag.setFarbe(daten.farbe());

        eintrag.setProjekt(daten.projektId() != null ? projektRepository.findById(daten.projektId()).orElse(null) : null);
        eintrag.setKunde(daten.kundeId() != null ? kundeRepository.findById(daten.kundeId()).orElse(null) : null);
        KundeDatenschutzService.pruefeNichtGesperrt(eintrag.getKunde());
        eintrag.setLieferant(daten.lieferantId() != null
                ? lieferantenRepository.findById(daten.lieferantId()).orElse(null)
                : null);
        eintrag.setAnfrage(daten.anfrageId() != null ? anfrageRepository.findById(daten.anfrageId()).orElse(null) : null);

        // null = unverändert (ältere Aufrufer), leere Liste = alle Teilnehmer entfernen
        if (daten.teilnehmerIds() != null) {
            Set<Mitarbeiter> teilnehmer = new HashSet<>(mitarbeiterRepository.findAllById(
                    daten.teilnehmerIds().stream().filter(Objects::nonNull).distinct().toList()));
            eintrag.setTeilnehmer(teilnehmer);
        }
        // Ein Firmentermin erscheint bei allen – den darf nur ein Admin entstehen lassen,
        // sonst würde ein privater Termin durch Entfernen aller Teilnehmer betriebsweit sichtbar.
        if (!betrachter.admin() && istFirmentermin(eintrag)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, FIRMENTERMIN_NUR_ADMIN);
        }
        return kalenderEintragRepository.save(eintrag);
    }
}
