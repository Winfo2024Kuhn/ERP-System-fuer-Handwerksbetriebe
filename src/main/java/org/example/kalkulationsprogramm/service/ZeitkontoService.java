package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Validator;
import org.example.kalkulationsprogramm.domain.ZeitkontoVersion;
import org.example.kalkulationsprogramm.domain.MonatsSaldo;
import org.example.kalkulationsprogramm.domain.ZeitkontoPause;
import org.example.kalkulationsprogramm.domain.Zeitkontenmodell;
import org.example.kalkulationsprogramm.domain.MitarbeiterArt;
import org.example.kalkulationsprogramm.dto.ZeitkontoVersionDto;
import org.example.kalkulationsprogramm.dto.ZeitkontoWechselDto;
import org.example.kalkulationsprogramm.dto.ZeitkontenmodellDto;
import org.example.kalkulationsprogramm.repository.ZeitkontoVersionRepository;
import org.example.kalkulationsprogramm.repository.ZeitkontoPauseRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.repository.MonatsSaldoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Objects;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Zeitkonto;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ZeitkontoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service für Zeitkonten (Sollstunden pro Mitarbeiter).
 */
@Service
@RequiredArgsConstructor
public class ZeitkontoService {

    private final ZeitkontoRepository zeitkontoRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final TagesSollService tagesSollService;
    private final ZeitkontoVersionRepository versionRepository;
    private final ZeitkontoPauseRepository pauseRepository;
    private final ZeitbuchungRepository zeitbuchungRepository;
    private final MonatsSaldoRepository monatsSaldoRepository;
    private final EntityManager entityManager;
    private final Validator validator;

    @Transactional(readOnly = true)
    public Optional<ZeitkontoVersion> versionAm(Long id, LocalDate tag) {
        return versionRepository.findAm(id, tag);
    }

    @Transactional(readOnly = true)
    public List<ZeitkontoVersion> versionenImZeitraum(Long id, LocalDate von, LocalDate bis) {
        if (von == null || bis == null || bis.isBefore(von)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte einen gültigen Zeitraum angeben.");
        }
        return versionRepository.findImZeitraum(id, von, bis);
    }

