package org.example.kalkulationsprogramm.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.*;
import org.example.kalkulationsprogramm.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ZeitkontoWechselService {
    private final MitarbeiterRepository mitarbeiterRepository;
    private final ZeitkontoVersionRepository versionRepository;
    private final ZeitbuchungRepository buchungRepository;
    private final AbwesenheitRepository abwesenheitRepository;
    private final ZeitkontoService zeitkontoService;
    private final MonatsSaldoService saldoService;
    private final TagesSollService tagesSollService;
    private final EntityManager entityManager;
    private final Validator validator;

    @Transactional(readOnly = true)
    public List<ZeitkontoStatusDto> alle() {
        return mitarbeiterRepository.findMenschen().stream().map(m -> status(m.getId())).toList();
    }

    @Transactional(readOnly = true)
    public ZeitkontoStatusDto status(Long id) {
        Mitarbeiter m = mensch(id);
        List<ZeitkontoVersionDto> historie = versionRepository.findImZeitraum(id,
                LocalDate.of(1000, 1, 1), LocalDate.of(9999, 12, 31)).stream()
                .map(ZeitkontoVersionDto::from).toList();
        LocalDate heute = LocalDate.now();
        ZeitkontoVersionDto aktuell = historie.stream().filter(v -> !v.gueltigVon().isAfter(heute)
                && (v.gueltigBis() == null || !v.gueltigBis().isBefore(heute))).findFirst().orElse(null);
        boolean istGf = Boolean.TRUE.equals(m.getIstGeschaeftsfuehrer());
        boolean aktiv = Boolean.TRUE.equals(m.getFuehrtZeitkonto());
        String hinweis = !aktiv ? "Arbeitszeit erfassen ist ausgeschaltet. Die bisherigen Stunden bleiben erhalten."
                : istGf ? "Geschäftsführung: Arbeitszeit kann optional hinterlegt werden, ist für die Zeiterfassung jedoch nicht erforderlich."
                : aktuell == null ? "Bitte zuerst die Arbeitszeit einrichten." : null;
        return new ZeitkontoStatusDto(id, m.getVersion(), m.getVorname() + " " + m.getNachname(),
                aktiv, istGf, aktuell != null || istGf, hinweis, aktuell,
                historie.isEmpty() ? null : historie.get(historie.size() - 1), historie);
    }

    @Transactional(readOnly = true)
    public ZeitkontoWechselErgebnisDto vorschau(Long id, ZeitkontoWechselDto request) {
        pruefe(id, request);
        List<ZeitkontoWechselErgebnisDto.Monat> vorher = vorher(id, request.gueltigVon());
        return ergebnis(id, request.gueltigVon(), false, auswirkungen(id, request, vorher));
    }

    @Transactional
    public ZeitkontoWechselErgebnisDto uebernehmen(Long id, ZeitkontoWechselDto request) {
        mensch(id);
        // Gleiche Sperrreihenfolge wie Zuweisung und Monatsabschluss.
        entityManager.find(Mitarbeiter.class, id, LockModeType.PESSIMISTIC_WRITE);
        pruefe(id, request);
        List<ZeitkontoWechselErgebnisDto.Monat> monate = vorher(id, request.gueltigVon());
        zeitkontoService.zuweisen(id, request);
        entityManager.flush();
        List<ZeitkontoWechselErgebnisDto.Monat> danach = new ArrayList<>();
        for (var monat : monate) {
            // In derselben Transaktion rechnen: eine REQUIRES_NEW-Transaktion sähe die neue Version noch nicht.
            MonatsSaldo saldo = saldoService.berechneOhneSpeichern(id, monat.jahr(), monat.monat());
            if (!monat.abgeschlossen() && YearMonth.of(monat.jahr(), monat.monat()).isBefore(YearMonth.now())) {
                saldo = saldoService.saveMonatsSaldoCache(id, monat.jahr(), monat.monat(), saldo);
            }
            var wert = saldo.getDifferenz();
            danach.add(new ZeitkontoWechselErgebnisDto.Monat(monat.jahr(), monat.monat(),
                    monat.abgeschlossen(), monat.saldoVorher(), wert, monat.saldoVorher().compareTo(wert) != 0));
        }
        return ergebnis(id, request.gueltigVon(), true, danach);
    }

    @Transactional
    public List<ZeitkontoWechselErgebnisDto> mehrere(ZeitkontoWechselErgebnisDto.Mehrere request) {
        validiere(request);
        Set<Long> ids = new HashSet<>();
        for (var auswahl : request.mitarbeiter()) {
            if (!ids.add(auswahl.mitarbeiterId())) throw konflikt("Bitte jeden Mitarbeiter nur einmal auswählen.");
        }
        // Ein Mehrfachwechsel sperrt zuerst jede Person, dann jede Vorlage in
        // aufsteigender Reihenfolge. Damit kann A -> V, B -> V nicht gegen
        // B -> V mit einer anderen Mitarbeiterauswahl zyklisch warten.
        ids.stream().sorted().forEach(this::sperreMitarbeiter);
        request.mitarbeiter().stream().map(a -> a.wechsel().vorlageId()).filter(Objects::nonNull).distinct()
                .sorted().forEach(this::sperreVorlage);
        return request.mitarbeiter().stream().sorted(Comparator.comparing(ZeitkontoWechselErgebnisDto.Auswahl::mitarbeiterId))
                .map(a -> uebernehmen(a.mitarbeiterId(), a.wechsel())).toList();
    }

    @Transactional
    public ZeitkontoStatusDto ausschalten(Long id, ZeitkontoWechselDto.Ausschalten request) {
        zeitkontoService.ausschalten(id, request);
        return status(id);
    }

    private void pruefe(Long id, ZeitkontoWechselDto request) {
        validiere(request);
        var status = status(id);
        if (!Objects.equals(status.mitarbeiterVersion(), request.expectedMitarbeiterVersion()))
            throw konflikt("Die Mitarbeiterdaten wurden inzwischen geändert. Bitte die Vorschau neu laden.");
        var letzte = status.letzteVersion();
        if ((letzte == null && (request.expectedLetzteVersionId() != null || request.expectedLetzteVersion() != null))
                || (letzte != null && (!Objects.equals(letzte.id(), request.expectedLetzteVersionId())
                || !Objects.equals(letzte.version(), request.expectedLetzteVersion()))))
            throw konflikt("Die Arbeitszeit wurde inzwischen geändert. Bitte die Vorschau neu laden.");
        if (letzte != null && !request.gueltigVon().isAfter(letzte.gueltigVon()))
            throw konflikt("Der neue Beginn muss nach dem Beginn der letzten Arbeitszeit liegen.");
        if (request.vorlageId() != null) {
            Zeitkontenmodell vorlage = entityManager.find(Zeitkontenmodell.class, request.vorlageId());
            if (vorlage == null || !Objects.equals(vorlage.getVersion(), request.expectedVorlageVersion()))
                throw konflikt("Die Vorlage wurde inzwischen geändert. Bitte die Vorschau neu laden.");
        } else if (request.arbeitszeit() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte die Arbeitszeit ausdrücklich angeben.");
        }
        pruefeAbgeschlosseneMonate(id, request.gueltigVon());
    }

    /** Die Vorschau darf keine Änderung anbieten, die die Übernahme ablehnen würde. */
    private void pruefeAbgeschlosseneMonate(Long id, LocalDate stichtag) {
        var abgeschlossen = entityManager.createQuery("""
                SELECT m FROM MonatsSaldo m WHERE m.mitarbeiter.id = :id
                  AND m.festgeschrieben = true AND m.jahr * 100 + m.monat >= :abMonat
                """, MonatsSaldo.class)
                .setParameter("id", id)
                .setParameter("abMonat", stichtag.getYear() * 100 + stichtag.getMonthValue())
                .setMaxResults(1).getResultList();
        if (!abgeschlossen.isEmpty()) {
            throw konflikt("Die neue Arbeitszeit würde einen abgeschlossenen Monat verändern. Bitte einen späteren Beginn wählen oder den Monat zuerst wieder öffnen.");
        }
    }

    private List<ZeitkontoWechselErgebnisDto.Monat> vorher(Long id, LocalDate stichtag) {
        Mitarbeiter m = mensch(id);
        LocalDate beginn = m.getEintrittsdatum();
        if (beginn == null) beginn = buchungRepository.findFirstByMitarbeiterIdOrderByStartZeitAsc(id)
                .map(b -> b.getStartZeit().toLocalDate()).orElse(LocalDate.now());
        // Bereits gespeicherte Monate ebenfalls zeigen, auch wenn das Eintrittsdatum später korrigiert wurde.
        List<MonatsSaldo> gespeichert = entityManager.createQuery(
                "select s from MonatsSaldo s where s.mitarbeiter.id = :id order by s.jahr, s.monat", MonatsSaldo.class)
                .setParameter("id", id).getResultList();
        if (!gespeichert.isEmpty()) {
            var s = gespeichert.get(0);
            LocalDate erster = LocalDate.of(s.getJahr(), s.getMonat(), 1);
            if (erster.isBefore(beginn)) beginn = erster;
        }
        if (stichtag.isBefore(beginn)) beginn = stichtag;
        YearMonth ende = YearMonth.now();
        if (YearMonth.from(stichtag).isAfter(ende)) ende = YearMonth.from(stichtag);
        List<ZeitkontoWechselErgebnisDto.Monat> result = new ArrayList<>();
        for (YearMonth ym = YearMonth.from(beginn); !ym.isAfter(ende); ym = ym.plusMonths(1)) {
            MonatsSaldo saldo = saldoService.berechneOhneSpeichern(id, ym.getYear(), ym.getMonthValue());
            result.add(new ZeitkontoWechselErgebnisDto.Monat(ym.getYear(), ym.getMonthValue(),
                    Boolean.TRUE.equals(saldo.getFestgeschrieben()), saldo.getDifferenz(), null, false));
        }
        return result;
    }

    /** Berechnet die Wirkung ohne Version, Cache oder Saldo zu speichern. */
    private List<ZeitkontoWechselErgebnisDto.Monat> auswirkungen(Long id, ZeitkontoWechselDto request,
            List<ZeitkontoWechselErgebnisDto.Monat> vorher) {
        ZeitkontenmodellDto.Arbeitszeit vorgeschlagen = vorgeschlageneArbeitszeit(request);
        List<ZeitkontoWechselErgebnisDto.Monat> result = new ArrayList<>();
        for (var monat : vorher) {
            if (monat.abgeschlossen()) {
                result.add(new ZeitkontoWechselErgebnisDto.Monat(monat.jahr(), monat.monat(), true,
                        monat.saldoVorher(), monat.saldoVorher(), false));
                continue;
            }
            LocalDate von = YearMonth.of(monat.jahr(), monat.monat()).atDay(1);
            LocalDate bis = YearMonth.of(monat.jahr(), monat.monat()).atEndOfMonth();
            LocalDate ab = request.gueltigVon().isAfter(von) ? request.gueltigVon() : von;
            BigDecimal nachher = monat.saldoVorher();
            if (!ab.isAfter(bis)) {
                BigDecimal bisherSoll = tagesSollService.periodenSollSumme(id, ab, bis);
                BigDecimal bisherFeiertag = tagesSollService.feiertagsGutschriftSumme(id, ab, bis);
                var neueArbeitszeit = tagesSollService.vorschau(id, vorgeschlagen, ab, bis);
                nachher = nachher.add(neueArbeitszeit.feiertagsGutschrift().subtract(bisherFeiertag))
                        .subtract(neueArbeitszeit.periodenSoll().subtract(bisherSoll));
            }
            result.add(new ZeitkontoWechselErgebnisDto.Monat(monat.jahr(), monat.monat(), false,
                    monat.saldoVorher(), nachher, monat.saldoVorher().compareTo(nachher) != 0));
        }
        return result;
    }

    private ZeitkontenmodellDto.Arbeitszeit vorgeschlageneArbeitszeit(ZeitkontoWechselDto request) {
        ZeitkontenmodellDto.Arbeitszeit arbeitszeit = request.arbeitszeit();
        if (arbeitszeit == null) {
            Zeitkontenmodell vorlage = entityManager.find(Zeitkontenmodell.class, request.vorlageId());
            if (vorlage == null) throw konflikt("Die Vorlage wurde inzwischen geändert. Bitte die Vorschau neu laden.");
            arbeitszeit = ZeitkontenmodellDto.Arbeitszeit.from(vorlage);
        }
        return arbeitszeit;
    }

    private void sperreMitarbeiter(Long id) {
        if (entityManager.find(Mitarbeiter.class, id, LockModeType.PESSIMISTIC_WRITE) == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mitarbeiter nicht gefunden.");
    }

    private void sperreVorlage(Long id) {
        if (entityManager.find(Zeitkontenmodell.class, id, LockModeType.PESSIMISTIC_WRITE) == null)
            throw konflikt("Die Vorlage wurde inzwischen geändert. Bitte die Vorschau neu laden.");
    }

    private ZeitkontoWechselErgebnisDto ergebnis(Long id, LocalDate stichtag, boolean gespeichert,
            List<ZeitkontoWechselErgebnisDto.Monat> monate) {
        long abwesenheiten = abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(id, stichtag,
                LocalDate.of(9999, 12, 31)).size();
        String hinweis = gespeichert
                ? "Die Arbeitszeit wurde übernommen. Offene Monate wurden neu gerechnet; abgeschlossene Monate bleiben unverändert."
                : "Offene Monate werden neu gerechnet. Frühere Arbeitszeiten und abgeschlossene Monate bleiben erhalten.";
        if (abwesenheiten > 0) hinweis += " Bereits gebuchte Abwesenheitsstunden bleiben unverändert und werden nicht an die neue Arbeitszeit angepasst.";
        return new ZeitkontoWechselErgebnisDto(status(id), stichtag, gespeichert, abwesenheiten, hinweis, monate);
    }

    private Mitarbeiter mensch(Long id) {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültige Mitarbeiter-ID.");
        return mitarbeiterRepository.findById(id).filter(m -> m.getArt() == MitarbeiterArt.MENSCH)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mitarbeiter nicht gefunden."));
    }
    private void validiere(Object request) {
        if (request == null || !validator.validate(request).isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte die Arbeitszeitdaten vollständig und gültig angeben.");
    }
    private static ResponseStatusException konflikt(String text) {
        return new ResponseStatusException(HttpStatus.CONFLICT, text);
    }
}
