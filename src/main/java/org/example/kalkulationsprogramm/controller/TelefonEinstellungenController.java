package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.Telefon.AbholErgebnisDto;
import org.example.kalkulationsprogramm.dto.Telefon.AnrufbeantworterDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonEinstellungenDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonEinstellungenSpeichernDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonVerbindungstestAnfrageDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonVerbindungstestDto;
import org.example.kalkulationsprogramm.service.telefon.AnlagenInfo;
import org.example.kalkulationsprogramm.service.telefon.TelefonAbholService;
import org.example.kalkulationsprogramm.service.telefon.TelefonAnlage;
import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException;
import org.example.kalkulationsprogramm.service.telefon.TelefonAnrufmonitorService;
import org.example.kalkulationsprogramm.service.telefon.TelefonEinstellungenService;
import org.example.kalkulationsprogramm.service.telefon.TelefonZugang;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * FRITZ!Box-Einstellungen und Nachholen – nur ADMIN (siehe SecurityConfig:
 * {@code /api/telefon/einstellungen/**} und {@code /api/telefon/admin/**}).
 */
@RestController
@RequestMapping("/api/telefon")
@RequiredArgsConstructor
public class TelefonEinstellungenController {

    private final TelefonEinstellungenService einstellungen;
    private final TelefonAnlage anlage;
    private final TelefonAbholService abholService;
    private final TelefonAnrufmonitorService anrufmonitor;

    @GetMapping("/einstellungen")
    public TelefonEinstellungenDto laden() {
        return einstellungen.lade(anrufmonitor.istVerbunden());
    }

    @PutMapping("/einstellungen")
    public TelefonEinstellungenDto speichern(@RequestBody TelefonEinstellungenSpeichernDto dto) {
        einstellungen.speichere(dto);
        anrufmonitor.einstellungenGeaendert();
        return einstellungen.lade(anrufmonitor.istVerbunden());
    }

    /** Prüft die Verbindung mit (ggf. ungespeicherten) Eingaben und übernimmt die Vorwahlen. */
    @PostMapping("/einstellungen/test")
    public TelefonVerbindungstestDto testen(@RequestBody(required = false) TelefonVerbindungstestAnfrageDto dto) {
        TelefonVerbindungstestAnfrageDto anfrage = dto != null ? dto : new TelefonVerbindungstestAnfrageDto(null, null, null);
        Optional<TelefonZugang> zugang = einstellungen.zugangFuerTest(anfrage.host(), anfrage.benutzer(), anfrage.passwort());
        if (zugang.isEmpty()) {
            return TelefonVerbindungstestDto.fehler("Bitte Adresse, Benutzer und Passwort angeben.");
        }
        try {
            AnlagenInfo info = anlage.pruefeVerbindung(zugang.get());
            einstellungen.speichereVorwahlen(info.landesvorwahl(), info.ortsvorwahl());
            return new TelefonVerbindungstestDto(true, "Verbindung zur FRITZ!Box steht.",
                    info.eigeneNummern(),
                    info.anrufbeantworter().stream().map(a -> new AnrufbeantworterDto(a.index(), a.name())).toList(),
                    info.landesvorwahl(), info.ortsvorwahl());
        } catch (TelefonAnlageException e) {
            return TelefonVerbindungstestDto.fehler(e.getMessage());
        }
    }

    /** Backfill: Anrufliste der letzten {@code tage} Tage, alle Nachrichten, alle Unbekannten neu abgleichen. */
    @PostMapping("/admin/nachholen")
    public AbholErgebnisDto nachholen(@RequestParam(defaultValue = "365") int tage) {
        return abholService.nachholen(tage);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> ungueltig(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
}
