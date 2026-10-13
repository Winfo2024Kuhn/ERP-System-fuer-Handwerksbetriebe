package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Werkstoffe pflegen: ansehen, anlegen, ändern, löschen. Ein Werkstoff, den Artikel benutzen, lässt sich nicht löschen, damit kein Artikel ohne
 * Werkstoff zurückbleibt. Die Dichte ist die Grundlage für Gewicht und Oberfläche je Meter, deshalb wird sie auf einen sinnvollen Bereich geprüft.
 */
@Service
@RequiredArgsConstructor
public class WerkstoffService {

    static final int MAX_NAME = 100;
    static final int MAX_NORM = 64;
    static final int MAX_WERKSTATT = 64;
    static final int MAX_HINWEIS = 255;
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
        return zeile(werkstoffe.save(w), 0);
    }

    @Transactional
    public Zeile aendern(Long id, Eingabe e) {
        Werkstoff w = finde(id);
        uebernehme(w, e);
        return zeile(werkstoffe.save(w), werkstoffe.zaehleArtikel(id));
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
        laenge(name, MAX_NAME, "Der Name");
        werkstoffe.findByNameIgnoreCase(name).filter(anderer -> !anderer.getId().equals(w.getId())).ifPresent(anderer -> {
            throw fehler(HttpStatus.CONFLICT, "Einen Werkstoff mit dem Namen „" + anderer.getName() + "“ gibt es schon.");
        });
        BigDecimal dichte = e.dichte();
        if (dichte != null && (dichte.compareTo(DICHTE_MIN) < 0 || dichte.compareTo(DICHTE_MAX) > 0)) {
            throw fehler(HttpStatus.BAD_REQUEST, "Die Dichte muss zwischen 0,1 und 25 kg/dm³ liegen (Stahl 7,85, Aluminium 2,7).");
        }
        w.setName(name);
        w.setAnzeigename(laenge(text(e.anzeigename()), MAX_NAME, "Der Anzeigename"));
        w.setWerkstattname(laenge(text(e.werkstattname()), MAX_WERKSTATT, "Der Werkstattname"));
        w.setDichte(dichte == null ? null : dichte.setScale(3, java.math.RoundingMode.HALF_UP));
        w.setWerkstoffnorm(laenge(text(e.werkstoffnorm()), MAX_NORM, "Die Norm"));
        w.setVerzinkungsgeeignet(Boolean.TRUE.equals(e.verzinkungsgeeignet()));
        w.setPulverbeschichtungsgeeignet(Boolean.TRUE.equals(e.pulverbeschichtungsgeeignet()));
        w.setBeschichtungshinweis(laenge(text(e.beschichtungshinweis()), MAX_HINWEIS, "Der Hinweis"));
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
