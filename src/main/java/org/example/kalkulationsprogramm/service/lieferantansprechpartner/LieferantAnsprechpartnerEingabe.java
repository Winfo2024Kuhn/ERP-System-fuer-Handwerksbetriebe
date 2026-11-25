package org.example.kalkulationsprogramm.service.lieferantansprechpartner;

import org.example.kalkulationsprogramm.domain.Anrede;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantAnsprechpartnerRequestDto;
import org.example.kalkulationsprogramm.util.EmailAdressen;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/**
 * Prüft und bereinigt die Eingabe eines Lieferanten-Ansprechpartners (rein, ohne Spring):
 * Texte getrimmt, leere Texte werden {@code null}, Längen wie in der Tabelle, Nachname
 * Pflicht, E-Mail-Format. Fehler kommen als HTTP 400 mit einem Satz in Handwerker-Sprache.
 * HTML wird nicht verändert: Die Felder sind reiner Text, die Oberfläche zeigt sie als Text.
 */
public final class LieferantAnsprechpartnerEingabe {

    static final int MAX_VORNAME = 100;
    static final int MAX_NACHNAME = 255;
    static final int MAX_AUFGABE = 100;
    static final int MAX_TELEFON = 50;
    static final int MAX_EMAIL = 255;
    static final int MAX_NOTIZ = 1000;

    private LieferantAnsprechpartnerEingabe() {
    }

    /** Bereinigte Werte, fertig zum Speichern. */
    public record Werte(Anrede anrede, String vorname, String nachname, String aufgabe, String telefon,
                        String mobiltelefon, String email, String notiz) {
    }

    public static Werte pruefe(LieferantAnsprechpartnerRequestDto r) {
        if (r == null) {
            throw fehler("Bitte einen Nachnamen eintragen.");
        }
        String nachname = text(r.getNachname(), MAX_NACHNAME, "Nachname");
        if (nachname == null) {
            throw fehler("Bitte einen Nachnamen eintragen.");
        }
        String email = text(r.getEmail(), MAX_EMAIL, "E-Mail");
        if (email != null && !EmailAdressen.istKontaktAdresse(email)) {
            throw fehler("Die E-Mail-Adresse ist ungültig.");
        }
        return new Werte(anrede(r.getAnrede()),
                text(r.getVorname(), MAX_VORNAME, "Vorname"),
                nachname,
                text(r.getAufgabe(), MAX_AUFGABE, "Aufgabe"),
                text(r.getTelefon(), MAX_TELEFON, "Telefon"),
                text(r.getMobiltelefon(), MAX_TELEFON, "Mobiltelefon"),
                email,
                text(r.getNotiz(), MAX_NOTIZ, "Notiz"));
    }

    private static String text(String roh, int max, String feld) {
        if (roh == null) {
            return null;
        }
        String wert = roh.trim();
        if (wert.isEmpty()) {
            return null;
        }
        if (wert.length() > max) {
            throw fehler(feld + " ist zu lang.");
        }
        return wert;
    }

    private static Anrede anrede(String roh) {
        if (roh == null || roh.isBlank()) {
            return null;
        }
        try {
            return Anrede.valueOf(roh.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw fehler("Die Anrede ist ungültig.");
        }
    }

    private static ResponseStatusException fehler(String meldung) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, meldung);
    }
}
