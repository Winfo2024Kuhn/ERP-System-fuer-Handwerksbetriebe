package org.example.kalkulationsprogramm.controller;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.service.FrontendUserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/frontend-users")
public class FrontendUserController {

    private final FrontendUserProfileService profileService;

    @GetMapping
    public List<FrontendUserProfile> list() {
        return profileService.list();
    }

    @PostMapping
    public ResponseEntity<FrontendUserProfile> save(@RequestBody SaveProfileRequest request) {
        if (request.getDisplayName() == null || request.getDisplayName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        FrontendUserProfile profile = new FrontendUserProfile();
        profile.setId(request.getId());
        profile.setDisplayName(request.getDisplayName().trim());
        if (request.getShortCode() != null && !request.getShortCode().isBlank()) {
            profile.setShortCode(request.getShortCode().trim());
        } else {
            profile.setShortCode(null);
        }
        try {
            FrontendUserProfile saved = profileService.saveOrUpdate(
                    profile,
                    request.getDefaultSignatureId(),
                    request.getMitarbeiterId(),
                    request.getUsername(),
                    request.getPassword(),
                    request.getRolesAsEnum(),
                    request.getActive()
            );
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().build();
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/default-signature")
    public ResponseEntity<FrontendUserProfile> setDefaultSignature(@PathVariable Long id,
            @RequestBody SetDefaultSignatureRequest request) {
        try {
            FrontendUserProfile updated = profileService.setDefaultSignature(id, request.getSignatureId());
            return ResponseEntity.ok(updated);
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        try {
            profileService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Data
    public static class SaveProfileRequest {
        private Long id;
        private String displayName;
        private String shortCode;
        private String username;
        private String password;
        private List<String> roles;
        private Boolean active;
        private Long defaultSignatureId;
        private Long mitarbeiterId;

        public Set<FrontendUserRole> getRolesAsEnum() {
            LinkedHashSet<FrontendUserRole> result = new LinkedHashSet<>();
            if (roles == null) {
                return result;
            }

            for (String role : roles) {
                if (role == null || role.isBlank()) {
                    continue;
                }
                try {
                    result.add(FrontendUserRole.valueOf(role.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return result;
        }
    }

    @Data
    public static class SetDefaultSignatureRequest {
        private Long signatureId;
    }
}
