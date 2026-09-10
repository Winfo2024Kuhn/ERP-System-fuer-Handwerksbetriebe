package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.example.kalkulationsprogramm.dto.MonatsabschlussDto;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Service für die monatliche Saldo-Zwischenspeicherung.
 * 
 * Caching-Strategie:
 * - Abgeschlossene Monate werden berechnet und in der DB zwischengespeichert.
 * - Bei Datenänderungen (Buchungen, Abwesenheiten, Korrekturen) wird der
 *   betroffene Monat invalidiert und bei der nächsten Abfrage neu berechnet.
 * - Der aktuelle (laufende) Monat wird IMMER live berechnet, nie gecached.
 * 
 * Rechtliche Sicherheit:
 * - Der Cache ist ausschließlich ein Performance-Mechanismus.
 * - Alle Quelldaten (Zeitbuchung, Abwesenheit, ZeitkontoKorrektur) bleiben
 *   unverändert mit ihrem vollständigen Audit-Trail bestehen.
 * - Bei Invalidierung wird sofort sauber aus den Quelldaten neu berechnet.
 */
@Service
@RequiredArgsConstructor
public class MonatsSaldoService {

    private static final Logger log = LoggerFactory.getLogger(MonatsSaldoService.class);

    private final MonatsSaldoRepository monatsSaldoRepository;
    private final ZeitbuchungRepository zeitbuchungRepository;
    private final AbwesenheitRepository abwesenheitRepository;
    private final ZeitkontoKorrekturRepository korrekturRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final ZeitkontoService zeitkontoService;
    private final TagesSollService tagesSollService;

    private final EntityManager entityManager;
    private final MonatsabschlussAuditRepository auditRepository;
    private final MonatsabschlussBerechtigungService berechtigungService;

    /** Separate owning transaction; never call this while holding a Mitarbeiter lock. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MonatsSaldo getOrBerechne(Long mitarbeiterId, int jahr, int monat) {
        validiereMonat(mitarbeiterId, jahr, monat);
        sperreMitarbeiter(mitarbeiterId);
        Optional<MonatsSaldo> cached = gesperrterSaldo(mitarbeiterId, jahr, monat);
        if (cached.filter(ms -> Boolean.TRUE.equals(ms.getFestgeschrieben())).isPresent()) {
            return cached.get();
        }
        if (!YearMonth.of(jahr, monat).isBefore(YearMonth.now())) {
            return berechneMonatsSaldo(mitarbeiterId, jahr, monat);
        }
        if (cached.filter(ms -> Boolean.TRUE.equals(ms.getGueltig())).isPresent()) {
            return cached.get();
        }
        return saveMonatsSaldoCache(mitarbeiterId, jahr, monat,
                berechneMonatsSaldo(mitarbeiterId, jahr, monat));
    }

    /** Schreibfreie Vorschau; in einer laufenden Wechseltransaktion sind neue Versionen bereits sichtbar. */
    @Transactional(readOnly = true)
    public MonatsSaldo berechneOhneSpeichern(Long id, int jahr, int monat) {
        validiereMonat(id, jahr, monat);
        Optional<MonatsSaldo> cached = monatsSaldoRepository.findByMitarbeiterIdAndJahrAndMonat(id, jahr, monat);
        if (cached.filter(ms -> Boolean.TRUE.equals(ms.getFestgeschrieben())).isPresent()) return cached.get();
        return berechneMonatsSaldo(id, jahr, monat);
    }

    /**
     * Prüft, ob der angegebene Monat für den Mitarbeiter festgeschrieben (abgeschlossen) ist.
     */
    @Transactional(readOnly = true)
    public boolean isMonatFestgeschrieben(Long mitarbeiterId, int jahr, int monat) {
        if (mitarbeiterId == null || jahr < 1000 || monat < 1 || monat > 12) {
            return false;
        }
        return monatsSaldoRepository.findByMitarbeiterIdAndJahrAndMonat(mitarbeiterId, jahr, monat)
                .map(ms -> Boolean.TRUE.equals(ms.getFestgeschrieben()))
                .orElse(false);
    }

