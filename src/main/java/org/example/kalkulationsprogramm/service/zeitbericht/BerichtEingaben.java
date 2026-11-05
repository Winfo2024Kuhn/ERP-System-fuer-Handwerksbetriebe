package org.example.kalkulationsprogramm.service.zeitbericht;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Prüft die Filter der Berichte im Report Centre und meldet Fehler in verständlicher Sprache.
 */
public final class BerichtEingaben {

    static final int MAX_MITARBEITER = 50;
    static final int MAX_TAGE = 366;
    private static final int ERSTES_JAHR = 2000;
    private static final int LETZTES_JAHR = 2100;

    private BerichtEingaben() {
    }

    public static void pruefeMonat(int jahr, int monat) {
        if (jahr < ERSTES_JAHR || jahr > LETZTES_JAHR || monat < 1 || monat > 12) {
            throw fehler("Bitte einen gültigen Monat wählen.");
        }
    }

    /** Entdoppelt die Auswahl (Reihenfolge bleibt) und prüft sie. */
    public static List<Long> mitarbeiterIds(List<Long> ids, boolean pflicht) {
        List<Long> auswahl = ids == null ? List.of() : ids;
        if (auswahl.stream().anyMatch(id -> id == null || id <= 0)) {
            throw fehler("Die Mitarbeiter-Auswahl ist ungültig. Bitte neu auswählen.");
        }
        List<Long> eindeutig = List.copyOf(new LinkedHashSet<>(auswahl));
        if (pflicht && eindeutig.isEmpty()) {
            throw fehler("Bitte mindestens einen Mitarbeiter auswählen.");
        }
        if (eindeutig.size() > MAX_MITARBEITER) {
            throw fehler("Bitte höchstens " + MAX_MITARBEITER + " Mitarbeiter auf einmal auswählen.");
        }
        return eindeutig;
    }

    public static void pruefeZeitraum(LocalDate von, LocalDate bis) {
        if (von == null || bis == null || von.getYear() < ERSTES_JAHR || bis.getYear() > LETZTES_JAHR) {
            throw fehler("Bitte einen gültigen Zeitraum wählen.");
        }
        if (von.isAfter(bis)) {
            throw fehler("Der Beginn muss vor dem Ende liegen.");
        }
        if (ChronoUnit.DAYS.between(von, bis) + 1 > MAX_TAGE) {
            throw fehler("Ein Bericht kann höchstens ein Jahr umfassen.");
        }
    }

    private static ResponseStatusException fehler(String meldung) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, Objects.requireNonNull(meldung));
    }
}
