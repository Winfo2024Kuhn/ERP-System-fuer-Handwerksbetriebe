package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.UrlaubUebertrag;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.UrlaubUebertragRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resturlaub aus dem Vorjahr von Hand festlegen (Mitarbeiter → Urlaub).
 *
 * <p>Das Programm rechnet den Übertrag selbst ({@link UrlaubskontoService}). Weicht
 * er ab – etwa weil Urlaub vor der Einführung auf Papier lief –, legt das Büro den
 * Wert hier fest. Leer zurücksetzen heißt: wieder automatisch rechnen.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UrlaubUebertragService {

    static final int MIN_JAHR = 2000;
    static final int MAX_JAHR = 2100;
    static final BigDecimal MAX_TAGE = new BigDecimal("365");
    private static final BigDecimal HALBER_TAG = new BigDecimal("0.5");

    private final MitarbeiterRepository mitarbeiterRepository;
    private final UrlaubUebertragRepository uebertragRepository;
    private final UrlaubskontoService urlaubskontoService;
    private final Clock clock;

    /**
     * Stand des Übertrags in ein Jahr.
     *
     * @param tage          gültiger Übertrag (von Hand oder berechnet)
     * @param berechnet     was das Programm selbst rechnen würde
     * @param manuell       {@code true}, wenn {@code tage} von Hand festgelegt ist
     * @param verfallsdatum ISO-Datum, bis zu dem der Übertrag genommen werden kann; {@code null} = verfällt nicht
     */
    public record Stand(int jahr, BigDecimal tage, BigDecimal berechnet, boolean manuell, String verfallsdatum) {}

    @Transactional(readOnly = true)
    public Stand laden(Long mitarbeiterId, Integer jahr) {
        Mitarbeiter mitarbeiter = mitarbeiter(mitarbeiterId);
        return stand(mitarbeiter, pruefeJahr(jahr));
    }

    /**
     * {@code tage == null} entfernt den Handeintrag – dann rechnet das Programm wieder selbst.
     *
     * @param geaendertVonId Benutzer-Profil, das den Wert festlegt ({@code null}, wenn unbekannt)
     */
    @Transactional
    public Stand speichern(Long mitarbeiterId, Integer jahr, BigDecimal tage, Long geaendertVonId) {
        Mitarbeiter mitarbeiter = mitarbeiter(mitarbeiterId);
        int pruefJahr = pruefeJahr(jahr);
        if (Boolean.TRUE.equals(mitarbeiter.getIstGeschaeftsfuehrer())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Die Geschäftsführung führt kein Urlaubskonto – ein Übertrag ist nicht nötig.");
        }
        var vorhanden = uebertragRepository.findByMitarbeiterIdAndJahr(mitarbeiter.getId(), pruefJahr);
        if (tage == null) {
            vorhanden.ifPresent(uebertragRepository::delete);
            log.info("Resturlaub-Übertrag für Mitarbeiter {} in {} wird wieder berechnet", mitarbeiter.getId(), pruefJahr);
        } else {
            UrlaubUebertrag eintrag = vorhanden.orElseGet(UrlaubUebertrag::new);
            eintrag.setMitarbeiterId(mitarbeiter.getId());
            eintrag.setJahr(pruefJahr);
            eintrag.setTage(pruefeTage(tage));
            eintrag.setGeaendertAm(LocalDateTime.now(clock));
            eintrag.setGeaendertVonId(geaendertVonId);
            uebertragRepository.save(eintrag);
            log.info("Resturlaub-Übertrag für Mitarbeiter {} in {} von Hand gesetzt", mitarbeiter.getId(), pruefJahr);
        }
        uebertragRepository.flush();
        return stand(mitarbeiter, pruefJahr);
    }

    private Stand stand(Mitarbeiter mitarbeiter, int jahr) {
        // Rechnet nur das Vorjahr (für den berechneten Wert), nicht das ganze Konto des Jahres.
        UrlaubskontoService.UebertragStand stand = urlaubskontoService.uebertragStand(mitarbeiter, jahr);
        return new Stand(jahr, stand.tage(), stand.berechnet(), stand.manuell(),
                stand.verfallsdatum() != null ? stand.verfallsdatum().toString() : null);
    }

    private Mitarbeiter mitarbeiter(Long mitarbeiterId) {
        if (mitarbeiterId == null || mitarbeiterId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte einen Mitarbeiter auswählen.");
        }
        return mitarbeiterRepository.findById(mitarbeiterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mitarbeiter nicht gefunden."));
    }

    private static int pruefeJahr(Integer jahr) {
        if (jahr == null || jahr < MIN_JAHR || jahr > MAX_JAHR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte ein gültiges Jahr angeben.");
        }
        return jahr;
    }

    private static BigDecimal pruefeTage(BigDecimal tage) {
        if (tage.signum() < 0 || tage.compareTo(MAX_TAGE) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Der Resturlaub muss zwischen 0 und 365 Tagen liegen.");
        }
        if (tage.remainder(HALBER_TAG).signum() != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bitte ganze oder halbe Tage eingeben (z. B. 3 oder 3,5).");
        }
        return tage.setScale(1, java.math.RoundingMode.UNNECESSARY);
    }
}
