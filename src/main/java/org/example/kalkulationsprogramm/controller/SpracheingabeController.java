package org.example.kalkulationsprogramm.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.SpracheingabeService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * Nimmt die Sprachaufnahme aus der mobilen Zeiterfassung entgegen und liefert
 * den von {@link SpracheingabeService} erzeugten Text zurueck.
 *
 * <p>Dieser Endpunkt wird ausschliesslich von {@code react-zeiterfassung}
 * angesprochen, es gibt keinen PC-Anteil. Deshalb bewusst das einfache
 * {@code ?token=}-Muster aus {@link PushSubscriptionController} statt des
 * doppelten Web/Mobile-Musters aus {@code ProjektController.resolveMitarbeiter}
 * (das existiert dort nur, weil sowohl die PC-Oberflaeche mit Session-Login
 * als auch die PWA denselben Controller ansprechen - das ist hier nicht der
 * Fall).
 *
 * <p><b>Bekannter Restpunkt</b> (aus der Spec, gilt heute schon fuer
 * {@code /api/push/*}): der Token steht als Query-Parameter in der URL und
 * landet damit in Server-Logs und im Browser-Verlauf. Nicht neu eingefuehrt
 * durch diesen Endpunkt, nur fortgeschrieben.
 *
 * <p>Der Body ist roh (kein Multipart, kein JSON) - siehe
 * {@link SpracheingabeService} fuer die Begruendung (Multipart wuerde die
 * Aufnahme auf Platte schreiben, bevor der Controller sie sieht).
 */
@Slf4j
@RestController
@RequestMapping("/api/spracheingabe")
@RequiredArgsConstructor
public class SpracheingabeController {

    private final SpracheingabeService spracheingabeService;
    private final MitarbeiterRepository mitarbeiterRepository;

    @PostMapping("/transkribieren")
    public ResponseEntity<?> transkribieren(
            @RequestParam String token,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String inhaltstyp,
            HttpServletRequest anfrage) throws IOException {
        Mitarbeiter mitarbeiter = mitarbeiterRepository.findByLoginToken(token).orElse(null);
        if (mitarbeiter == null || !Boolean.TRUE.equals(mitarbeiter.getAktiv())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(spracheingabeService.transkribiere(anfrage.getInputStream(), inhaltstyp));
    }

    @ExceptionHandler(SpracheingabeService.AufnahmeZuGross.class)
    public ResponseEntity<Map<String, Object>> handleZuGross(SpracheingabeService.AufnahmeZuGross ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("success", false, "message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleUngueltig(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleFehler(IllegalStateException ex) {
        // DSGVO: ex.getMessage() ist hier unbedenklich - SpracheingabeService wirft
        // niemals Audio-Bytes oder das Transkript in einer Exception-Message, siehe
        // SpracheingabeService.sendeUndLies(). Trotzdem geht nur eine feste, neutrale
        // Meldung an den Client; ex.getMessage() landet ausschliesslich im Server-Log.
        log.warn("[Spracheingabe] Transkription fehlgeschlagen: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "success", false,
                "message", "Die Spracherkennung ist gerade nicht erreichbar."));
    }
}