    /**
     * Berechnet den tatsächlichen Gesamtsaldo (Überstunden/Minusstunden) eines Mitarbeiters
     * von Beginn bis zum angegebenen Stichtag unter Einbeziehung aller Zeitbuchungen,
     * Abwesenheiten, Feiertagsgutschriften und aktiver Korrekturbuchungen.
     */
    @Transactional(readOnly = true)
    public BigDecimal berechneGesamtsaldo(Long mitarbeiterId, LocalDate bisDatum) {
        if (mitarbeiterId == null || bisDatum == null) {
            return BigDecimal.ZERO;
        }
        Mitarbeiter mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId).orElse(null);
        if (mitarbeiter == null || Boolean.TRUE.equals(mitarbeiter.getIstGeschaeftsfuehrer())) {
            return BigDecimal.ZERO;
        }

        LocalDate startDatum = mitarbeiter.getEintrittsdatum();
        if (startDatum == null) {
            Optional<Zeitbuchung> ersteBuchung = zeitbuchungRepository
                    .findFirstByMitarbeiterIdOrderByStartZeitAsc(mitarbeiterId);
            if (ersteBuchung.isPresent()) {
                startDatum = ersteBuchung.get().getStartZeit().toLocalDate();
            } else {
                startDatum = LocalDate.of(bisDatum.getYear(), 1, 1);
            }
        }

        if (bisDatum.isBefore(startDatum)) {
            return BigDecimal.ZERO;
        }

        YearMonth startYM = YearMonth.from(startDatum);
        YearMonth endYM = YearMonth.from(bisDatum);

        BigDecimal gesamtIst = BigDecimal.ZERO;
        BigDecimal gesamtSoll = BigDecimal.ZERO;

        for (YearMonth ym = startYM; !ym.isAfter(endYM); ym = ym.plusMonths(1)) {
            final YearMonth monat = ym;
            boolean istErsterMonat = ym.equals(startYM) && startDatum.getDayOfMonth() > 1;
            boolean istLetzterMonat = ym.equals(endYM) && bisDatum.getDayOfMonth() < ym.lengthOfMonth();

            Optional<MonatsSaldo> cached = monatsSaldoRepository.findByMitarbeiterIdAndJahrAndMonat(
                    mitarbeiterId, ym.getYear(), ym.getMonthValue());
            boolean festgeschrieben = cached.map(ms -> Boolean.TRUE.equals(ms.getFestgeschrieben())).orElse(false);

            if (!festgeschrieben && (istErsterMonat || istLetzterMonat)) {
                LocalDate monatVon = istErsterMonat ? startDatum : ym.atDay(1);
                LocalDate monatBis = istLetzterMonat ? bisDatum : ym.atEndOfMonth();

                LocalDateTime vonDT = monatVon.atStartOfDay();
                LocalDateTime bisDT = monatBis.atTime(23, 59, 59);

                BigDecimal istStunden = zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(
                        mitarbeiterId, vonDT, bisDT).stream()
                        .filter(b -> b.getTyp() != BuchungsTyp.PAUSE)
                        .filter(b -> b.getAnzahlInStunden() != null)
                        .map(Zeitbuchung::getAnzahlInStunden)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal abwesenheitsStunden = abwesenheitRepository.sumStundenByMitarbeiterIdAndDatumBetween(
                        mitarbeiterId, monatVon, monatBis);
                if (abwesenheitsStunden == null) abwesenheitsStunden = BigDecimal.ZERO;

                BigDecimal feiertagsStunden = tagesSollService.feiertagsGutschriftSumme(
                        mitarbeiterId, monatVon, monatBis);

                BigDecimal korrekturStunden = korrekturRepository.findByMitarbeiterIdAndDatumBetween(
                        mitarbeiterId, monatVon, monatBis).stream()
                        .filter(k -> !Boolean.TRUE.equals(k.getStorniert()))
                        .filter(k -> k.getTyp() == KorrekturTyp.STUNDEN)
                        .map(k -> k.getStunden() != null ? k.getStunden() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                gesamtIst = gesamtIst.add(istStunden).add(abwesenheitsStunden).add(feiertagsStunden).add(korrekturStunden);
                gesamtSoll = gesamtSoll.add(tagesSollService.periodenSollSumme(mitarbeiterId, monatVon, monatBis));
            } else {
                MonatsSaldo ms = cached.filter(s -> Boolean.TRUE.equals(s.getGueltig()))
                        .orElseGet(() -> berechneMonatsSaldo(mitarbeiterId, monat.getYear(), monat.getMonthValue()));
                gesamtIst = gesamtIst.add(ms.getGesamtIst());
                gesamtSoll = gesamtSoll.add(ms.getSollStunden());
            }
        }

        return gesamtIst.subtract(gesamtSoll);
    }

