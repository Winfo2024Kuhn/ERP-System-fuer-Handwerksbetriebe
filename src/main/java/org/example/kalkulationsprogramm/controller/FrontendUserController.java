package org.example.kalkulationsprogramm.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.service.FrontendUserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

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
            FrontendUserProfile saved = profileService.saveOrUpdate(profile, request.getDefaultSignatureId(), request.getMitarbeiterId());
            return ResponseEntity.ok(saved);
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
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Data
    public static class SaveProfileRequest {
        private Long id;
        private String displayName;
        private String shortCode;
        private Long defaultSignatureId;
        private Long mitarbeiterId;
    }

    @Data
    public static class SetDefaultSignatureRequest {
        private Long signatureId;
    }
}
