package org.example.kalkulationsprogramm.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Betriebsweite Einstellungen der Zeiterfassung.
 *
 * <p>Das Einführungsdatum begrenzt die Monatsabschluss-Erinnerungen: Monate, in
 * denen der Betrieb noch gar keine Zeiterfassung hatte, müssen nicht
 * abgeschlossen werden – sonst verlangt die Glocke z. B. Abschlüsse ab dem
 * Eintrittsdatum langjähriger Mitarbeiter.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ZeiterfassungEinstellungService {

    static final String EINGEFUEHRT_AM = "zeiterfassung.eingefuehrt-am";

    private final SystemSettingsService settings;

    /** Tag, ab dem der Betrieb die Zeiterfassung nutzt; leer = keine Grenze. */
    public Optional<LocalDate> eingefuehrtAm() {
        String wert = settings.get(EINGEFUEHRT_AM, "");
        if (wert == null || wert.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(wert.trim()));
        } catch (DateTimeParseException ex) {
            log.warn("Gespeichertes Einführungsdatum der Zeiterfassung ist ungültig und wird ignoriert");
            return Optional.empty();
        }
    }

    /** Erster Monat, der abgeschlossen werden muss, oder {@code null} ohne Grenze. */
    public LocalDate ersterAbschlussMonat() {
        return eingefuehrtAm().map(tag -> tag.withDayOfMonth(1)).orElse(null);
    }

    /**
     * Speichert das Einführungsdatum (ISO {@code yyyy-MM-dd}); leer oder {@code null} entfernt die Grenze.
     */
    @Transactional
    public Optional<LocalDate> speichern(String eingefuehrtAm) {
        LocalDate tag = parsen(eingefuehrtAm);
        settings.save(EINGEFUEHRT_AM, tag == null ? "" : tag.toString(),
                "Ab diesem Tag nutzt der Betrieb die Zeiterfassung; frühere Monate müssen nicht abgeschlossen werden");
        log.info("Einführungsdatum der Zeiterfassung gespeichert: {}", tag == null ? "(keine Grenze)" : tag);
        return Optional.ofNullable(tag);
    }

    private static LocalDate parsen(String eingabe) {
        if (eingabe == null || eingabe.isBlank()) {
            return null;
        }
        LocalDate tag;
        try {
            // An ISO date has exactly 10 characters; anything longer is rejected before parsing.
            tag = eingabe.length() == 10 ? LocalDate.parse(eingabe) : null;
        } catch (DateTimeParseException ex) {
            tag = null;
        }
        if (tag == null || tag.getYear() < 1900) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte ein gültiges Datum eingeben.");
        }
        if (tag.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Das Datum darf nicht in der Zukunft liegen.");
        }
        return tag;
    }
}
