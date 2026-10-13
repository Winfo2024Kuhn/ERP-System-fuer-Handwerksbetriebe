package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Werkstoffe pflegen: ansehen, anlegen, ändern, löschen. Ein Werkstoff, den Artikel benutzen, lässt sich nicht löschen, damit kein Artikel ohne
 * Werkstoff zurückbleibt. Die Dichte wird nur gespeichert (das Programm rechnet im Moment nichts damit) und auf einen sinnvollen Bereich geprüft.
 * Die Längen entsprechen den Spalten der Tabelle {@code werkstoff} (je 255 Zeichen), damit sich auch bestehende Werkstoffe wieder speichern lassen.
 */
@Service
@RequiredArgsConstructor
public class WerkstoffService {

    static final int MAX_TEXT = 255;
    static final BigDecimal DICHTE_MIN = new BigDecimal("0.1");
    static final BigDecimal DICHTE_MAX = new BigDecimal("25");

    private final WerkstoffRepository werkstoffe;

    /** Die Angaben beim Anlegen und Ändern; {@code verzinkungsgeeignet} und {@code pulverbeschichtungsgeeignet} leer = nein. */
    public record Eingabe(String name, String anzeigename, String werkstattname, BigDecimal dichte, String werkstoffnorm, Boolean verzinkungsgeeignet,
            Boolean pulverbeschichtungsgeeignet, String beschichtungshinweis) { }

    /** Ein Werkstoff mit der Zahl der Artikel, die ihn benutzen. */
    public record Zeile(Long id, String name, String anzeigename, String werkstattname, BigDecimal dichte, String werkstoffnorm, boolean verzinkungsgeeignet,
            boolean pulverbeschichtungsgeeignet, String beschichtungshinweis, long artikelAnzahl) { }

    @Transactional(readOnly = true)
    public List<Zeile> alle() {
        Map<Long, Long> anzahl = new HashMap<>();
        for (Object[] z : werkstoffe.zaehleArtikelJeWerkstoff()) {
            anzahl.put((Long) z[0], ((Number) z[1]).longValue());
        }
        return werkstoffe.findAll().stream()
                .sorted(Comparator.comparing((Werkstoff w) -> w.getAnzeigenameOderName() == null ? "" : w.getAnzeigenameOderName().toLowerCase(Locale.ROOT)))
                .map(w -> zeile(w, anzahl.getOrDefault(w.getId(), 0L)))
                .toList();
    }

    @Transactional
    public Zeile anlegen(Eingabe e) {
        Werkstoff w = new Werkstoff();
        uebernehme(w, e);
        return zeile(speichere(w), 0);
    }

    @Transactional
    public Zeile aendern(Long id, Eingabe e) {
        Werkstoff w = finde(id);
        uebernehme(w, e);
        return zeile(speichere(w), werkstoffe.zaehleArtikel(id));
    }

    @Transactional
    public void loeschen(Long id) {
        Werkstoff w = finde(id);
        long benutzt = werkstoffe.zaehleArtikel(id);
        if (benutzt > 0) {
            throw fehler(HttpStatus.CONFLICT, "Dieser Werkstoff wird von " + benutzt + (benutzt == 1 ? " Artikel" : " Artikeln")
                    + " benutzt und kann nicht gelöscht werden. Bitte zuerst bei den Artikeln einen anderen Werkstoff wählen.");
        }
        werkstoffe.delete(w);
    }

    /**
     * Speichert sofort, damit ein gleichzeitig angelegter gleicher Name hier als verständliche Meldung ankommt (409) und nicht erst beim Abschluss der
     * Transaktion als allgemeiner Datenbankfehler.
     */
    private Werkstoff speichere(Werkstoff w) {
        try {
            return werkstoffe.saveAndFlush(w);
        } catch (DataIntegrityViolationException ex) {
            throw fehler(HttpStatus.CONFLICT, nameVergeben(w.getName()));
        }
    }

    private static String nameVergeben(String name) {
        return "Einen Werkstoff mit dem Namen „" + name + "“ gibt es schon.";
    }

    private Werkstoff finde(Long id) {
        return werkstoffe.findById(id).orElseThrow(() -> fehler(HttpStatus.NOT_FOUND, "Den Werkstoff gibt es nicht."));
    }

    private void uebernehme(Werkstoff w, Eingabe e) {
        if (e == null) {
            throw fehler(HttpStatus.BAD_REQUEST, "Die Angaben fehlen.");
        }
        String name = text(e.name());
        if (name == null) {
            throw fehler(HttpStatus.BAD_REQUEST, "Bitte einen Namen eingeben (zum Beispiel S235JR oder 1.4301).");
        }
        laenge(name, MAX_TEXT, "Der Name");
        // Über die ganze (kleine) Liste statt über eine Einzelabfrage: Existieren schon Namen, die sich nur in der Schreibweise unterscheiden, würde
        // eine Einzelabfrage mit zwei Treffern abbrechen.
        werkstoffe.findAll().stream()
                .filter(anderer -> anderer.getName() != null && anderer.getName().equalsIgnoreCase(name) && !anderer.getId().equals(w.getId()))
                .findFirst().ifPresent(anderer -> {
                    throw fehler(HttpStatus.CONFLICT, nameVergeben(anderer.getName()));
                });
        BigDecimal dichte = e.dichte();
        if (dichte != null && (dichte.compareTo(DICHTE_MIN) < 0 || dichte.compareTo(DICHTE_MAX) > 0)) {
            throw fehler(HttpStatus.BAD_REQUEST, "Die Dichte muss zwischen 0,1 und 25 kg/dm³ liegen (Stahl 7,85, Aluminium 2,7).");
        }
        w.setName(name);
        w.setAnzeigename(laenge(text(e.anzeigename()), MAX_TEXT, "Der Anzeigename"));
        w.setWerkstattname(laenge(text(e.werkstattname()), MAX_TEXT, "Der Werkstattname"));
        w.setDichte(dichte == null ? null : dichte.setScale(3, RoundingMode.HALF_UP));
        w.setWerkstoffnorm(laenge(text(e.werkstoffnorm()), MAX_TEXT, "Die Norm"));
        w.setVerzinkungsgeeignet(Boolean.TRUE.equals(e.verzinkungsgeeignet()));
        w.setPulverbeschichtungsgeeignet(Boolean.TRUE.equals(e.pulverbeschichtungsgeeignet()));
        w.setBeschichtungshinweis(laenge(text(e.beschichtungshinweis()), MAX_TEXT, "Der Hinweis"));
    }

    private static String text(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static String laenge(String s, int max, String was) {
        if (s != null && s.length() > max) {
            throw fehler(HttpStatus.BAD_REQUEST, was + " ist zu lang (höchstens " + max + " Zeichen).");
        }
        return s;
    }

    private static Zeile zeile(Werkstoff w, long artikel) {
        return new Zeile(w.getId(), w.getName(), w.getAnzeigename(), w.getWerkstattname(), w.getDichte(), w.getWerkstoffnorm(), w.isVerzinkungsgeeignet(),
                w.isPulverbeschichtungsgeeignet(), w.getBeschichtungshinweis(), artikel);
    }

    private static ResponseStatusException fehler(HttpStatus status, String text) {
        return new ResponseStatusException(status, text);
    }
}
