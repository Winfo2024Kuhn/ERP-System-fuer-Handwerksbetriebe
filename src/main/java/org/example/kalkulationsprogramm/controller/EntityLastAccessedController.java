package org.example.kalkulationsprogramm.controller;

import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.service.EntityLastAccessedService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Endpoints, mit denen das Frontend pro Listen-Ansicht (Projekte, Anfragen, ...)
 * die "Zuletzt aufgerufen"-Reihenfolge geräteübergreifend pflegen kann.
 */
@RestController
@RequestMapping("/api/last-accessed")
@RequiredArgsConstructor
public class EntityLastAccessedController {

    private static final Set<String> ALLOWED_TYPES = Set.of("PROJEKT", "ANFRAGE");

    private final EntityLastAccessedService service;

    @GetMapping("/{entityType}")
    public ResponseEntity<Map<Long, Long>> list(@PathVariable String entityType,
                                                Authentication authentication) {
        FrontendUserPrincipal principal = principal(authentication);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String normalized = normalize(entityType);
        if (normalized == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(service.listForUser(principal.getId(), normalized));
    }

    @PostMapping("/{entityType}/{entityId}")
    public ResponseEntity<Void> track(@PathVariable String entityType,
                                      @PathVariable Long entityId,
                                      Authentication authentication) {
        FrontendUserPrincipal principal = principal(authentication);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String normalized = normalize(entityType);
        if (normalized == null || entityId == null) {
            return ResponseEntity.badRequest().build();
        }
        service.track(principal.getId(), normalized, entityId);
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

    private String normalize(String entityType) {
        if (entityType == null) return null;
        String upper = entityType.trim().toUpperCase();
        return ALLOWED_TYPES.contains(upper) ? upper : null;
    }
}
