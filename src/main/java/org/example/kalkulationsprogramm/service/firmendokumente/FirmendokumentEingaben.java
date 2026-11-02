package org.example.kalkulationsprogramm.service.firmendokumente;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Prüft Namen und Seitenangaben der Firmendokumente; Fehler als 400 mit verständlicher Meldung. */
final class FirmendokumentEingaben {

    static final int MAX_NAME = 200;
    static final int MAX_SEITE = 50;
    /** Höchste Seitennummer, bei der der Versatz (Seite × Größe) noch in einen {@code int} passt. */
    static final int MAX_SEITENNUMMER = Integer.MAX_VALUE / MAX_SEITE - 1;

    private FirmendokumentEingaben() {
    }

    /** Getrimmter Name; leer oder zu lang → 400. */
    static String name(String roh, String leerMeldung) {
        String name = roh == null ? "" : roh.strip();
        if (name.isEmpty()) {
            throw fehler(leerMeldung);
        }
        if (name.length() > MAX_NAME) {
            throw fehler("Der Name darf höchstens " + MAX_NAME + " Zeichen lang sein.");
        }
        return name;
    }

    /** Seitennummer: negativ → 0, absurd groß → höchste noch abfragbare Seite (liefert dann eine leere Seite). */
    static int seite(int page) {
        return Math.min(Math.max(page, 0), MAX_SEITENNUMMER);
    }

    static int groesse(int size) {
        return Math.min(Math.max(size, 1), MAX_SEITE);
    }

    static ResponseStatusException fehler(String meldung) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, meldung);
    }

    static ResponseStatusException nichtGefunden(String meldung) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, meldung);
    }
}
