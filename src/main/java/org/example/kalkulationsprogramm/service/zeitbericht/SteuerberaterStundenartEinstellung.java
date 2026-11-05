package org.example.kalkulationsprogramm.service.zeitbericht;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.dto.zeitbericht.SteuerberaterBerichtDto.Stundenart;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * Merkt sich betriebsweit, ob der Steuerberater Soll- oder Iststunden bekommt. So ist beim
 * nächsten Mal an jedem PC die zuletzt gewählte Art vorausgewählt und niemand schickt aus
 * Versehen die falsche.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SteuerberaterStundenartEinstellung {

    static final String SCHLUESSEL = "zeitbericht.steuerberater.stundenart";

    private final SystemSettingsService settings;

    /** Zuletzt gewählte Art; leer, solange noch nie gewählt wurde. */
    public Optional<Stundenart> gespeichert() {
        String wert = settings.get(SCHLUESSEL, "");
        if (wert == null || wert.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Stundenart.valueOf(wert.trim()));
        } catch (IllegalArgumentException e) {
            log.warn("Gespeicherte Stundenart für den Steuerberater ist ungültig und wird ignoriert");
            return Optional.empty();
        }
    }

    /** Gespeicherte Art oder Soll (bisheriges Verhalten), wenn noch nie gewählt wurde. */
    public Stundenart oderStandard() {
        return gespeichert().orElse(Stundenart.SOLL);
    }

    @Transactional
    public Stundenart speichern(Stundenart stundenart) {
        if (stundenart == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bitte wählen, ob Soll- oder Iststunden an den Steuerberater gehen.");
        }
        settings.save(SCHLUESSEL, stundenart.name(),
                "Welche Arbeitsstunden der Steuerberater im Report Centre bekommt (SOLL oder IST)");
        log.info("Stundenart für den Steuerberater gespeichert: {}", stundenart);
        return stundenart;
    }
}
