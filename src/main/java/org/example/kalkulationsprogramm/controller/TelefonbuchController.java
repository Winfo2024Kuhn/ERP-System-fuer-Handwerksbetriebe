package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonbuchSchalterDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonbuchStatusDto;
import org.example.kalkulationsprogramm.service.telefon.telefonbuch.TelefonbuchAbgleichPlaner;
import org.example.kalkulationsprogramm.service.telefon.telefonbuch.TelefonbuchAbgleichService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Übertragung der ERP-Kontakte ins FRITZ!Box-Telefonbuch – nur ADMIN
 * (SecurityConfig: {@code /api/telefon/einstellungen/**}).
 */
@RestController
@RequestMapping("/api/telefon/einstellungen/telefonbuch")
@RequiredArgsConstructor
public class TelefonbuchController {

    private final TelefonbuchAbgleichService abgleich;
    private final TelefonbuchAbgleichPlaner planer;

    @GetMapping
    public TelefonbuchStatusDto status() {
        return abgleich.status();
    }

    /**
     * Schalter umlegen. Einschalten: Vollabgleich startet sofort im Hintergrund.
     * Ausschalten: Das Telefonbuch „ERP-Kontakte“ wird im Hintergrund aus der FRITZ!Box
     * gelöscht; klappt das nicht, steht „Löschung offen“ im Status und wird nachgeholt.
     */
    @PutMapping
    public TelefonbuchStatusDto schalten(@RequestBody(required = false) TelefonbuchSchalterDto dto) {
        if (dto == null || dto.aktiv() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte angeben, ob die Kontakte übertragen werden sollen.");
        }
        abgleich.schalte(dto.aktiv());
        if (abgleich.istBereit()) {
            planer.sofort();
        }
        return abgleich.status();
    }

    /**
     * „Jetzt abgleichen“ (bzw. offene Löschung nachholen): startet im Hintergrund und
     * antwortet sofort mit 202 – den Fortschritt und das Ergebnis liefert {@link #status()}.
     */
    @PostMapping("/abgleichen")
    public ResponseEntity<TelefonbuchStatusDto> abgleichen() {
        String grund = abgleich.grundGegenLauf();
        if (grund != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, grund);
        }
        if (!planer.sofort()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Der Abgleich konnte nicht gestartet werden. Bitte gleich noch einmal versuchen.");
        }
        return ResponseEntity.accepted().body(abgleich.status());
    }
}
