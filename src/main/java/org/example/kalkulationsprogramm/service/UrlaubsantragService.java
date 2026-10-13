package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.example.kalkulationsprogramm.dto.UrlaubsentscheidungDto;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlaubsantragService {

    private final UrlaubsantragRepository repository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final AbwesenheitRepository abwesenheitRepository;
    private final FeiertagService feiertagService;
    private final ZeitkontoService zeitkontoService;
    private final MonatsSaldoService monatsSaldoService;
    private final TagesSollService tagesSollService;
    private final LangzeitkrankmeldungService langzeitkrankmeldungService;
    private final UrlaubskontoService urlaubskontoService;
    private final WebPushService webPushService;

    private static final DateTimeFormatter TAG_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * Erstellt einen neuen Urlaubsantrag.
     */
    @Transactional
    public Urlaubsantrag createAntrag(Long mitarbeiterId, LocalDate von, LocalDate bis, String bemerkung,
            Urlaubsantrag.Typ typ) {
        Mitarbeiter mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId)
                .orElseThrow(() -> new IllegalArgumentException("Mitarbeiter nicht gefunden"));

        boolean istGf = Boolean.TRUE.equals(mitarbeiter.getIstGeschaeftsfuehrer());

        // Urlaubskontingent prüfen (nur für URLAUB-Typ bei normalen Mitarbeitern)
        if (!istGf && typ == Urlaubsantrag.Typ.URLAUB) {
            // Stichtag = erster Urlaubstag: Liegt er nach dem Verfall, zählt der Vorjahresrest nicht mehr.
            BigDecimal verbleibend = urlaubskontoService.berechne(mitarbeiter, von.getYear(),
                    von.isAfter(LocalDate.now()) ? von : LocalDate.now()).verbleibend();
            long beantragteTage = zaehleArbeitstage(von, bis);
            if (BigDecimal.valueOf(beantragteTage).compareTo(verbleibend) > 0) {
                throw new IllegalStateException(
                        String.format("Nicht genügend Urlaubstage. Verbleibend: %s, Beantragt: %d",
                                UrlaubstageRechner.formatTage(verbleibend), beantragteTage));
            }
        }

        // Überlappungsprüfung: Keine überschneidenden Anträge erlauben
        List<Urlaubsantrag> ueberlappend = repository.findOverlapping(mitarbeiterId, von, bis);
        if (!ueberlappend.isEmpty()) {
            Urlaubsantrag erster = ueberlappend.getFirst();
            throw new IllegalStateException(
                    String.format("Es existiert bereits ein %s-Antrag vom %s bis %s in diesem Zeitraum",
                            erster.getTyp().name(), erster.getVonDatum(), erster.getBisDatum()));
        }

        Urlaubsantrag antrag = new Urlaubsantrag();
        antrag.setMitarbeiter(mitarbeiter);
        antrag.setVonDatum(von);
        antrag.setBisDatum(bis);
        antrag.setBemerkung(bemerkung);
        antrag.setTyp(typ != null ? typ : Urlaubsantrag.Typ.URLAUB);
        antrag.setStatus(istGf ? Urlaubsantrag.Status.GENEHMIGT : Urlaubsantrag.Status.OFFEN);

        Urlaubsantrag savedAntrag = repository.save(antrag);

        // Bei Geschäftsführern wird der Urlaub direkt genehmigt und Abwesenheiten mit 0 Stunden erfasst
        if (istGf) {
            AbwesenheitsTyp abwesenheitsTyp = toAbwesenheitsTyp(savedAntrag.getTyp());
            List<LocalDate> buchungstage = von.datesUntil(bis.plusDays(1))
                    .filter(tag -> tag.getDayOfWeek() != DayOfWeek.SATURDAY && tag.getDayOfWeek() != DayOfWeek.SUNDAY)
                    .filter(tag -> !feiertagService.istFeiertag(tag))
                    .toList();

            for (LocalDate date : buchungstage) {
                if (!abwesenheitRepository.existsByMitarbeiterIdAndDatumAndTyp(
                        mitarbeiterId, date, abwesenheitsTyp)) {
                    Abwesenheit abwesenheit = new Abwesenheit();
                    abwesenheit.setMitarbeiter(mitarbeiter);
                    abwesenheit.setUrlaubsantrag(savedAntrag);
                    abwesenheit.setTyp(abwesenheitsTyp);
                    abwesenheit.setDatum(date);
                    abwesenheit.setStunden(BigDecimal.ZERO);
                    abwesenheit.setNotiz(savedAntrag.getTyp().name() + " (GF-Eintrag)");
                    abwesenheitRepository.save(abwesenheit);
                }
            }
            invalidiereBetroffeneMonate(mitarbeiterId, von, bis);
        }

        return savedAntrag;
    }

    /**
     * Konvertiert Urlaubsantrag.Typ zu AbwesenheitsTyp.
     */
    private AbwesenheitsTyp toAbwesenheitsTyp(Urlaubsantrag.Typ typ) {
        return switch (typ) {
            case URLAUB -> AbwesenheitsTyp.URLAUB;
            case KRANKHEIT -> AbwesenheitsTyp.KRANKHEIT;
            case FORTBILDUNG -> AbwesenheitsTyp.FORTBILDUNG;
            case ZEITAUSGLEICH -> AbwesenheitsTyp.ZEITAUSGLEICH;
            default -> AbwesenheitsTyp.URLAUB; // Fallback für ARBEIT, PAUSE (sollte nicht vorkommen)
        };
    }

    /**
     * Genehmigt einen Urlaubsantrag und erstellt entsprechende
     * Abwesenheits-Einträge.
     *
     * Die Urlaubsstunden je Tag sind die Gegenbuchung zum Tagessoll (siehe
     * {@link TagesSollService}) und folgen deshalb während einer laufenden
     * Wiedereingliederung dem Stufenplan statt der vollen Sollstunden — sonst
     * entstünden Phantom-Überstunden. Das Urlaubs<b>kontingent</b> zählt
     * davon unberührt weiterhin volle Tage, nicht Stunden ({@link #getResturlaub}).
     */
    @Transactional
    public Urlaubsantrag approveAntrag(Long antragId) {
        Urlaubsantrag antrag = repository.findById(antragId)
                .orElseThrow(() -> new IllegalArgumentException("Antrag nicht gefunden"));

        if (antrag.getStatus() != Urlaubsantrag.Status.OFFEN) {
            throw new IllegalStateException("Nur offene Anträge können genehmigt werden");
        }

        // AbwesenheitsTyp ermitteln
        AbwesenheitsTyp abwesenheitsTyp = toAbwesenheitsTyp(antrag.getTyp());

        boolean istGf = Boolean.TRUE.equals(antrag.getMitarbeiter().getIstGeschaeftsfuehrer());

        List<LocalDate> buchungstage = antrag.getVonDatum().datesUntil(antrag.getBisDatum().plusDays(1))
                .filter(tag -> tag.getDayOfWeek() != DayOfWeek.SATURDAY && tag.getDayOfWeek() != DayOfWeek.SUNDAY)
                .filter(tag -> !feiertagService.istFeiertag(tag))
                .toList();

        if (!istGf) {
            // Vertragsdaten einmal laden und alle Buchungstage vor der ersten Änderung prüfen.
            // Der heutige Kontoschalter darf historische Buchungen nicht verhindern.
            List<ZeitkontoVersion> versionen = zeitkontoService.versionenImZeitraum(
                    antrag.getMitarbeiter().getId(), antrag.getVonDatum(), antrag.getBisDatum());
            for (LocalDate tag : buchungstage) {
                boolean konfiguriert = versionen.stream().anyMatch(v -> !v.getGueltigVon().isAfter(tag)
                        && (v.getGueltigBis() == null || !v.getGueltigBis().isBefore(tag)));
                if (!konfiguriert) {
                    throw new IllegalStateException("Für den " + tag
                            + " ist noch keine Arbeitszeit hinterlegt. Bitte zuerst Arbeitszeit zuweisen.");
                }
            }
        }

        pruefeMonateNichtFestgeschrieben(antrag);

        // Soll-Stunden für den GESAMTEN Zeitraum auf einmal laden
        Map<LocalDate, BigDecimal> sollStundenJeTag = istGf
                ? Map.of()
                : tagesSollService.arbeitsSollJeTag(
                        antrag.getMitarbeiter().getId(), antrag.getVonDatum(), antrag.getBisDatum());

        // Zeitraum iterieren und Abwesenheiten erstellen
        for (LocalDate date : buchungstage) {
            BigDecimal sollStunden = istGf ? BigDecimal.ZERO : sollStundenJeTag.getOrDefault(date, BigDecimal.ZERO);

            if (istGf || sollStunden.compareTo(BigDecimal.ZERO) > 0) {
                // Prüfen ob bereits Abwesenheit für diesen Tag existiert
                if (!abwesenheitRepository.existsByMitarbeiterIdAndDatumAndTyp(
                        antrag.getMitarbeiter().getId(), date, abwesenheitsTyp)) {

                    Abwesenheit abwesenheit = new Abwesenheit();
                    abwesenheit.setMitarbeiter(antrag.getMitarbeiter());
                    abwesenheit.setUrlaubsantrag(antrag);
                    abwesenheit.setTyp(abwesenheitsTyp);
                    abwesenheit.setDatum(date);
                    abwesenheit.setStunden(sollStunden);
                    abwesenheit.setNotiz(antrag.getTyp().name() + (istGf ? " (GF-Eintrag)" : (" (Antrag #" + antrag.getId() + ")")));

                    abwesenheitRepository.save(abwesenheit);
                }
            }
        }

        antrag.setStatus(Urlaubsantrag.Status.GENEHMIGT);
        markiereEntscheidung(antrag);

        // MonatsSaldo-Cache invalidieren für alle betroffenen Monate
        invalidiereBetroffeneMonate(antrag.getMitarbeiter().getId(),
                antrag.getVonDatum(), antrag.getBisDatum());

        Urlaubsantrag gespeichert = repository.save(antrag);
        benachrichtigeMitarbeiter(antrag, "genehmigt");
        return gespeichert;
    }

    /**
     * Lehnt einen Urlaubsantrag ab.
     */
    @Transactional
    public Urlaubsantrag rejectAntrag(Long antragId) {
        Urlaubsantrag antrag = repository.findById(antragId)
                .orElseThrow(() -> new IllegalArgumentException("Antrag nicht gefunden"));

        if (antrag.getStatus() != Urlaubsantrag.Status.OFFEN) {
            throw new IllegalStateException("Nur offene Anträge können abgelehnt werden");
        }
        antrag.setStatus(Urlaubsantrag.Status.ABGELEHNT);
        markiereEntscheidung(antrag);
        Urlaubsantrag gespeichert = repository.save(antrag);
        benachrichtigeMitarbeiter(antrag, "abgelehnt");
        return gespeichert;
    }

    /**
     * Storniert einen Urlaubsantrag (Büro, PC): offene und genehmigte Anträge.
     * Zugehörige Abwesenheiten werden gelöscht, der Resturlaub steigt wieder.
     *
     * @throws IllegalStateException   bereits storniert oder abgelehnt
     * @throws ResponseStatusException 409, wenn ein betroffener Monat festgeschrieben ist
     */
    @Transactional
    public Urlaubsantrag stornoAntrag(Long antragId) {
        Urlaubsantrag antrag = repository.findById(antragId)
                .orElseThrow(() -> new IllegalArgumentException("Antrag nicht gefunden"));
        pruefeStornierbar(antrag);
        markiereEntscheidung(antrag);
        Urlaubsantrag gespeichert = storniere(antrag);
        benachrichtigeMitarbeiter(antrag, "vom Büro storniert");
        return gespeichert;
    }

    /**
     * Zurückziehen am Handy: nur der eigene Antrag und nur, solange er offen ist.
     * Genehmigten Urlaub nimmt das Büro zurück, weil dabei gebuchte Tage aus
     * dem Kalender verschwinden.
     *
     * @throws ResponseStatusException 403 bei fremdem Antrag
     * @throws IllegalStateException   wenn der Antrag nicht mehr offen ist
     */
    @Transactional
    public Urlaubsantrag ziehEigenenAntragZurueck(Long antragId, Long mitarbeiterId) {
        Urlaubsantrag antrag = repository.findById(antragId)
                .orElseThrow(() -> new IllegalArgumentException("Antrag nicht gefunden"));
        if (mitarbeiterId == null || !mitarbeiterId.equals(antrag.getMitarbeiter().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Zugriff verweigert.");
        }
        pruefeStornierbar(antrag);
        if (antrag.getStatus() == Urlaubsantrag.Status.GENEHMIGT) {
            throw new IllegalStateException("Genehmigten Urlaub nimmt das Büro zurück. Bitte dort melden.");
        }
        return storniere(antrag);
    }

    private void pruefeStornierbar(Urlaubsantrag antrag) {
        if (antrag.getStatus() == Urlaubsantrag.Status.STORNIERT) {
            throw new IllegalStateException("Dieser Antrag ist bereits storniert.");
        }
        if (antrag.getStatus() == Urlaubsantrag.Status.ABGELEHNT) {
            throw new IllegalStateException("Ein abgelehnter Antrag kann nicht storniert werden.");
        }
    }

    private Urlaubsantrag storniere(Urlaubsantrag antrag) {
        Long antragId = antrag.getId();
        if (antrag.getStatus() == Urlaubsantrag.Status.GENEHMIGT) {
            // Nur genehmigte Anträge haben Tage im Kalender, die in einem
            // abgeschlossenen Monat liegen könnten.
            pruefeMonateNichtFestgeschrieben(antrag);
        }

        // Lösche alle zugehörigen Abwesenheiten
        abwesenheitRepository.deleteByUrlaubsantragId(antragId);

        // MonatsSaldo-Cache invalidieren für alle betroffenen Monate
        invalidiereBetroffeneMonate(antrag.getMitarbeiter().getId(),
                antrag.getVonDatum(), antrag.getBisDatum());

        antrag.setStatus(Urlaubsantrag.Status.STORNIERT);
        return repository.save(antrag);
    }

    /**
     * Invalidiert MonatsSaldo-Cache für alle Monate in einem Datumsbereich.
     */
    private void invalidiereBetroffeneMonate(Long mitarbeiterId, LocalDate von, LocalDate bis) {
        java.time.YearMonth start = java.time.YearMonth.from(von);
        java.time.YearMonth end = java.time.YearMonth.from(bis);
        for (java.time.YearMonth ym = start; !ym.isAfter(end); ym = ym.plusMonths(1)) {
            monatsSaldoService.invalidiereMonat(mitarbeiterId, ym.getYear(), ym.getMonthValue());
        }
    }

    /**
     * Genehmigen oder Stornieren schreibt Abwesenheiten in die betroffenen
     * Monate – das darf in einem festgeschriebenen Monat nicht still passieren.
     */
    private void pruefeMonateNichtFestgeschrieben(Urlaubsantrag antrag) {
        Long mitarbeiterId = antrag.getMitarbeiter().getId();
        YearMonth start = YearMonth.from(antrag.getVonDatum());
        YearMonth end = YearMonth.from(antrag.getBisDatum());
        for (YearMonth ym = start; !ym.isAfter(end); ym = ym.plusMonths(1)) {
            if (monatsSaldoService.isMonatFestgeschrieben(mitarbeiterId, ym.getYear(), ym.getMonthValue())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, String.format(
                        "Der Monat %02d/%d ist bereits festgeschrieben. Bitte zuerst den Monatsabschluss zurücksetzen.",
                        ym.getMonthValue(), ym.getYear()));
            }
        }
    }

    private void markiereEntscheidung(Urlaubsantrag antrag) {
        antrag.setEntschiedenAm(LocalDateTime.now());
        antrag.setEntscheidungGesehen(false);
    }

    /**
     * Push an die Geräte des Mitarbeiters – erst NACH dem Commit. So bekommt
     * niemand „genehmigt" aufs Handy, wenn das Speichern noch scheitert, und
     * die Sperre auf den Mitarbeiter (Saldo-Neuberechnung) hängt nicht an
     * langsamen Push-Diensten. Die Handy-App zeigt die Entscheidung zusätzlich
     * in der Glocke.
     *
     * <p>Der Titel bleibt neutral („Antrag genehmigt"), weil er auf dem
     * Sperrbildschirm steht; die Art steht erst im Text.
     */
    private void benachrichtigeMitarbeiter(Urlaubsantrag antrag, String entscheidung) {
        Long mitarbeiterId = antrag.getMitarbeiter().getId();
        String titel = "Antrag " + entscheidung;
        String text = String.format("%s vom %s bis %s wurde %s.",
                antragMitArtikel(antrag.getTyp()),
                antrag.getVonDatum().format(TAG_FORMAT),
                antrag.getBisDatum().format(TAG_FORMAT),
                entscheidung);
        Runnable senden = () -> webPushService.notifyMitarbeiter(mitarbeiterId, titel, text, "/zeiterfassung/urlaub");
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // Spring reicht Fehler aus afterCommit an den Aufrufer durch – die
                    // Entscheidung ist aber schon gespeichert und darf nicht als 500 enden.
                    try {
                        senden.run();
                    } catch (RuntimeException e) {
                        log.warn("Push nach Entscheidung fehlgeschlagen: {}", e.getMessage());
                    }
                }
            });
        } else {
            senden.run();
        }
    }

    /** „Dein Urlaub", „Deine Krankmeldung" … – Subjekt für Meldungen an den Mitarbeiter. */
    static String antragMitArtikel(Urlaubsantrag.Typ typ) {
        if (typ == null) return "Dein Urlaub";
        return switch (typ) {
            case URLAUB -> "Dein Urlaub";
            case KRANKHEIT -> "Deine Krankmeldung";
            case FORTBILDUNG -> "Deine Fortbildung";
            case ZEITAUSGLEICH -> "Dein Zeitausgleich";
            default -> "Dein Antrag";
        };
    }

    /** Entschiedene Anträge, die der Mitarbeiter noch nicht mit „Verstanden" bestätigt hat. */
    @Transactional(readOnly = true)
    public List<UrlaubsentscheidungDto> neueEntscheidungen(Long mitarbeiterId) {
        return repository.findUngeseheneEntscheidungen(mitarbeiterId,
                EnumSet.of(Urlaubsantrag.Status.GENEHMIGT, Urlaubsantrag.Status.ABGELEHNT,
                        Urlaubsantrag.Status.STORNIERT))
                .stream()
                .map(UrlaubsentscheidungDto::from)
                .toList();
    }

    /**
     * Eine einzelne Entscheidung als gelesen markieren – Handy: nur der eigene Antrag.
     *
     * @throws IllegalArgumentException unbekannter Antrag
     * @throws ResponseStatusException  403 bei fremdem Antrag oder fehlendem Mitarbeiter
     */
    @Transactional
    public void eigeneEntscheidungGesehen(Long antragId, Long mitarbeiterId) {
        Urlaubsantrag antrag = repository.findById(antragId)
                .orElseThrow(() -> new IllegalArgumentException("Antrag nicht gefunden"));
        if (mitarbeiterId == null || !mitarbeiterId.equals(antrag.getMitarbeiter().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Zugriff verweigert.");
        }
        markiereGesehen(antrag);
    }

    /** Büro-Anmeldung (PC): jede Entscheidung darf als gelesen markiert werden. */
    @Transactional
    public void entscheidungGesehenImBuero(Long antragId) {
        markiereGesehen(repository.findById(antragId)
                .orElseThrow(() -> new IllegalArgumentException("Antrag nicht gefunden")));
    }

    private void markiereGesehen(Urlaubsantrag antrag) {
        if (!antrag.isEntscheidungGesehen()) {
            antrag.setEntscheidungGesehen(true);
            repository.save(antrag);
        }
    }

    public List<Urlaubsantrag> getOffeneAntraege() {
        return repository.findByStatus(Urlaubsantrag.Status.OFFEN);
    }

    /**
     * Gibt alle Anträge mit einem bestimmten Status zurück.
     */
    public List<Urlaubsantrag> getAntraegeByStatus(Urlaubsantrag.Status status) {
        return repository.findByStatus(status);
    }

    public List<Urlaubsantrag> getAntraegeByMitarbeiter(Long mitarbeiterId) {
        return repository.findByMitarbeiterIdOrderByVonDatumDesc(mitarbeiterId);
    }

    /**
     * Gibt Anträge eines Mitarbeiters für ein bestimmtes Jahr zurück.
     */
    public List<Urlaubsantrag> getAntraegeByMitarbeiterAndYear(Long mitarbeiterId, int jahr) {
        LocalDate start = LocalDate.of(jahr, 1, 1);
        LocalDate end = LocalDate.of(jahr, 12, 31);
        return repository.findByMitarbeiterIdAndVonDatumBetweenOrderByVonDatumDesc(mitarbeiterId, start, end);
    }

    /**
     * Gibt Anträge eines Mitarbeiters mit einem bestimmten Status zurück.
     */
    public List<Urlaubsantrag> getAntraegeByMitarbeiterAndStatus(Long mitarbeiterId, Urlaubsantrag.Status status) {
        return repository.findByMitarbeiterIdAndStatusOrderByVonDatumDesc(mitarbeiterId, status);
    }

    /**
     * Verbleibende Urlaubstage eines Mitarbeiters für ein Jahr (heutiger Stand,
     * inklusive Resturlaub aus dem Vorjahr). Rechnung: {@link UrlaubskontoService}.
     */
    @Transactional(readOnly = true)
    public BigDecimal getResturlaub(Long mitarbeiterId, int jahr) {
        return getUrlaubskonto(mitarbeiterId, jahr).verbleibend();
    }

    /** Das ganze Urlaubskonto eines Jahres – mit Übertrag und Verfall für die Anzeige. */
    @Transactional(readOnly = true)
    public Urlaubskonto getUrlaubskonto(Long mitarbeiterId, int jahr) {
        Mitarbeiter mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId)
                .orElseThrow(() -> new IllegalArgumentException("Mitarbeiter nicht gefunden"));
        return urlaubskontoService.berechne(mitarbeiter, jahr);
    }

    /**
     * Prüft, ob der angefragte Urlaubszeitraum in eine laufende Krankmeldung
     * fällt, und liefert dazu Hinweistexte fürs Büro. Das ist eine Warnung,
     * keine Sperre — der Antrag wird dadurch nicht blockiert, das Büro
     * entscheidet.
     */
    @Transactional(readOnly = true)
    public List<String> pruefeHinweise(Long mitarbeiterId, LocalDate von, LocalDate bis) {
        return langzeitkrankmeldungService.pruefeUrlaubsHinweise(mitarbeiterId, von, bis);
    }

    /**
     * Zählt Arbeitstage (Mo–Fr, ohne Feiertage) in einem Zeitraum.
     */
    private long zaehleArbeitstage(LocalDate von, LocalDate bis) {
        long count = 0;
        for (LocalDate d = von; !d.isAfter(bis); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            if (feiertagService.istFeiertag(d)) continue;
            count++;
        }
        return count;
    }
}