    private Mitarbeiter sperreMitarbeiter(Long id) {
        Mitarbeiter mitarbeiter = entityManager.find(Mitarbeiter.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (mitarbeiter == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mitarbeiter nicht gefunden");
        }
        return mitarbeiter;
    }

    private Optional<MonatsSaldo> gesperrterSaldo(Long id, int jahr, int monat) {
        // Bulk-Invalidierung erhöht @Version außerhalb des Persistence Context.
        // Vor einem Lock-Upgrade zunächst die bekannte Entity auffrischen.
        Optional<MonatsSaldo> verwaltet = monatsSaldoRepository.findByMitarbeiterIdAndJahrAndMonat(id, jahr, monat);
        if (verwaltet.isPresent()) {
            entityManager.refresh(verwaltet.get(), LockModeType.PESSIMISTIC_WRITE);
            return verwaltet;
        }
        // Locking read sieht auch seit dem Snapshot neu angelegte Zeilen.
        Optional<MonatsSaldo> saldo = monatsSaldoRepository.findGesperrt(id, jahr, monat);
        saldo.ifPresent(ms -> entityManager.refresh(ms, LockModeType.PESSIMISTIC_WRITE));
        return saldo;
    }

    private void validiereMonat(Long id, int jahr, int monat) {
        if (id == null || id <= 0 || jahr < 1000 || jahr > 9999 || monat < 1 || monat > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte einen gültigen Mitarbeiter und Monat wählen");
        }
    }

    @Transactional
    public MonatsabschlussDto status(Long id, int jahr, int monat) {
        validiereMonat(id, jahr, monat);
        if (!mitarbeiterRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mitarbeiter nicht gefunden");
        }
        return statusDto(id, jahr, monat,
                getOrBerechne(id, jahr, monat));
    }

    @Transactional
    public MonatsabschlussDto abschliessen(Long id, int jahr, int monat, Authentication authentication) {
        return abschliessenIntern(id, jahr, monat, authentication, true);
    }

    /** Sammelabschluss ohne eine zusätzliche Verlaufsabfrage pro Mitarbeiter. */
    @Transactional
    public MonatsabschlussDto abschliessenOhneVerlauf(Long id, int jahr, int monat, Authentication authentication) {
        return abschliessenIntern(id, jahr, monat, authentication, false);
    }

    private MonatsabschlussDto abschliessenIntern(Long id, int jahr, int monat,
                                                 Authentication authentication, boolean mitVerlauf) {
        Mitarbeiter akteur = berechtigungService.verlangeAkteur(authentication);
        validiereMonat(id, jahr, monat);
        if (!YearMonth.of(jahr, monat).isBefore(YearMonth.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nur vergangene Monate können abgeschlossen werden");
        }
        Mitarbeiter ziel = sperreMitarbeiter(id);
        if (ziel.getArt() != MitarbeiterArt.MENSCH) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "System-Mitarbeiter haben keinen Monatsabschluss");
        }
        if (Boolean.TRUE.equals(ziel.getIstGeschaeftsfuehrer())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Geschäftsführer führen kein Zeitkonto und haben keinen Monatsabschluss");
        }
        if (!Boolean.TRUE.equals(ziel.getFuehrtZeitkonto())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mitarbeiter ohne Zeiterfassung haben keinen Monatsabschluss");
        }
        MonatsSaldo saldo = gesperrterSaldo(id, jahr, monat).orElse(null);
        if (saldo != null && Boolean.TRUE.equals(saldo.getFestgeschrieben())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dieser Monat ist bereits abgeschlossen");
        }
        // Calculate and persist within this TX: no REQUIRES_NEW under the employee lock.
        saldo = saveMonatsSaldoCache(id, jahr, monat, berechneMonatsSaldo(id, jahr, monat));
        sichereAbwesenheitsDetails(id, jahr, monat, saldo);
        LocalDateTime zeitpunkt = LocalDateTime.now();
        saldo.setFestgeschrieben(true);
        saldo.setFestgeschriebenAm(zeitpunkt);
        saldo.setFestgeschriebenVon(akteur);
        monatsSaldoRepository.saveAndFlush(saldo);
        auditRepository.save(new MonatsabschlussAudit(ziel, jahr, monat,
                MonatsabschlussAudit.Aktion.ABSCHLIESSEN, akteur, zeitpunkt));
        return statusDto(id, jahr, monat, saldo, mitVerlauf);
    }

