package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.Langzeitkrankmeldung.LangzeitkrankmeldungDto;
import org.example.kalkulationsprogramm.dto.Langzeitkrankmeldung.LangzeitkrankmeldungPhaseDto;
import org.example.kalkulationsprogramm.dto.Langzeitkrankmeldung.StufenplanTagDto;
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository;
import org.example.kalkulationsprogramm.repository.LangzeitkrankmeldungPhaseRepository;
import org.example.kalkulationsprogramm.repository.LangzeitkrankmeldungRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fachlogik rund um {@link Langzeitkrankmeldung}: Anlegen, Statusuebergaenge,
 * Phasenverwaltung (Lohnfortzahlung, Krankengeld, Wiedereingliederung) und die
 * Verknuepfung mit den taeglichen {@link Abwesenheit}-Eintraegen.
 *
 * <p><b>Der 42-Tage-Countdown rechnet nichts von allein.</b> Beim Anlegen wird
 * {@code lohnfortzahlungBis = beginn + 41 Tage} gesetzt (42 Tage inklusive
 * Beginn) und die Lohnfortzahlungs-Phase angelegt. Es gibt danach keinen
 * Hintergrundjob, der Phasen automatisch weiterschaltet - dieser Service
 * liefert nur den aktuellen Stand, ein Klick des Bueros legt die naechste
 * Phase an.
 *
 * <p><b>Langzeitkrankmeldungen werden ausschliesslich am PC gepflegt</b>
 * (Vorgabe des Projektinhabers vom 08.09.2026). {@link #getMobileStand} ist
 * die einzige fuer die Handy-App gedachte Methode dieser Klasse und rein
 * lesend - sie liefert bewusst nur Phase, Label, heute geplante Stunden, seit
 * und bisDatum, nie Name oder Notiz (DSGVO).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LangzeitkrankmeldungService {

    private static final Map<LangzeitkrankmeldungPhaseTyp, String> PHASE_LABELS = Map.of(
            LangzeitkrankmeldungPhaseTyp.LOHNFORTZAHLUNG, "Lohnfortzahlung durch den Betrieb",
            LangzeitkrankmeldungPhaseTyp.KRANKENGELD, "Krankengeld der Krankenkasse",
            LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG, "Wiedereingliederung");

    private static final Map<LangzeitkrankmeldungStatus, String> STATUS_LABELS = Map.of(
            LangzeitkrankmeldungStatus.LAUFEND, "Läuft noch",
            LangzeitkrankmeldungStatus.BEENDET, "Wieder voll im Einsatz",
            LangzeitkrankmeldungStatus.ABGEBROCHEN, "Zurückgenommen");

    /**
     * Steht fuer "kein Enddatum bekannt" in der Ueberlappungspruefung
     * ({@link #pruefeKeineUeberlappung}). Bewusst NICHT {@code LocalDate.MAX}
     * (Jahr 999999999) - das sprengt den MySQL-DATE-Bereich (hoechstens
     * 9999-12-31). Je nach Servermodus wirft das einen Fehler oder liefert,
     * schlimmer, still KEINE Treffer, wodurch die Ueberlappungspruefung
     * nichts mehr prueft und zwei sich ueberschneidende Krankmeldungen
     * fuer denselben Mitarbeiter durchkaemen (Befund 1, Abschnitt-4-Review,
     * Issue #91). NICHT auf {@code LocalDate.MAX} zurueckbauen.
     */
    private static final LocalDate OFFENES_ENDE = LocalDate.of(9999, 12, 31);

    private final LangzeitkrankmeldungRepository repository;
    private final LangzeitkrankmeldungPhaseRepository phaseRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final AbwesenheitRepository abwesenheitRepository;
    private final ZeitbuchungRepository zeitbuchungRepository;
    private final ZeitkontoService zeitkontoService;
    private final TagesSollService tagesSollService;
    private final MonatsSaldoService monatsSaldoService;

    // ==================== Schreiboperationen ====================

    @Transactional
    public Langzeitkrankmeldung anlegen(Long mitarbeiterId, LocalDate beginn, LocalDate lohnfortzahlungBis,
            String notiz) {
        Mitarbeiter mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId)
                .orElseThrow(() -> new IllegalArgumentException("Mitarbeiter nicht gefunden"));

        // 42 Tage inklusive Beginn = Beginn + 41 Tage. Ueberschreibbar fuer
        // Fortsetzungserkrankungen, bei denen die Lohnfortzahlung schon frueher
        // begonnen hat.
        LocalDate lohnfortzahlungBisEndgueltig = lohnfortzahlungBis != null ? lohnfortzahlungBis
                : beginn.plusDays(41);
        if (lohnfortzahlungBisEndgueltig.isBefore(beginn)) {
            throw new IllegalArgumentException("Das Ende der Lohnfortzahlung darf nicht vor dem Beginn liegen.");
        }

        pruefeKeineUeberlappung(mitarbeiterId, beginn, OFFENES_ENDE, null);

        Langzeitkrankmeldung meldung = new Langzeitkrankmeldung();
        meldung.setMitarbeiter(mitarbeiter);
        meldung.setBeginn(beginn);
        meldung.setLohnfortzahlungBis(lohnfortzahlungBisEndgueltig);
        meldung.setStatus(LangzeitkrankmeldungStatus.LAUFEND);
        meldung.setNotiz(pruefeNotiz(notiz));

        LangzeitkrankmeldungPhase lohnfortzahlung = new LangzeitkrankmeldungPhase();
        lohnfortzahlung.setLangzeitkrankmeldung(meldung);
        lohnfortzahlung.setTyp(LangzeitkrankmeldungPhaseTyp.LOHNFORTZAHLUNG);
        lohnfortzahlung.setVonDatum(beginn);
        lohnfortzahlung.setBisDatum(lohnfortzahlungBisEndgueltig);
        meldung.getPhasen().add(lohnfortzahlung);

        Langzeitkrankmeldung gespeichert = speichernUndFolgeaktionen(meldung);
        log.info("Langzeitkrankmeldung {} angelegt für Mitarbeiter {}", gespeichert.getId(), mitarbeiterId);
        return gespeichert;
    }

    @Transactional
    public Langzeitkrankmeldung aendern(Long id, LocalDate beginn, LocalDate lohnfortzahlungBis, String notiz) {
        Langzeitkrankmeldung meldung = ladeMitPhasen(id);

        if (beginn != null) {
            meldung.setBeginn(beginn);
        }
        if (lohnfortzahlungBis != null) {
            if (lohnfortzahlungBis.isBefore(meldung.getBeginn())) {
                throw new IllegalArgumentException("Das Ende der Lohnfortzahlung darf nicht vor dem Beginn liegen.");
            }
            meldung.setLohnfortzahlungBis(lohnfortzahlungBis);
        }
        meldung.setNotiz(pruefeNotiz(notiz));

        pruefeKeineUeberlappung(meldung.getMitarbeiter().getId(), meldung.getBeginn(),
                meldung.getEnde() != null ? meldung.getEnde() : OFFENES_ENDE, id);

        Langzeitkrankmeldung gespeichert = speichernUndFolgeaktionen(meldung);
        log.info("Langzeitkrankmeldung {} geändert für Mitarbeiter {}", id, gespeichert.getMitarbeiter().getId());
        return gespeichert;
    }

    @Transactional
    public Langzeitkrankmeldung beenden(Long id, LocalDate ende) {
        Langzeitkrankmeldung meldung = ladeMitPhasen(id);
        if (meldung.getStatus() != LangzeitkrankmeldungStatus.LAUFEND) {
            throw new IllegalStateException("Nur eine laufende Krankmeldung kann beendet werden.");
        }
        if (ende.isBefore(meldung.getBeginn())) {
            throw new IllegalArgumentException("Das Ende darf nicht vor dem Beginn liegen.");
        }

        meldung.setEnde(ende);
        for (LangzeitkrankmeldungPhase phase : meldung.getPhasen()) {
            if (phase.getBisDatum() == null) {
                phase.setBisDatum(ende);
            }
        }
        meldung.setStatus(LangzeitkrankmeldungStatus.BEENDET);

        Langzeitkrankmeldung gespeichert = speichernUndFolgeaktionen(meldung);
        log.info("Langzeitkrankmeldung {} beendet für Mitarbeiter {}", id, gespeichert.getMitarbeiter().getId());
        return gespeichert;
    }

    @Transactional
    public Langzeitkrankmeldung wiederEroeffnen(Long id) {
        Langzeitkrankmeldung meldung = ladeMitPhasen(id);
        if (meldung.getStatus() == LangzeitkrankmeldungStatus.ABGEBROCHEN) {
            throw new IllegalStateException(
                    "Eine zurückgenommene Krankmeldung lässt sich nicht wieder öffnen. Bitte neu anlegen.");
        }
        if (meldung.getStatus() != LangzeitkrankmeldungStatus.BEENDET) {
            throw new IllegalStateException("Nur eine beendete Krankmeldung kann wieder eröffnet werden.");
        }

        meldung.setEnde(null);
        pruefeKeineUeberlappung(meldung.getMitarbeiter().getId(), meldung.getBeginn(), OFFENES_ENDE, id);

        List<LangzeitkrankmeldungPhase> sortiert = sortierePhasen(meldung);
        if (!sortiert.isEmpty()) {
            sortiert.get(sortiert.size() - 1).setBisDatum(null);
        }
        meldung.setStatus(LangzeitkrankmeldungStatus.LAUFEND);

        Langzeitkrankmeldung gespeichert = speichernUndFolgeaktionen(meldung);
        log.info("Langzeitkrankmeldung {} wieder eröffnet für Mitarbeiter {}", id,
                gespeichert.getMitarbeiter().getId());
        return gespeichert;
    }

    @Transactional
    public Langzeitkrankmeldung abbrechen(Long id) {
        Langzeitkrankmeldung meldung = ladeMitPhasen(id);
        if (meldung.getStatus() != LangzeitkrankmeldungStatus.LAUFEND) {
            throw new IllegalStateException("Nur eine laufende Krankmeldung kann zurückgenommen werden.");
        }

        meldung.setStatus(LangzeitkrankmeldungStatus.ABGEBROCHEN);

        Langzeitkrankmeldung gespeichert = repository.save(meldung);
        // verknuepfeAbwesenheiten entkoppelt alle FK, weil der Status jetzt
        // ABGEBROCHEN ist (siehe dortige Fallunterscheidung).
        verknuepfeAbwesenheiten(gespeichert);
        invalidiereBetroffeneMonate(gespeichert.getMitarbeiter().getId(), gespeichert.getBeginn(),
                gespeichert.getEnde());
        log.info("Langzeitkrankmeldung {} abgebrochen für Mitarbeiter {}", id, gespeichert.getMitarbeiter().getId());
        return gespeichert;
    }

    @Transactional
    public Langzeitkrankmeldung phaseHinzufuegen(Long id, LangzeitkrankmeldungPhaseTyp typ, LocalDate vonDatum,
            LocalDate bisDatum, BigDecimal stundenProTag) {
        Langzeitkrankmeldung meldung = ladeMitPhasen(id);

        LangzeitkrankmeldungPhase phase = new LangzeitkrankmeldungPhase();
        phase.setLangzeitkrankmeldung(meldung);
        phase.setTyp(typ);
        phase.setVonDatum(vonDatum);
        phase.setBisDatum(bisDatum);
        phase.setStundenProTag(stundenProTag);
        meldung.getPhasen().add(phase);

        return speichernUndFolgeaktionen(meldung);
    }

    @Transactional
    public Langzeitkrankmeldung phaseAendern(Long id, Long phasenId, LangzeitkrankmeldungPhaseTyp typ,
            LocalDate vonDatum, LocalDate bisDatum, BigDecimal stundenProTag) {
        Langzeitkrankmeldung meldung = ladeMitPhasen(id);
        LangzeitkrankmeldungPhase phase = findePhaseInMeldung(meldung, phasenId);
        phase.setTyp(typ);
        phase.setVonDatum(vonDatum);
        phase.setBisDatum(bisDatum);
        phase.setStundenProTag(stundenProTag);

        return speichernUndFolgeaktionen(meldung);
    }

    @Transactional
    public Langzeitkrankmeldung phaseLoeschen(Long id, Long phasenId) {
        Langzeitkrankmeldung meldung = ladeMitPhasen(id);
        LangzeitkrankmeldungPhase phase = findePhaseInMeldung(meldung, phasenId);
        meldung.getPhasen().remove(phase);

        return speichernUndFolgeaktionen(meldung);
    }

    // ==================== Leseoperationen ====================

    public List<Langzeitkrankmeldung> finde(LangzeitkrankmeldungStatus status) {
        Collection<LangzeitkrankmeldungStatus> gesuchteStatus = status != null ? List.of(status)
                : List.of(LangzeitkrankmeldungStatus.values());
        return repository.findMitPhasen(gesuchteStatus);
    }

    public Langzeitkrankmeldung findeMitPhasen(Long id) {
        return ladeMitPhasen(id);
    }

    public Optional<LangzeitkrankmeldungPhase> findePhase(Long mitarbeiterId, LocalDate stichtag) {
        return phaseRepository.findImZeitraum(mitarbeiterId, stichtag, stichtag).stream().findFirst();
    }

    public int restTageLohnfortzahlung(Langzeitkrankmeldung meldung, LocalDate stichtag) {
        return (int) ChronoUnit.DAYS.between(stichtag, meldung.getLohnfortzahlungBis());
    }

    public LangzeitkrankmeldungDto toDto(Langzeitkrankmeldung meldung, boolean mitStufenplanTagen) {
        Mitarbeiter mitarbeiter = meldung.getMitarbeiter();
        LangzeitkrankmeldungDto dto = new LangzeitkrankmeldungDto();
        dto.setId(meldung.getId());
        dto.setMitarbeiterId(mitarbeiter.getId());
        dto.setMitarbeiterName(mitarbeiter.getNachname() + ", " + mitarbeiter.getVorname());
        dto.setBeginn(meldung.getBeginn());
        dto.setEnde(meldung.getEnde());
        dto.setStatus(meldung.getStatus());
        dto.setStatusLabel(STATUS_LABELS.get(meldung.getStatus()));
        dto.setLohnfortzahlungBis(meldung.getLohnfortzahlungBis());
        dto.setNotiz(meldung.getNotiz());
        dto.setVersion(meldung.getVersion());
        dto.setRestTageLohnfortzahlung(restTageLohnfortzahlung(meldung, LocalDate.now()));

        List<LangzeitkrankmeldungPhase> sortiert = sortierePhasen(meldung);
        dto.setPhasen(sortiert.stream().map(this::toPhaseDto).toList());

        LocalDate heute = LocalDate.now();
        LangzeitkrankmeldungPhase aktuellePhase = phaseAmTag(sortiert, heute);
        if (aktuellePhase != null) {
            dto.setAktuellePhaseTyp(aktuellePhase.getTyp());
            dto.setAktuellePhaseLabel(PHASE_LABELS.get(aktuellePhase.getTyp()));
        }

        dto.setGeplanteRueckkehr(geplanteRueckkehr(meldung, sortiert));

        if (aktuellePhase != null && aktuellePhase.getTyp() == LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG) {
            Zeitkonto konto = zeitkontoService.getOrCreateZeitkonto(mitarbeiter.getId());
            dto.setHeuteGeplanteStunden(tagesSollService.arbeitsSoll(mitarbeiter.getId(), konto, heute));
        }

        if (mitStufenplanTagen) {
            dto.setStufenplanTage(baueStufenplanTage(meldung, sortiert));
        }

        return dto;
    }

    /**
     * Nur fuer die Handy-App (rein lesend, siehe Klassen-Javadoc). Liegt unter
     * {@code /api/zeiterfassung/langzeitkrankmeldung/{token}} und ist damit
     * schon in der Mobile-Whitelist (SecurityConfig.java) - keine
     * SecurityConfig-Aenderung noetig. Analog zu
     * {@code ZeiterfassungApiService.getUrlaubsverfallWarnung}: unbekanntes
     * Token oder keine laufende Phase liefert ein leeres Objekt.
     */
    public Map<String, Object> getMobileStand(String loginToken, LocalDate stichtag) {
        Optional<Mitarbeiter> mitarbeiter = mitarbeiterRepository.findByLoginToken(loginToken);
        if (mitarbeiter.isEmpty()) {
            return Collections.emptyMap();
        }
        Optional<LangzeitkrankmeldungPhase> phase = findePhase(mitarbeiter.get().getId(), stichtag);
        if (phase.isEmpty()) {
            return Collections.emptyMap();
        }

        LangzeitkrankmeldungPhase aktuellePhase = phase.get();
        Langzeitkrankmeldung meldung = aktuellePhase.getLangzeitkrankmeldung();

        // Bewusst nur diese fuenf Felder - kein Name, keine Notiz (DSGVO).
        Map<String, Object> stand = new LinkedHashMap<>();
        stand.put("phase", aktuellePhase.getTyp());
        stand.put("phaseLabel", PHASE_LABELS.get(aktuellePhase.getTyp()));
        if (aktuellePhase.getTyp() == LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG) {
            Zeitkonto konto = zeitkontoService.getOrCreateZeitkonto(mitarbeiter.get().getId());
            stand.put("heuteGeplanteStunden",
                    tagesSollService.arbeitsSoll(mitarbeiter.get().getId(), konto, stichtag));
        } else {
            stand.put("heuteGeplanteStunden", null);
        }
        stand.put("seit", meldung.getBeginn());
        stand.put("bisDatum", aktuellePhase.getBisDatum());
        return stand;
    }

    public List<String> pruefeUrlaubsHinweise(Long mitarbeiterId, LocalDate von, LocalDate bis) {
        return repository.findUeberlappende(mitarbeiterId, von, bis).stream()
                .map(l -> String.format(
                        "In diesem Zeitraum läuft eine Krankmeldung (seit %s). Bitte prüfen, ob der Urlaub wirklich passt.",
                        l.getBeginn()))
                .toList();
    }

    // ==================== Interne Hilfsmethoden ====================

    private Langzeitkrankmeldung ladeMitPhasen(Long id) {
        return repository.findMitPhasenById(id)
                .orElseThrow(() -> new IllegalArgumentException("Langzeitkrankmeldung nicht gefunden"));
    }

    private LangzeitkrankmeldungPhase findePhaseInMeldung(Langzeitkrankmeldung meldung, Long phasenId) {
        return meldung.getPhasen().stream()
                .filter(p -> p.getId() != null && p.getId().equals(phasenId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Phase nicht gefunden"));
    }

    private void pruefeKeineUeberlappung(Long mitarbeiterId, LocalDate von, LocalDate bis, Long eigeneId) {
        List<Langzeitkrankmeldung> ueberlappend = repository.findUeberlappende(mitarbeiterId, von, bis).stream()
                .filter(l -> eigeneId == null || !l.getId().equals(eigeneId))
                .toList();
        if (!ueberlappend.isEmpty()) {
            Langzeitkrankmeldung erste = ueberlappend.get(0);
            throw new IllegalStateException(
                    String.format("Für diesen Mitarbeiter läuft bereits eine Krankmeldung seit %s.",
                            erste.getBeginn()));
        }
    }

    private String pruefeNotiz(String notiz) {
        if (notiz == null) {
            return null;
        }
        String getrimmt = notiz.trim();
        if (getrimmt.length() > 500) {
            throw new IllegalArgumentException("Die Notiz darf höchstens 500 Zeichen lang sein.");
        }
        return getrimmt.isEmpty() ? null : getrimmt;
    }

    /** Speichert die Meldung und stoesst die immer noetigen Folgeaktionen an. */
    private Langzeitkrankmeldung speichernUndFolgeaktionen(Langzeitkrankmeldung meldung) {
        pruefePhasen(meldung);
        Langzeitkrankmeldung gespeichert = repository.save(meldung);
        verknuepfeAbwesenheiten(gespeichert);
        invalidiereBetroffeneMonate(gespeichert.getMitarbeiter().getId(), gespeichert.getBeginn(),
                gespeichert.getEnde());
        return gespeichert;
    }

    private List<LangzeitkrankmeldungPhase> sortierePhasen(Langzeitkrankmeldung meldung) {
        return meldung.getPhasen().stream()
                .sorted(Comparator.comparing(LangzeitkrankmeldungPhase::getVonDatum))
                .toList();
    }

    /**
     * Prueft die vier Phasen-Regeln aus Abschnitt 7 der Spec: keine
     * Ueberlappung, keine Luecke, hoechstens eine offene (letzte) Phase, und
     * je Typ die richtige stundenProTag-Belegung inkl. Deckelung am
     * Zeitkonto-Tagessoll. Jede Verletzung wirft eine
     * {@link IllegalStateException} mit Klartext.
     */
    private void pruefePhasen(Langzeitkrankmeldung meldung) {
        List<LangzeitkrankmeldungPhase> phasen = sortierePhasen(meldung);

        for (int i = 0; i < phasen.size(); i++) {
            LangzeitkrankmeldungPhase phase = phasen.get(i);
            boolean istLetzte = i == phasen.size() - 1;

            if (phase.getBisDatum() == null && !istLetzte) {
                throw new IllegalStateException("Nur die letzte Phase darf offen sein (ohne Enddatum).");
            }
            pruefeStundenProTagPflicht(phase);
            pruefeStundenProTagDeckel(meldung, phase);
            pruefeInnerhalbDesZeitraums(meldung, phase);

            if (i > 0) {
                LangzeitkrankmeldungPhase vorherige = phasen.get(i - 1);
                if (!vorherige.getBisDatum().isBefore(phase.getVonDatum())) {
                    throw new IllegalStateException("Phasen dürfen sich nicht überlappen.");
                }
                if (!vorherige.getBisDatum().plusDays(1).isEqual(phase.getVonDatum())) {
                    throw new IllegalStateException("Zwischen zwei Phasen darf keine Lücke bestehen.");
                }
            }
        }
    }

    private void pruefeStundenProTagPflicht(LangzeitkrankmeldungPhase phase) {
        if (phase.getTyp() == LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG) {
            if (phase.getStundenProTag() == null || phase.getStundenProTag().signum() <= 0) {
                throw new IllegalStateException("Bitte trage ein, wie viele Stunden pro Tag geplant sind.");
            }
        } else if (phase.getStundenProTag() != null) {
            throw new IllegalStateException(
                    "Nur eine Wiedereingliederung hat Stunden pro Tag - bitte das Feld leer lassen.");
        }
    }

    /**
     * Sicherheitsnetz: die Stufenplan-Stunden duerfen an keinem Arbeitstag der
     * Phase ueber dem normalen Zeitkonto-Tagessoll liegen. Tage mit Soll 0
     * (Wochenende) werden ausgenommen - TagesSollService ignoriert die Phase
     * dort ohnehin (tagesBasis wird nie ueber ein Soll von 0 gehoben).
     */
    private void pruefeStundenProTagDeckel(Langzeitkrankmeldung meldung, LangzeitkrankmeldungPhase phase) {
        if (phase.getStundenProTag() == null) {
            return;
        }
        Zeitkonto konto = zeitkontoService.getOrCreateZeitkonto(meldung.getMitarbeiter().getId());
        for (DayOfWeek wochentag : wochentageInPhase(phase)) {
            BigDecimal sollstunden = konto.getSollstundenFuerTag(wochentag.getValue());
            if (sollstunden.signum() <= 0) {
                continue;
            }
            if (phase.getStundenProTag().compareTo(sollstunden) > 0) {
                throw new IllegalStateException(String.format(
                        "Die Stufenplan-Stunden (%s) dürfen das normale Tagessoll (%s) nicht übersteigen.",
                        phase.getStundenProTag(), sollstunden));
            }
        }
    }

    /** Alle Wochentage, die in der Phase vorkommen - hoechstens 7, auch bei offenem Ende. */
    private Set<DayOfWeek> wochentageInPhase(LangzeitkrankmeldungPhase phase) {
        long spanne = phase.getBisDatum() != null
                ? Math.min(6, ChronoUnit.DAYS.between(phase.getVonDatum(), phase.getBisDatum()))
                : 6;
        LocalDate ende = phase.getVonDatum().plusDays(spanne);
        Set<DayOfWeek> wochentage = EnumSet.noneOf(DayOfWeek.class);
        for (LocalDate tag = phase.getVonDatum(); !tag.isAfter(ende); tag = tag.plusDays(1)) {
            wochentage.add(tag.getDayOfWeek());
        }
        return wochentage;
    }

    private void pruefeInnerhalbDesZeitraums(Langzeitkrankmeldung meldung, LangzeitkrankmeldungPhase phase) {
        if (phase.getVonDatum().isBefore(meldung.getBeginn())) {
            throw new IllegalStateException("Eine Phase darf nicht vor dem Beginn der Krankmeldung liegen.");
        }
        LocalDate meldungsEnde = meldung.getEnde();
        if (meldungsEnde != null) {
            if (phase.getBisDatum() == null) {
                throw new IllegalStateException("Eine beendete Krankmeldung darf keine offene Phase haben.");
            }
            if (phase.getBisDatum().isAfter(meldungsEnde)) {
                throw new IllegalStateException("Eine Phase darf nicht über das Ende der Krankmeldung hinausgehen.");
            }
        }
    }

    /**
     * Verknuepft jede Krankheits-Abwesenheit im Zeitraum der Meldung mit der
     * an dem Tag geltenden Phase (setzt beide FK), und setzt beide FK auf
     * {@code null} zurueck fuer Tage, die zu keiner Phase (mehr) gehoeren -
     * etwa eine Luecke zwischen der letzten geschlossenen Phase und heute,
     * oder eine komplett abgebrochene Meldung. Genau EINE Abfrage, kein
     * Aufruf im Schleifenkoerper.
     */
    private void verknuepfeAbwesenheiten(Langzeitkrankmeldung meldung) {
        LocalDate bis = meldung.getEnde() != null ? meldung.getEnde() : LocalDate.now();
        if (bis.isBefore(meldung.getBeginn())) {
            return;
        }
        Long mitarbeiterId = meldung.getMitarbeiter().getId();
        List<Abwesenheit> abwesenheiten = abwesenheitRepository.findByMitarbeiterIdAndTypAndDatumBetween(
                mitarbeiterId, AbwesenheitsTyp.KRANKHEIT, meldung.getBeginn(), bis);

        Map<LocalDate, LangzeitkrankmeldungPhase> phaseNachTag = meldung.getStatus() == LangzeitkrankmeldungStatus.ABGEBROCHEN
                ? Map.of()
                : phaseNachTag(meldung, bis);

        for (Abwesenheit abwesenheit : abwesenheiten) {
            LangzeitkrankmeldungPhase phase = phaseNachTag.get(abwesenheit.getDatum());
            abwesenheit.setLangzeitkrankmeldung(phase != null ? meldung : null);
            abwesenheit.setLangzeitkrankmeldungPhase(phase);
        }
        abwesenheitRepository.saveAll(abwesenheiten);
    }

    private Map<LocalDate, LangzeitkrankmeldungPhase> phaseNachTag(Langzeitkrankmeldung meldung, LocalDate bis) {
        Map<LocalDate, LangzeitkrankmeldungPhase> ergebnis = new LinkedHashMap<>();
        for (LangzeitkrankmeldungPhase phase : meldung.getPhasen()) {
            LocalDate phasenEnde = phase.getBisDatum() != null ? phase.getBisDatum() : bis;
            for (LocalDate tag = phase.getVonDatum(); !tag.isAfter(phasenEnde); tag = tag.plusDays(1)) {
                ergebnis.put(tag, phase);
            }
        }
        return ergebnis;
    }

    private void invalidiereBetroffeneMonate(Long mitarbeiterId, LocalDate beginn, LocalDate ende) {
        LocalDate bis = ende != null ? ende : LocalDate.now();
        YearMonth start = YearMonth.from(beginn);
        YearMonth end = YearMonth.from(bis);
        for (YearMonth ym = start; !ym.isAfter(end); ym = ym.plusMonths(1)) {
            monatsSaldoService.invalidiereMonat(mitarbeiterId, ym.getYear(), ym.getMonthValue());
        }
    }

    private LangzeitkrankmeldungPhaseDto toPhaseDto(LangzeitkrankmeldungPhase phase) {
        LangzeitkrankmeldungPhaseDto dto = new LangzeitkrankmeldungPhaseDto();
        dto.setId(phase.getId());
        dto.setTyp(phase.getTyp());
        dto.setLabel(PHASE_LABELS.get(phase.getTyp()));
        dto.setVonDatum(phase.getVonDatum());
        dto.setBisDatum(phase.getBisDatum());
        dto.setStundenProTag(phase.getStundenProTag());
        return dto;
    }

    private LangzeitkrankmeldungPhase phaseAmTag(List<LangzeitkrankmeldungPhase> sortiertePhasen, LocalDate tag) {
        return sortiertePhasen.stream()
                .filter(p -> !p.getVonDatum().isAfter(tag))
                .filter(p -> p.getBisDatum() == null || !p.getBisDatum().isBefore(tag))
                .findFirst()
                .orElse(null);
    }

    private LocalDate geplanteRueckkehr(Langzeitkrankmeldung meldung, List<LangzeitkrankmeldungPhase> sortiertePhasen) {
        LangzeitkrankmeldungPhase letzteWiedereingliederung = sortiertePhasen.stream()
                .filter(p -> p.getTyp() == LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG)
                .reduce((erste, letzte) -> letzte)
                .orElse(null);
        if (letzteWiedereingliederung != null && letzteWiedereingliederung.getBisDatum() != null) {
            return letzteWiedereingliederung.getBisDatum().plusDays(1);
        }
        return meldung.getEnde();
    }

    /**
     * Baut die Stufenplan-Tage fuer alle Wiedereingliederungs-Zeitraeume der
     * Meldung. Gestempelte Stunden kommen aus EINER Abfrage ueber den
     * Gesamtzeitraum, nicht je Tag - sonst waere das N+1.
     */
    private List<StufenplanTagDto> baueStufenplanTage(Langzeitkrankmeldung meldung,
            List<LangzeitkrankmeldungPhase> sortiertePhasen) {
        List<LangzeitkrankmeldungPhase> wiedereingliederungsPhasen = sortiertePhasen.stream()
                .filter(p -> p.getTyp() == LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG)
                .toList();
        if (wiedereingliederungsPhasen.isEmpty()) {
            return List.of();
        }

        LocalDate von = wiedereingliederungsPhasen.get(0).getVonDatum();
        boolean offen = wiedereingliederungsPhasen.stream().anyMatch(p -> p.getBisDatum() == null);
        LocalDate bis = offen ? LocalDate.now()
                : wiedereingliederungsPhasen.stream()
                        .map(LangzeitkrankmeldungPhase::getBisDatum)
                        .filter(Objects::nonNull)
                        .max(LocalDate::compareTo)
                        .orElse(von);

        Long mitarbeiterId = meldung.getMitarbeiter().getId();
        List<Zeitbuchung> buchungen = zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(
                mitarbeiterId, von.atStartOfDay(), bis.atTime(LocalTime.MAX));

        Map<LocalDate, BigDecimal> gestempeltProTag = buchungen.stream()
                .filter(b -> b.getTyp() != BuchungsTyp.PAUSE)
                .collect(Collectors.groupingBy(
                        b -> b.getStartZeit().toLocalDate(),
                        Collectors.reducing(BigDecimal.ZERO,
                                b -> b.getAnzahlInStunden() != null ? b.getAnzahlInStunden() : BigDecimal.ZERO,
                                BigDecimal::add)));

        Zeitkonto konto = zeitkontoService.getOrCreateZeitkonto(mitarbeiterId);
        // Geplante Stunden EINMAL fuer den Gesamtzeitraum laden statt einmal
        // pro Tag (Befund 2, Abschnitt 4) - vorher ein TagesSollService-Aufruf
        // mit eigener Phasen-/Feiertagsabfrage je Schleifendurchlauf, gemessen
        // 165 statt 9 Repository-Aufrufe fuer 42 Tage Wiedereingliederung.
        Map<LocalDate, BigDecimal> geplantJeTag = tagesSollService.arbeitsSollJeTag(mitarbeiterId, konto, von, bis);
        List<StufenplanTagDto> tage = new ArrayList<>();
        for (LangzeitkrankmeldungPhase phase : wiedereingliederungsPhasen) {
            LocalDate phasenEnde = phase.getBisDatum() != null ? phase.getBisDatum() : LocalDate.now();
            for (LocalDate tag = phase.getVonDatum(); !tag.isAfter(phasenEnde); tag = tag.plusDays(1)) {
                BigDecimal geplant = geplantJeTag.get(tag);
                BigDecimal gestempelt = gestempeltProTag.getOrDefault(tag, BigDecimal.ZERO);

                StufenplanTagDto tagDto = new StufenplanTagDto();
                tagDto.setDatum(tag);
                tagDto.setGeplanteStunden(geplant);
                tagDto.setGestempelteStunden(gestempelt);
                tagDto.setUeberPlan(gestempelt.compareTo(geplant) > 0);
                tage.add(tagDto);
            }
        }
        return tage;
    }
}