    /** Neue Stichtage statt rückwirkender Überschreibung. Rückgabe enthält die neue Sperrversion. */
    @Transactional
    public ZeitkontoVersionDto zuweisen(Long id, ZeitkontoWechselDto request) {
        validiere(request);
        Mitarbeiter mitarbeiter = sperreMitarbeiter(id, request.expectedMitarbeiterVersion());
        ZeitkontoVersion letzte = letzteGeprueft(id, request.expectedLetzteVersionId(), request.expectedLetzteVersion());
        ZeitkontoPause pause = pauseRepository.findByMitarbeiterIdAndGueltigBisIsNull(id).orElse(null);
        LocalDate start = request.gueltigVon();
        if (letzte != null && !start.isAfter(letzte.getGueltigVon())) {
            throw konflikt("Der neue Beginn muss nach dem Beginn der letzten Arbeitszeit liegen.");
        }
        if (pause != null) {
            if (!start.equals(LocalDate.now()) || !start.isAfter(pause.getGueltigVon())) {
                throw konflikt("Wieder einschalten ist nur ab heute und frühestens am Tag nach dem Ausschalten möglich.");
            }
            if ((letzte != null && (letzte.getGueltigBis() == null
                    || !letzte.getGueltigBis().plusDays(1).equals(pause.getGueltigVon())))
                    || Boolean.TRUE.equals(mitarbeiter.getFuehrtZeitkonto())) {
                throw konflikt("Die Kontopause passt nicht zur letzten Arbeitszeit. Bitte die Historie prüfen.");
            }
        } else if (letzte != null && (letzte.getGueltigBis() != null
                || !Boolean.TRUE.equals(mitarbeiter.getFuehrtZeitkonto()))) {
            throw konflikt("Zwischen den Arbeitszeiten fehlt eine dokumentierte Kontopause.");
        }
        if (!Boolean.TRUE.equals(mitarbeiter.getFuehrtZeitkonto()) && !start.equals(LocalDate.now())) {
            throw konflikt("Ein ausgeschaltetes Zeitkonto kann nur mit einer Arbeitszeit ab heute eingerichtet werden.");
        }
        Zeitkontenmodell vorlage = null;
        if (request.vorlageId() != null) {
            vorlage = entityManager.find(Zeitkontenmodell.class, request.vorlageId(), LockModeType.PESSIMISTIC_WRITE);
            if (vorlage == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Arbeitszeit-Vorlage nicht gefunden.");
            entityManager.refresh(vorlage, LockModeType.PESSIMISTIC_WRITE);
            if (request.expectedVorlageVersion() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte den erwarteten Versionsstand der Vorlage angeben.");
            }
            if (!Objects.equals(request.expectedVorlageVersion(), vorlage.getVersion())) {
                throw konflikt("Die Vorlage wurde inzwischen geändert. Bitte neu laden.");
            }
        }
        ZeitkontenmodellDto.Arbeitszeit arbeitszeit = request.arbeitszeit();
        if (arbeitszeit == null && vorlage != null) arbeitszeit = ZeitkontenmodellDto.Arbeitszeit.from(vorlage);
        if (arbeitszeit == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Bitte die Arbeitszeit ausdrücklich angeben oder eine Vorlage auswählen.");
        validiere(arbeitszeit);
        pruefeOffeneMonate(id, start);
        ZeitkontoVersion neu = new ZeitkontoVersion();
        neu.setMitarbeiter(mitarbeiter);
        neu.setGueltigVon(start);
        neu.setVorlage(vorlage);
        arbeitszeit.kopiereNach(neu);
        if (pause != null) {
            pause.setGueltigBis(start.minusDays(1));
            pauseRepository.save(pause);
        } else if (letzte != null) {
            letzte.setGueltigBis(start.minusDays(1));
            versionRepository.save(letzte);
        }
        mitarbeiter.setFuehrtZeitkonto(true);
        ZeitkontoVersion gespeichert = versionRepository.saveAndFlush(neu);
        // Abschluss-Schutz wird durch Task 2 in dieser bestehenden Repository-Methode ergänzt.
        monatsSaldoRepository.invalidiereAlle(id);
        return ZeitkontoVersionDto.from(gespeichert);
    }

    /** Ausschalten gilt ab heute; eine aktive Buchung muss vorher beendet werden. */
    @Transactional
    public void ausschalten(Long id, ZeitkontoWechselDto.Ausschalten request) {
        validiere(request);
        Mitarbeiter mitarbeiter = sperreMitarbeiter(id, request.expectedMitarbeiterVersion());
        ZeitkontoVersion letzte = letzteGeprueft(id, request.expectedLetzteVersionId(), request.expectedLetzteVersion());
        LocalDate heute = LocalDate.now();
        if (!Boolean.TRUE.equals(mitarbeiter.getFuehrtZeitkonto())
                || (letzte != null && (letzte.getGueltigBis() != null || !heute.isAfter(letzte.getGueltigVon())))
                || pauseRepository.findByMitarbeiterIdAndGueltigBisIsNull(id).isPresent()) {
            throw konflikt("Heute kann keine gültige Kontopause beginnen. Eine neue Arbeitszeit muss mindestens einen Tag bestehen.");
        }
        if (!zeitbuchungRepository.findByMitarbeiterIdAndEndeZeitIsNull(id).isEmpty()) {
            throw konflikt("Bitte zuerst die laufende Zeitbuchung beenden und danach das Zeitkonto ausschalten.");
        }
        pruefeOffeneMonate(id, heute);
        if (letzte != null) {
            letzte.setGueltigBis(heute.minusDays(1));
            versionRepository.save(letzte);
        }
        ZeitkontoPause pause = new ZeitkontoPause();
        pause.setMitarbeiter(mitarbeiter);
        pause.setGueltigVon(heute);
        pauseRepository.save(pause);
        mitarbeiter.setFuehrtZeitkonto(false);
        entityManager.flush();
        monatsSaldoRepository.invalidiereAlle(id);
    }

    /** Eine neue offene Version würde alle Monate ab dem Stichtag verändern.
     * Aktueller Lock-Read berücksichtigt auch bereits ungültig markierte Abschlüsse.
     * Abschlussoperationen müssen dieselbe Mitarbeitersperre verwenden (Task 2).
     */
    private void pruefeOffeneMonate(Long id, LocalDate start) {
        var abgeschlossen = entityManager.createQuery("""
                SELECT m FROM MonatsSaldo m WHERE m.mitarbeiter.id = :id
                  AND m.festgeschrieben = true AND m.jahr * 100 + m.monat >= :abMonat
                """, MonatsSaldo.class)
                .setParameter("id", id)
                .setParameter("abMonat", start.getYear() * 100 + start.getMonthValue())
                .setLockMode(LockModeType.PESSIMISTIC_READ)
                .setMaxResults(1).getResultList();
        if (!abgeschlossen.isEmpty()) {
            throw konflikt("Die neue Arbeitszeit würde einen abgeschlossenen Monat verändern. Bitte einen späteren Beginn wählen oder den Monat zuerst wieder öffnen.");
        }
    }

    private Mitarbeiter sperreMitarbeiter(Long id, Long expectedVersion) {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültige Mitarbeiter-ID.");
        Mitarbeiter mitarbeiter = entityManager.find(Mitarbeiter.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (mitarbeiter == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mitarbeiter nicht gefunden.");
        // Auch bei bereits verwaltetem Mitarbeiter den aktuellen Datenbankstand unter der Sperre prüfen.
        entityManager.refresh(mitarbeiter, LockModeType.PESSIMISTIC_WRITE);
        if (!Objects.equals(mitarbeiter.getVersion(), expectedVersion)) {
            throw konflikt("Die Mitarbeiterdaten wurden inzwischen geändert. Bitte neu laden.");
        }
        if (mitarbeiter.getArt() == MitarbeiterArt.SYSTEM) throw konflikt("System-Mitarbeiter führen kein Zeitkonto.");
        return mitarbeiter;
    }

    private ZeitkontoVersion letzteGeprueft(Long id, Long expectedId, Long expectedVersion) {
        ZeitkontoVersion letzte = versionRepository.findFirstByMitarbeiterIdOrderByGueltigVonDesc(id).orElse(null);
        if (letzte != null) entityManager.refresh(letzte, LockModeType.PESSIMISTIC_WRITE);
        if ((letzte == null && (expectedId != null || expectedVersion != null))
                || (letzte != null && (expectedId == null || expectedVersion == null
                || !Objects.equals(letzte.getId(), expectedId) || !Objects.equals(letzte.getVersion(), expectedVersion)))) {
            throw konflikt("Die Arbeitszeit wurde inzwischen geändert. Bitte neu laden.");
        }
        return letzte;
    }

    private void validiere(Object request) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte Arbeitszeitdaten angeben.");
        var fehler = validator.validate(request);
        if (!fehler.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                fehler.stream().map(v -> v.getPropertyPath() + ": " + v.getMessage()).sorted()
                        .collect(java.util.stream.Collectors.joining("; ")));
    }

    private static ResponseStatusException konflikt(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }


    /**
     * Gibt das Zeitkonto für einen Mitarbeiter zurück.
     * Erstellt automatisch ein Standard-Zeitkonto wenn noch keines existiert.
     */
    @Transactional
    public Zeitkonto getOrCreateZeitkonto(Long mitarbeiterId) {
        return zeitkontoRepository.findByMitarbeiterId(mitarbeiterId)
                .orElseGet(() -> {
                    Mitarbeiter mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId)
                            .orElseThrow(
                                    () -> new IllegalArgumentException("Mitarbeiter nicht gefunden: " + mitarbeiterId));
                    Zeitkonto neuesKonto = new Zeitkonto(mitarbeiter);
                    return zeitkontoRepository.save(neuesKonto);
                });
    }

    /**
     * Gibt alle Zeitkonten zurück.
     */
    public List<Zeitkonto> getAlleZeitkonten() {
        return zeitkontoRepository.findAll();
    }

    /**
     * Speichert ein Zeitkonto.
     */
    @Transactional
    public Zeitkonto speichereZeitkonto(Zeitkonto zeitkonto) {
        return zeitkontoRepository.save(zeitkonto);
    }

    /**
     * Aktualisiert die Sollstunden für einen Mitarbeiter.
     */
    @Transactional
    public Zeitkonto aktualisiereZeitkonto(Long mitarbeiterId,
            BigDecimal montag, BigDecimal dienstag,
            BigDecimal mittwoch, BigDecimal donnerstag,
            BigDecimal freitag, BigDecimal samstag,
            BigDecimal sonntag) {
        Zeitkonto konto = getOrCreateZeitkonto(mitarbeiterId);
        konto.setMontagStunden(montag);
        konto.setDienstagStunden(dienstag);
        konto.setMittwochStunden(mittwoch);
        konto.setDonnerstagStunden(donnerstag);
        konto.setFreitagStunden(freitag);
        konto.setSamstagStunden(samstag);
        konto.setSonntagStunden(sonntag);
        return zeitkontoRepository.save(konto);
    }

    /**
     * Berechnet die Sollstunden für einen Monat unter Berücksichtigung von
     * Feiertagen und halben Feiertagen (z.B. Heiligabend, Silvester).
     */
    public BigDecimal berechneSollstundenFuerMonat(Long mitarbeiterId, int jahr, int monat) {
        LocalDate ersterTag = LocalDate.of(jahr, monat, 1);
        LocalDate letzterTag = ersterTag.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());

        return berechneSollstundenFuerZeitraum(mitarbeiterId, ersterTag, letzterTag);
    }

    /**
     * Berechnet die Sollstunden für einen Monat bis heute unter Berücksichtigung
     * von
     * Feiertagen und halben Feiertagen.
     */
    public BigDecimal berechneSollstundenFuerMonatBisHeute(Long mitarbeiterId, int jahr, int monat) {
        LocalDate ersterTag = LocalDate.of(jahr, monat, 1);
        LocalDate heute = LocalDate.now();
        LocalDate letzterTag = (monat == heute.getMonthValue() && jahr == heute.getYear())
                ? heute
                : ersterTag.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());

        return berechneSollstundenFuerZeitraum(mitarbeiterId, ersterTag, letzterTag);
    }

    /**
     * Berechnung: Sollstunden für einen Zeitraum.
     * Feiertage zählen weiter als bezahlte Arbeitstage mit den vollen
     * Sollstunden, halbe Feiertage (z.B. Heiligabend, Silvester) mit 50%.
     * Zusätzlich richtet sich das Soll jetzt nach einer laufenden
     * Wiedereingliederung, falls für den Mitarbeiter eine läuft.
     * Die eigentliche Rechenregel steckt in {@link TagesSollService#periodenSollSumme}.
     */
    public BigDecimal berechneSollstundenFuerZeitraum(Long mitarbeiterId, LocalDate von, LocalDate bis) {
        return tagesSollService.periodenSollSumme(mitarbeiterId, von, bis);
    }

    /** Temporär bis zur vollständigen Umstellung der Aufrufer. */
    public BigDecimal berechneSollstundenFuerZeitraum(Zeitkonto konto, LocalDate von, LocalDate bis) {
        Mitarbeiter mitarbeiter = konto.getMitarbeiter();
        Long mitarbeiterId = mitarbeiter != null ? mitarbeiter.getId() : null;
        return tagesSollService.periodenSollSumme(mitarbeiterId, konto, von, bis);
    }
}