    @Transactional
    public MonatsabschlussDto oeffnen(Long id, int jahr, int monat, Authentication authentication) {
        Mitarbeiter akteur = berechtigungService.verlangeAkteur(authentication);
        validiereMonat(id, jahr, monat);
        Mitarbeiter ziel = sperreMitarbeiter(id);
        MonatsSaldo saldo = gesperrterSaldo(id, jahr, monat).orElse(null);
        if (saldo == null || !Boolean.TRUE.equals(saldo.getFestgeschrieben())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dieser Monat ist bereits offen");
        }
        saldo.setFestgeschrieben(false);
        saldo.setFestgeschriebenAm(null);
        saldo.setFestgeschriebenVon(null);
        saldo.setGueltig(false);
        monatsSaldoRepository.saveAndFlush(saldo);
        auditRepository.save(new MonatsabschlussAudit(ziel, jahr, monat,
                MonatsabschlussAudit.Aktion.OEFFNEN, akteur, LocalDateTime.now()));
        return statusDto(id, jahr, monat, saveMonatsSaldoCache(id, jahr, monat, berechneMonatsSaldo(id, jahr, monat)));
    }

    private MonatsabschlussDto statusDto(Long id, int jahr, int monat, MonatsSaldo saldo) {
        return statusDto(id, jahr, monat, saldo, true);
    }

    private MonatsabschlussDto statusDto(Long id, int jahr, int monat, MonatsSaldo saldo, boolean mitVerlauf) {
        entityManager.flush();
        return new MonatsabschlussDto(id, jahr, monat,
                saldo != null && Boolean.TRUE.equals(saldo.getFestgeschrieben()),
                saldo == null ? null : saldo.getVersion(),
                saldo == null ? null : saldo.getFestgeschriebenAm(),
                saldo == null || saldo.getFestgeschriebenVon() == null ? null : saldo.getFestgeschriebenVon().getId(),
                saldo.getIstStunden(), saldo.getSollStunden(), saldo.getAbwesenheitsStunden(),
                saldo.getFeiertagsStunden(), saldo.getKorrekturStunden(), saldo.getGesamtIst(), saldo.getDifferenz(),
                mitVerlauf ? auditRepository.findByMitarbeiterIdAndJahrAndMonatOrderByZeitpunktAscIdAsc(id, jahr, monat)
                        .stream().map(a -> new MonatsabschlussDto.Audit(a.getId(), a.getAktion().name(),
                                a.getAkteur().getId(), a.getAkteur().getVorname() + " " + a.getAkteur().getNachname(),
                                a.getZeitpunkt())).toList() : List.of());
    }

