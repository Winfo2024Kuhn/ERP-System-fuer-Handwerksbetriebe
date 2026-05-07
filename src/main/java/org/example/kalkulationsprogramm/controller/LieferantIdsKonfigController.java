package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.IdsProtokoll;
import org.example.kalkulationsprogramm.domain.LieferantIdsKonfig;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.Lieferant.IdsKonfigResponseDto;
import org.example.kalkulationsprogramm.dto.Lieferant.IdsKonfigUpdateRequestDto;
import org.example.kalkulationsprogramm.service.FrontendUserProfileService;
import org.example.kalkulationsprogramm.service.LieferantIdsKonfigService;
import org.example.kalkulationsprogramm.service.LieferantIdsKonfigService.IdsKonfigUpdate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Admin-Endpoints zur Pflege der IDS-Connect-Konfiguration eines
 * Lieferanten. Der Pfad liegt unter {@code /api/admin/...}; in der
 * {@link org.example.kalkulationsprogramm.config.SecurityConfig}
 * wird dieser Pfad-Präfix mit {@code .hasRole("ADMIN")} geschützt,
 * sodass Spring Security vor dem Controller greift. Die Konfig
 * enthält Login-Credentials, die kein normaler Bauleiter sehen muss.
 */
@RestController
@RequestMapping("/api/admin/lieferanten/{lieferantId}/ids-konfig")
@RequiredArgsConstructor
public class LieferantIdsKonfigController {

    private final LieferantIdsKonfigService konfigService;
    private final FrontendUserProfileService userProfileService;

    @GetMapping
    public ResponseEntity<IdsKonfigResponseDto> get(@PathVariable Long lieferantId) {
        LieferantIdsKonfig konfig = konfigService.findOrEmpty(lieferantId);
        return ResponseEntity.ok(toResponse(konfig));
    }

    @PutMapping
    public ResponseEntity<IdsKonfigResponseDto> save(
            @PathVariable Long lieferantId,
            @Valid @RequestBody IdsKonfigUpdateRequestDto request,
            Authentication authentication) {
        IdsKonfigUpdate update = new IdsKonfigUpdate();
        update.aktiviert = request.aktiviert();
        update.protokoll = request.protokoll() != null ? request.protokoll() : IdsProtokoll.IDS_CONNECT_2_5;
        update.punchoutUrl = request.punchoutUrl();
        update.kundennummer = request.kundennummer();
        update.loginName = request.loginName();
        update.passwortKlartext = request.passwort();
        update.notizen = request.notizen();

        Mitarbeiter mitarbeiter = resolveMitarbeiter(authentication);
        try {
            LieferantIdsKonfig gespeichert = konfigService.save(lieferantId, update, mitarbeiter);
            return ResponseEntity.ok(toResponse(gespeichert));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Bestimmt den eingeloggten Mitarbeiter (für Audit) – kann null sein. */
    private Mitarbeiter resolveMitarbeiter(Authentication authentication) {
        if (authentication == null) return null;
        return userProfileService.findByUsername(authentication.getName())
                .map(FrontendUserProfile::getMitarbeiter)
                .orElse(null);
    }

    private IdsKonfigResponseDto toResponse(LieferantIdsKonfig konfig) {
        boolean hatPasswort = konfig.getPasswortVerschluesselt() != null
                && !konfig.getPasswortVerschluesselt().isBlank();
        return new IdsKonfigResponseDto(
                konfig.isAktiviert(),
                konfig.getProtokoll() != null ? konfig.getProtokoll() : IdsProtokoll.IDS_CONNECT_2_5,
                konfig.getPunchoutUrl(),
                konfig.getKundennummer(),
                konfig.getLoginName(),
                hatPasswort ? LieferantIdsKonfigService.passwortPlatzhalter() : null,
                konfig.getNotizen());
    }
}
