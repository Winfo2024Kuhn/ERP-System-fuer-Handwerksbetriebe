package org.example.kalkulationsprogramm.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.Bestellung;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.service.FrontendUserProfileService;
import org.example.kalkulationsprogramm.service.IdsPunchoutService;
import org.example.kalkulationsprogramm.service.IdsPunchoutService.PunchoutForm;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Bauleiter-Endpoints zum Punchout in den Lieferanten-Shop (IDS-Connect 2.5).
 *
 * <p>Drei Endpoints:</p>
 * <ul>
 *   <li>{@code GET /api/ids/lieferanten} — Liste aller IDS-aktivierten
 *       Lieferanten für die Lieferanten-Auswahl im Frontend.</li>
 *   <li>{@code POST /api/ids/punchout/{lieferantId}/start} — gibt die
 *       Auto-Submit-Form-Daten zurück (URL + Felder), die das Frontend
 *       als verstecktes {@code <form>} an den Shop POSTet. Erzeugt einen
 *       kurzlebigen Vorgangs-Token, der den späteren Cart-Return bindet.</li>
 *   <li>{@code POST /api/ids/punchout/{lieferantId}/return} — Hook,
 *       den der Shop nach Cart-Übernahme aufruft. Validiert den Token,
 *       legt die Bestellung als ENTWURF an und leitet den Browser auf
 *       die Bedarf-Seite zurück.</li>
 * </ul>
 *
 * <p>Bewusst <b>nicht</b> unter {@code /api/admin/...}: jeder Bauleiter
 * darf in den Shop punchen — nur die Konfig (Login-Daten) ist Admin-only.
 * Authentifizierung ist trotzdem Pflicht: VPN allein ist keine Auth.</p>
 */
@RestController
@RequestMapping("/api/ids")
@RequiredArgsConstructor
public class IdsPunchoutController {

    private final IdsPunchoutService punchoutService;
    private final FrontendUserProfileService userProfileService;

    @GetMapping("/lieferanten")
    public ResponseEntity<List<IdsLieferantDto>> listeAktivierterLieferanten() {
        List<IdsLieferantDto> liste = punchoutService.findeAktivierteLieferanten().stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(liste);
    }

    /**
     * Liefert die Auto-Submit-Form-Daten für den Punchout. Das Frontend
     * baut daraus dynamisch ein {@code <form method="POST">} und submitted
     * es in einem neuen Browser-Tab. Der zurueckgegebene HOOK_URL enthaelt
     * einen signierten Token, den der Shop beim Cart-Return zwingend
     * mitsenden muss.
     */
    @PostMapping("/punchout/{lieferantId}/start")
    public ResponseEntity<PunchoutFormDto> startPunchout(
            @PathVariable Long lieferantId,
            HttpServletRequest request,
            Authentication authentication) {
        Mitarbeiter mitarbeiter = resolveMitarbeiter(authentication);
        Long mitarbeiterId = mitarbeiter == null ? null : mitarbeiter.getId();
        try {
            String returnUrlBase = ServletUriComponentsBuilder
                    .fromContextPath(request)
                    .path("/api/ids/punchout/{id}/return")
                    .buildAndExpand(lieferantId)
                    .toUriString();
            PunchoutForm form = punchoutService.buildPunchoutForm(lieferantId, returnUrlBase, mitarbeiterId);
            // Cache-Control: no-store, weil das Klartext-Passwort des Lieferanten
            // by-design im Response steht (Auto-Submit-Form). Kein Browser-Cache,
            // kein zwischengeschalteter Proxy darf den Wert puffern.
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-store")
                    .header("Pragma", "no-cache")
                    .body(new PunchoutFormDto(form.action(), form.enctype(), form.fields()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * Hook-Endpoint, an den der Lieferanten-Shop nach "Warenkorb übernehmen"
     * den Cart als URL-encoded Form-Body POSTet. Validiert den Vorgangs-Token
     * (Replay-Schutz, Lieferant-Bindung) und verlangt eine eingeloggte Session
     * — VPN ist keine Auth-Ersatz. Legt eine neue Bestellung im Status
     * ENTWURF an und leitet den Browser auf die Bedarf-Übersicht zurück.
     */
    @PostMapping(
            value = "/punchout/{lieferantId}/return",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> returnHook(
            @PathVariable Long lieferantId,
            @RequestParam(name = "token", required = false) String token,
            @RequestParam Map<String, String> alleFormFelder,
            Authentication authentication) {
        Mitarbeiter mitarbeiter = resolveMitarbeiter(authentication);
        if (mitarbeiter == null) {
            // Spring Security blockt anonyme Requests bereits per /api/**-AuthZ —
            // dieser Guard ist defense-in-depth, falls die Filter-Reihenfolge
            // den Hook irgendwann anders behandelt.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            punchoutService.validiereVorgangsToken(token, lieferantId);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        // Token raus aus dem Cart-Map, damit er nicht versehentlich in die
        // Bestellung oder ein spaeteres Logging des Cart-Bodies wandert.
        alleFormFelder.remove("token");
        Bestellung bestellung;
        try {
            bestellung = punchoutService.verarbeiteCart(lieferantId, alleFormFelder, mitarbeiter);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        URI redirect = URI.create("/bestellungen/bedarf?ids-import=" + bestellung.getId());
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(redirect).build();
    }

    private Mitarbeiter resolveMitarbeiter(Authentication authentication) {
        if (authentication == null) return null;
        return userProfileService.findByUsername(authentication.getName())
                .map(FrontendUserProfile::getMitarbeiter)
                .orElse(null);
    }

    private IdsLieferantDto toDto(Lieferanten l) {
        return new IdsLieferantDto(l.getId(), l.getLieferantenname());
    }

    public record IdsLieferantDto(Long id, String name) {
    }

    public record PunchoutFormDto(String action, String enctype, Map<String, String> fields) {
    }
}
