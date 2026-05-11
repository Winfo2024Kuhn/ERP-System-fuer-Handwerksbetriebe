package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Zahlungsart;
import org.example.kalkulationsprogramm.repository.ZahlungsartRepository;
import org.example.kalkulationsprogramm.service.BelegService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST-Endpoint fuer Zahlungsart-Stammdaten.
 *
 * Zugriff: dieselbe BELEG-Sehen-Berechtigung wie {@link SachkontoController}.
 * Anlegen/Editieren ist bewusst nicht ueber API moeglich — Stammdaten werden
 * per Flyway-Seed gepflegt; einzelne Eintraege koennen ueber DB-Migrationen
 * ergaenzt werden.
 */
@RestController
@RequestMapping("/api/buchhaltung")
@RequiredArgsConstructor
public class ZahlungsartController {

    private final ZahlungsartRepository zahlungsartRepository;
    private final BelegService belegService;

    @GetMapping("/zahlungsarten")
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(value = "nurAktive", defaultValue = "true") boolean nurAktive,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = belegService.findCaller(token, auth);
        if (caller == null || !belegService.darfSehen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<Zahlungsart> zas = nurAktive
                ? zahlungsartRepository.findByAktivTrueOrderBySortierungAscBezeichnungAsc()
                : zahlungsartRepository.findAllByOrderBySortierungAscBezeichnungAsc();
        return ResponseEntity.ok(zas.stream().map(ZahlungsartController::toDto).toList());
    }

    private static Map<String, Object> toDto(Zahlungsart za) {
        return Map.of(
                "id", za.getId(),
                "bezeichnung", za.getBezeichnung(),
                "aktiv", za.isAktiv(),
                "sortierung", za.getSortierung()
        );
    }
}
