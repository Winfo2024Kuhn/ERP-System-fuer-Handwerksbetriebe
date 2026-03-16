package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.FrontendUserProfileService;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final FrontendUserProfileService profileService;
    private final FrontendUserProfileRepository profileRepository;
    private final SystemSettingsService settingsService;

    @GetMapping("/bootstrap-status")
    public ResponseEntity<BootstrapStatusResponse> bootstrapStatus() {
        boolean hasLoginUsers = profileRepository.countByUsernameIsNotNull() > 0;
        return ResponseEntity.ok(new BootstrapStatusResponse(
                hasLoginUsers,
                settingsService.isInitialConfigurationRequired()
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (profileRepository.countByUsernameIsNotNull() > 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Registrierung ist nur während der Einrichtungsphase möglich."));
        }
        try {
            FrontendUserProfile created = profileService.register(request.displayName(), request.username(), request.password());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "id", created.getId(),
                    "username", created.getUsername(),
                    "displayName", created.getDisplayName()
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        FrontendUserPrincipal principal = extractPrincipal(authentication);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        FrontendUserProfile profile = profileService.findById(principal.getId()).orElse(null);
        if (profile == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean isAdmin = profile.hasRole(FrontendUserRole.ADMIN);
        boolean requiresInitialSetup = isAdmin && settingsService.isInitialConfigurationRequired();

        MitarbeiterInfo mitarbeiter = null;
        if (profile.getMitarbeiter() != null) {
            mitarbeiter = new MitarbeiterInfo(profile.getMitarbeiter().getId(), profile.getMitarbeiter().getLoginToken());
        }

        return ResponseEntity.ok(new MeResponse(
                profile.getId(),
                profile.getDisplayName(),
                profile.getUsername(),
                profile.isActive(),
                profile.getRoles(),
                isAdmin,
                requiresInitialSetup,
                mitarbeiter
        ));
    }

    @PutMapping("/me/credentials")
    public ResponseEntity<?> updateOwnCredentials(@RequestBody CredentialsUpdateRequest request, Authentication authentication) {
        FrontendUserPrincipal principal = extractPrincipal(authentication);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if ((request.username() == null || request.username().isBlank())
                && (request.password() == null || request.password().isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Bitte Benutzername oder Passwort angeben."));
        }

        try {
            FrontendUserProfile updated = profileService.updateCredentials(principal.getId(), request.username(), request.password());
            return ResponseEntity.ok(Map.of(
                    "id", updated.getId(),
                    "username", updated.getUsername(),
                    "displayName", updated.getDisplayName()
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    private FrontendUserPrincipal extractPrincipal(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        if (authentication.getPrincipal() instanceof FrontendUserPrincipal principal) {
            return principal;
        }
        return null;
    }

    public record RegisterRequest(String displayName, String username, String password) {
    }

    public record CredentialsUpdateRequest(String username, String password) {
    }

    public record BootstrapStatusResponse(boolean hasLoginUsers, boolean setupRequired) {
    }

    public record MitarbeiterInfo(Long id, String loginToken) {
    }

    public record MeResponse(
            Long id,
            String displayName,
            String username,
            boolean active,
            List<String> roles,
            boolean admin,
            boolean requiresInitialSetup,
            MitarbeiterInfo mitarbeiter
    ) {
    }
}
