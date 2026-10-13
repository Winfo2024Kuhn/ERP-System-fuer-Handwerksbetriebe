package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Hinweis „Resturlaub verfällt bald“ für die Glocke der Handy-App.
 *
 * <p>Der Hinweis erscheint {@value #WARN_MONATE} Monate vor dem eingestellten
 * Verfallstag (Einstellungen → Zeiterfassung → Resturlaub), solange noch Tage
 * aus dem Vorjahr offen sind. Bei „Resturlaub verfällt nicht“ erscheint er nie.
 * Die Zahlen kommen aus demselben Urlaubskonto wie Saldo und Antragsprüfung.
 */
@Service
@RequiredArgsConstructor
public class UrlaubsverfallService {

    static final int WARN_MONATE = 2;
    static final int DRINGEND_TAGE = 30;

    private final MitarbeiterRepository mitarbeiterRepository;
    private final UrlaubskontoService urlaubskontoService;
    private final UrlaubsverfallEinstellungService einstellungen;
    private final Clock clock;

    /**
     * Prüft, ob ein Mitarbeiter einen Verfall-Hinweis bekommen soll.
     *
     * @return Hinweisdaten oder leer, wenn nichts verfällt
     */
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> pruefeVerfallWarnung(Long mitarbeiterId) {
        return mitarbeiterRepository.findById(mitarbeiterId).flatMap(this::pruefeVerfallWarnung);
    }

    /** Wie {@link #pruefeVerfallWarnung(Long)}, aber über den Anmelde-Code der Handy-App. */
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> pruefeVerfallWarnungByToken(String token) {
        return mitarbeiterRepository.findByLoginTokenAndAktivTrue(token).flatMap(this::pruefeVerfallWarnung);
    }

    private Optional<Map<String, Object>> pruefeVerfallWarnung(Mitarbeiter mitarbeiter) {
        if (Boolean.TRUE.equals(mitarbeiter.getIstGeschaeftsfuehrer())) {
            return Optional.empty();
        }
        UrlaubsverfallEinstellungService.Verfallsregel regel = einstellungen.regel();
        if (!regel.verfaellt()) {
            return Optional.empty();
        }
        LocalDate heute = LocalDate.now(clock);
        // Liegt der Verfallstag früh im Jahr (z. B. 15.01.), beginnt der Warnzeitraum schon im Vorjahr.
        for (int jahr = heute.getYear(); jahr <= heute.getYear() + 1; jahr++) {
            LocalDate verfallsdatum = regel.verfallsdatum(jahr).orElseThrow();
            if (heute.isAfter(verfallsdatum) || heute.isBefore(verfallsdatum.minusMonths(WARN_MONATE))) {
                continue;
            }
            Urlaubskonto konto = urlaubskontoService.berechne(mitarbeiter, jahr);
            BigDecimal offen = konto.ausVorjahrOffen();
            if (offen.signum() <= 0) {
                return Optional.empty();
            }
            long tageVerbleibend = ChronoUnit.DAYS.between(heute, verfallsdatum);
            Map<String, Object> warnung = new LinkedHashMap<>();
            warnung.put("resturlaubTage", offen);
            warnung.put("verfallsDatum", verfallsdatum.toString());
            warnung.put("verfallsJahr", verfallsdatum.getYear());
            warnung.put("tageVerbleibend", tageVerbleibend);
            warnung.put("dringend", tageVerbleibend <= DRINGEND_TAGE);
            return Optional.of(warnung);
        }
        return Optional.empty();
    }
}