    /** Wird ausschließlich beim Abschluss unter dem bestehenden Mitarbeiterlock ausgeführt. */
    private void sichereAbwesenheitsDetails(Long id, int jahr, int monat, MonatsSaldo saldo) {
        BigDecimal urlaub = BigDecimal.ZERO;
        BigDecimal krankheit = BigDecimal.ZERO;
        BigDecimal fortbildung = BigDecimal.ZERO;
        BigDecimal zeitausgleich = BigDecimal.ZERO;
        BigDecimal krankengeld = BigDecimal.ZERO;
        BigDecimal wiedereingliederung = BigDecimal.ZERO;
        YearMonth zeitraum = YearMonth.of(jahr, monat);
        for (var gruppe : abwesenheitRepository.sumStundenNachTypUndPhase(id,
                zeitraum.atDay(1), zeitraum.atEndOfMonth())) {
            BigDecimal stunden = gruppe.getStunden() == null ? BigDecimal.ZERO : gruppe.getStunden();
            switch (gruppe.getTyp()) {
                case URLAUB -> urlaub = urlaub.add(stunden);
                case FORTBILDUNG -> fortbildung = fortbildung.add(stunden);
                case ZEITAUSGLEICH -> zeitausgleich = zeitausgleich.add(stunden);
                case KRANKHEIT -> {
                    if (gruppe.getPhaseTyp() == LangzeitkrankmeldungPhaseTyp.KRANKENGELD) {
                        krankengeld = krankengeld.add(stunden);
                    } else if (gruppe.getPhaseTyp() == LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG) {
                        wiedereingliederung = wiedereingliederung.add(stunden);
                    } else {
                        krankheit = krankheit.add(stunden);
                    }
                }
            }
        }
        BigDecimal summe = urlaub.add(krankheit).add(fortbildung).add(zeitausgleich)
                .add(krankengeld).add(wiedereingliederung);
        if (summe.compareTo(saldo.getAbwesenheitsStunden()) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Die Abwesenheitsdetails stimmen nicht mit den Monatsstunden überein. Bitte den Monat neu laden.");
        }
        saldo.setUrlaubStunden(urlaub);
        saldo.setKrankheitStunden(krankheit);
        saldo.setFortbildungStunden(fortbildung);
        saldo.setZeitausgleichStunden(zeitausgleich);
        saldo.setKrankengeldStunden(krankengeld);
        saldo.setWiedereingliederungStunden(wiedereingliederung);
    }

    // ==================== Berechnung ====================

