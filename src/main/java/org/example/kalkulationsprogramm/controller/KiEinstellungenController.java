package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.Ki.KiAuswahlRequest;
import org.example.kalkulationsprogramm.dto.Ki.KiEinstellungenDto;
import org.example.kalkulationsprogramm.dto.Ki.KiFunktionsWahlRequest;
import org.example.kalkulationsprogramm.dto.Ki.KiModellListeDto;
import org.example.kalkulationsprogramm.dto.Ki.KiSchluesselRequest;
import org.example.kalkulationsprogramm.service.SystemSettingsService.TestResult;
import org.example.kalkulationsprogramm.service.ki.KiAnbieter;
import org.example.kalkulationsprogramm.service.ki.KiDienst;
import org.example.kalkulationsprogramm.service.ki.KiEinstellungen;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiFunktionsWahl;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Einstellungen → KI-Funktionen: Anbieter wählen, Schlüssel und Modelle je
 * Anbieter pflegen. Nur für Admins ({@code /api/settings/**} in der SecurityConfig).
 * Jede Änderung antwortet mit dem kompletten neuen Stand ({@link KiEinstellungenDto}).
 */
@RestController
@RequestMapping("/api/settings/ki")
@RequiredArgsConstructor
public class KiEinstellungenController {

    private final KiDienst kiDienst;
    private final KiEinstellungen einstellungen;

    @GetMapping
    public KiEinstellungenDto lade() {
        return kiDienst.uebersicht();
    }

    @PutMapping
    public KiEinstellungenDto waehle(@RequestBody KiAuswahlRequest req) {
        return mitPruefung(() -> {
            KiAnbieter anbieter = kiDienst.anbieter(req.anbieter());
            boolean ersatz = req.ersatzErlaubt() == null ? einstellungen.ersatzErlaubt() : req.ersatzErlaubt();
            einstellungen.speichereAuswahl(anbieter.typ(), ersatz);
            return kiDienst.uebersicht();
        });
    }

    @PutMapping("/anbieter/{typ}/schluessel")
    public KiEinstellungenDto speichereSchluessel(@PathVariable String typ, @RequestBody KiSchluesselRequest req) {
        return mitPruefung(() -> {
            einstellungen.speichereSchluessel(kiDienst.anbieter(typ), req.apiKey());
            return kiDienst.uebersicht();
        });
    }

    @DeleteMapping("/anbieter/{typ}/schluessel")
    public KiEinstellungenDto entferneSchluessel(@PathVariable String typ) {
        return mitPruefung(() -> {
            einstellungen.entferneSchluessel(kiDienst.anbieter(typ));
            return kiDienst.uebersicht();
        });
    }

    @PostMapping("/anbieter/{typ}/test")
    public TestResult teste(@PathVariable String typ, @RequestBody(required = false) KiSchluesselRequest req) {
        return mitPruefung(() -> kiDienst.teste(kiDienst.anbieter(typ), req == null ? null : req.apiKey()));
    }

    @PutMapping("/anbieter/{typ}/modelle")
    public KiEinstellungenDto speichereModelle(@PathVariable String typ, @RequestBody Map<String, String> modelle) {
        return mitPruefung(() -> {
            einstellungen.speichereModelle(kiDienst.anbieter(typ), modelle);
            return kiDienst.uebersicht();
        });
    }

    /** Chat-Modelle aller Anbieter mit Schlüssel, live geladen und kurz gemerkt. */
    @GetMapping("/modelle")
    public KiModellListeDto modelle() {
        return kiDienst.modellListe();
    }

    /** Anbieter und Modell für eine einzelne Funktion; {@code anbieter = null} heißt „Standard-Anbieter“. */
    @PutMapping("/funktionen/{funktion}")
    public KiEinstellungenDto waehleFuerFunktion(@PathVariable String funktion, @RequestBody KiFunktionsWahlRequest req) {
        return mitPruefung(() -> {
            KiFunktion gesucht = funktion(funktion);
            KiFunktionsWahl wahl = req.anbieter() == null || req.anbieter().isBlank()
                    ? null
                    : new KiFunktionsWahl(kiDienst.anbieter(req.anbieter()).typ(), req.modell());
            kiDienst.waehleFuerFunktion(gesucht, wahl);
            return kiDienst.uebersicht();
        });
    }

    private static KiFunktion funktion(String name) {
        for (KiFunktion funktion : KiFunktion.values()) {
            if (funktion.name().equals(name)) {
                return funktion;
            }
        }
        throw new IllegalArgumentException("Diese KI-Funktion gibt es nicht.");
    }

    /** Prüffehler der Eingaben werden zu HTTP 400 mit dem deutschen Satz aus dem Dienst. */
    private static <T> T mitPruefung(Supplier<T> aktion) {
        try {
            return aktion.get();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
