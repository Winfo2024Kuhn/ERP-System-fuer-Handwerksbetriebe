package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.SperrbarerTyp;
import org.example.kalkulationsprogramm.dto.DatensatzLockDto;
import org.example.kalkulationsprogramm.service.DatensatzLockService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Endpoints fuer das Soft-Lock auf sperrbaren Datensaetzen (siehe {@link SperrbarerTyp}).
 * Verallgemeinerte Nachfolge-Route des frueheren {@code DokumentLockController} (geloescht), die
 * nicht mehr nur Geschaeftsdokumente, sondern beliebige sperrbare Typen bedient.
 *
 *   POST   /api/datensatz-locks/{typ}/{id}/acquire    — Lock erwerben oder uebernehmen
 *   POST   /api/datensatz-locks/{typ}/{id}/heartbeat  — Lock am Leben halten
 *   DELETE /api/datensatz-locks/{typ}/{id}            — Lock aktiv freigeben
 *
 * typ: AUSGANG | EINGANG (Gross-/Kleinschreibung und Whitespace egal, siehe
 * {@link SperrbarerTyp#ausText(String)}).
 */
@RestController
@RequestMapping("/api/datensatz-locks")
@RequiredArgsConstructor
public class DatensatzLockController {

    private final DatensatzLockService service;

    @PostMapping("/{typ}/{id}/acquire")
    public ResponseEntity<DatensatzLockDto> acquire(@PathVariable String typ,
                                                      @PathVariable Long id,
                                                      Authentication authentication) {
        FrontendUserPrincipal principal = principal(authentication);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        SperrbarerTyp entitaetTyp = normalizeTyp(typ);
        if (entitaetTyp == null) {
            return ResponseEntity.badRequest().build();
        }
        DatensatzLockDto result = service.acquire(entitaetTyp, id, principal.getId(), principal.getDisplayName());
        if (DatensatzLockDto.LOCKED_BY_OTHER.equals(result.status())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{typ}/{id}/heartbeat")
    public ResponseEntity<DatensatzLockDto> heartbeat(@PathVariable String typ,
                                                        @PathVariable Long id,
                                                        Authentication authentication) {
        FrontendUserPrincipal principal = principal(authentication);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        SperrbarerTyp entitaetTyp = normalizeTyp(typ);
        if (entitaetTyp == null) {
            return ResponseEntity.badRequest().build();
        }
        DatensatzLockDto result = service.heartbeat(entitaetTyp, id, principal.getId(), principal.getDisplayName());
        if (DatensatzLockDto.LOCKED_BY_OTHER.equals(result.status())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
        }
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{typ}/{id}")
    public ResponseEntity<Void> release(@PathVariable String typ,
                                        @PathVariable Long id,
                                        Authentication authentication) {
        FrontendUserPrincipal principal = principal(authentication);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        SperrbarerTyp entitaetTyp = normalizeTyp(typ);
        if (entitaetTyp == null) {
            return ResponseEntity.badRequest().build();
        }
        service.release(entitaetTyp, id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    private FrontendUserPrincipal principal(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        if (authentication.getPrincipal() instanceof FrontendUserPrincipal principal) {
            return principal;
        }
        return null;
    }

    private SperrbarerTyp normalizeTyp(String typ) {
        return SperrbarerTyp.ausText(typ).orElse(null);
    }
}