    /**
     * Berechnet den MonatsSaldo aus den Quelldaten (Zeitbuchungen, Abwesenheiten, etc.).
     * Gibt ein transientes (nicht gespeichertes) MonatsSaldo-Objekt zurück.
     */
    private MonatsSaldo berechneMonatsSaldo(Long mitarbeiterId, int jahr, int monat) {
        LocalDate ersterTag = LocalDate.of(jahr, monat, 1);
        LocalDate letzterTag = YearMonth.of(jahr, monat).atEndOfMonth();
        LocalDateTime startDT = ersterTag.atStartOfDay();
        LocalDateTime endDT = letzterTag.atTime(23, 59, 59);

        // 1. Ist-Stunden aus Zeitbuchungen (ohne PAUSE)
        List<Zeitbuchung> buchungen = zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(
                mitarbeiterId, startDT, endDT);
        BigDecimal istStunden = buchungen.stream()
                .filter(b -> b.getTyp() != BuchungsTyp.PAUSE)
                .filter(b -> b.getAnzahlInStunden() != null)
                .map(Zeitbuchung::getAnzahlInStunden)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. Soll-Stunden aus Zeitkonto
        BigDecimal sollStunden = zeitkontoService.berechneSollstundenFuerMonat(mitarbeiterId, jahr, monat);

        // 3. Abwesenheitsstunden
        BigDecimal abwesenheitsStunden = abwesenheitRepository.sumStundenByMitarbeiterIdAndDatumBetween(
                mitarbeiterId, ersterTag, letzterTag);
        if (abwesenheitsStunden == null) abwesenheitsStunden = BigDecimal.ZERO;

        // 4. Feiertagsstunden
        BigDecimal feiertagsStunden = tagesSollService.feiertagsGutschriftSumme(
                mitarbeiterId, ersterTag, letzterTag);

        // 5. Korrekturstunden (nur STUNDEN-Typ, nicht storniert, Datum im Monat)
        BigDecimal korrekturStunden = korrekturRepository
                .findByMitarbeiterIdAndDatumBetween(mitarbeiterId, ersterTag, letzterTag)
                .stream()
                .filter(k -> !Boolean.TRUE.equals(k.getStorniert()))
                .filter(k -> k.getTyp() == KorrekturTyp.STUNDEN)
                .map(k -> k.getStunden() != null ? k.getStunden() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // MonatsSaldo zusammenbauen (transient)
        MonatsSaldo saldo = new MonatsSaldo();
        saldo.setJahr(jahr);
        saldo.setMonat(monat);
        saldo.setIstStunden(istStunden);
        saldo.setSollStunden(sollStunden);
        saldo.setAbwesenheitsStunden(abwesenheitsStunden);
        saldo.setFeiertagsStunden(feiertagsStunden);
        saldo.setKorrekturStunden(korrekturStunden);
        saldo.setGueltig(true);
        saldo.setBerechnetAm(LocalDateTime.now());

        return saldo;
    }

    /** Cache writes join their owning transaction and serialize first inserts with closure. */
    @Transactional
    public MonatsSaldo saveMonatsSaldoCache(Long mitarbeiterId, int jahr, int monat,
                                            MonatsSaldo berechnet) {
        validiereMonat(mitarbeiterId, jahr, monat);
        Mitarbeiter mitarbeiter = sperreMitarbeiter(mitarbeiterId);
        Optional<MonatsSaldo> existing = gesperrterSaldo(mitarbeiterId, jahr, monat);
        if (existing.filter(ms -> Boolean.TRUE.equals(ms.getFestgeschrieben())).isPresent()) {
            return existing.get();
        }

        MonatsSaldo entity;
        if (existing.isPresent()) {
            entity = existing.get();
        } else {
            entity = new MonatsSaldo();
            entity.setMitarbeiter(mitarbeiter);
            entity.setJahr(jahr);
            entity.setMonat(monat);
        }

        entity.setIstStunden(berechnet.getIstStunden());
        entity.setSollStunden(berechnet.getSollStunden());
        entity.setAbwesenheitsStunden(berechnet.getAbwesenheitsStunden());
        entity.setFeiertagsStunden(berechnet.getFeiertagsStunden());
        entity.setKorrekturStunden(berechnet.getKorrekturStunden());
        entity.setGueltig(true);
        entity.setBerechnetAm(LocalDateTime.now());

        return monatsSaldoRepository.save(entity);
    }

    // ==================== Invalidierung ====================

    /**
     * Invalidiert den Cache für einen bestimmten Monat.
     * Wird aufgerufen bei Änderungen an Zeitbuchungen oder Abwesenheiten.
     */
    @Transactional
    public void invalidiereMonat(Long mitarbeiterId, int jahr, int monat) {
        sperreMitarbeiter(mitarbeiterId);
        monatsSaldoRepository.invalidiere(mitarbeiterId, jahr, monat);
        log.debug("MonatsSaldo invalidiert: Mitarbeiter={}, {}/{}", mitarbeiterId, jahr, monat);
    }

    /**
     * Invalidiert den Cache für ein ganzes Jahr.
     * Wird aufgerufen bei Zeitkonto-Korrekturen (die jahresbezogen sind).
     */
    @Transactional
    public void invalidiereJahr(Long mitarbeiterId, int jahr) {
        sperreMitarbeiter(mitarbeiterId);
        monatsSaldoRepository.invalidiereJahr(mitarbeiterId, jahr);
        log.debug("MonatsSaldo invalidiert (ganzes Jahr): Mitarbeiter={}, {}", mitarbeiterId, jahr);
    }

    /**
     * Invalidiert ALLE Cache-Einträge für einen Mitarbeiter.
     * Wird aufgerufen bei Änderung der Zeitkonto-Sollstunden.
     */
    @Transactional
    public void invalidiereAlle(Long mitarbeiterId) {
        sperreMitarbeiter(mitarbeiterId);
        monatsSaldoRepository.invalidiereAlle(mitarbeiterId);
        log.debug("MonatsSaldo invalidiert (alle): Mitarbeiter={}", mitarbeiterId);
    }

    /**
     * Invalidiert den Cache basierend auf einem Zeitbuchungs-Datum.
     * Ermittelt automatisch Jahr/Monat aus dem Datum.
     */
    @Transactional
    public void invalidiereFuerDatum(Long mitarbeiterId, LocalDate datum) {
        if (datum != null) {
            invalidiereMonat(mitarbeiterId, datum.getYear(), datum.getMonthValue());
        }
    }

    /**
     * Invalidiert den Cache basierend auf einem DateTime (z.B. Zeitbuchung.startZeit).
     */
    @Transactional
    public void invalidiereFuerDateTime(Long mitarbeiterId, LocalDateTime dateTime) {
        if (dateTime != null) {
            invalidiereFuerDatum(mitarbeiterId, dateTime.toLocalDate());
        }
    }
}
