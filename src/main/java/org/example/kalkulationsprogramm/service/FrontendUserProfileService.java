package org.example.kalkulationsprogramm.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.EmailSignatureRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FrontendUserProfileService {

    private final FrontendUserProfileRepository repository;
    private final EmailSignatureRepository emailSignatureRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<FrontendUserProfile> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<FrontendUserProfile> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<FrontendUserProfile> findByDisplayName(String displayName) {
        if (displayName == null) {
            return Optional.empty();
        }
        String normalized = displayName.trim();
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return repository.findByDisplayNameIgnoreCase(normalized);
    }

    @Transactional(readOnly = true)
    public Optional<FrontendUserProfile> findByUsername(String username) {
        String normalized = normalizeUsername(username);
        if (normalized == null) {
            return Optional.empty();
        }
        return repository.findByUsernameIgnoreCase(normalized);
    }

    @Transactional
    public FrontendUserProfile saveOrUpdate(FrontendUserProfile profile, Long defaultSignatureId, Long mitarbeiterId) {
        return saveOrUpdate(profile, defaultSignatureId, mitarbeiterId, profile.getUsername(), null, profile.getRoleSet(), null);
    }

    @Transactional
    public FrontendUserProfile saveOrUpdate(
            FrontendUserProfile profile,
            Long defaultSignatureId,
            Long mitarbeiterId,
            String username,
            String rawPassword,
            Set<FrontendUserRole> roles,
            Boolean active
    ) {
        EmailSignature signature = null;
        if (defaultSignatureId != null) {
            signature = emailSignatureRepository.findById(defaultSignatureId).orElseThrow();
        }
        Mitarbeiter mitarbeiter = null;
        if (mitarbeiterId != null) {
            mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId).orElse(null);
        }

        boolean usernameProvided = username != null;
        String normalizedUsername = usernameProvided ? normalizeUsername(username) : null;

        FrontendUserProfile target;
        boolean creating = profile.getId() == null;
        if (profile.getId() != null) {
            target = repository.findById(profile.getId()).orElseThrow();
            target.setDisplayName(profile.getDisplayName());
            target.setShortCode(profile.getShortCode());
        } else {
            target = profile;
        }

        if (creating || usernameProvided) {
            ensureUsernameAvailable(normalizedUsername, target.getId());
        }

        Set<FrontendUserRole> normalizedRoles = normalizeRoleSet(roles, creating ? null : target.getRoleSet());
        boolean targetActive = active != null ? active : (creating || target.isActive());

        ensureAdminSafetyOnUpdate(target, normalizedRoles, targetActive);

        if (creating || usernameProvided) {
            target.setUsername(normalizedUsername);
        }
        if (rawPassword != null && !rawPassword.isBlank()) {
            validatePassword(rawPassword);
            target.setPasswordHash(passwordEncoder.encode(rawPassword));
        }
        target.setRoleSet(normalizedRoles);
        target.setActive(targetActive);

        if (creating && (target.getPasswordHash() == null || target.getPasswordHash().isBlank()) && normalizedUsername != null) {
            throw new IllegalArgumentException("Für neue Benutzer mit Login ist ein Passwort erforderlich.");
        }

        target.setDefaultSignature(signature);
        target.setMitarbeiter(mitarbeiter);

        if (target.getShortCode() == null || target.getShortCode().isBlank()) {
            target.setShortCode(generateShortCode(target.getDisplayName()));
        }

        return repository.save(target);
    }

    @Transactional
    public FrontendUserProfile register(String displayName, String username, String rawPassword) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Anzeigename ist erforderlich.");
        }

        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null) {
            throw new IllegalArgumentException("Benutzername ist erforderlich.");
        }

        validatePassword(rawPassword);
        ensureUsernameAvailable(normalizedUsername, null);

        FrontendUserProfile profile = new FrontendUserProfile();
        profile.setDisplayName(displayName.trim());
        profile.setShortCode(generateShortCode(displayName));
        profile.setUsername(normalizedUsername);
        profile.setPasswordHash(passwordEncoder.encode(rawPassword));
        profile.setActive(true);
        profile.setRoleSet(new LinkedHashSet<>(Set.of(FrontendUserRole.USER)));
        return repository.save(profile);
    }

    @Transactional
    public FrontendUserProfile updateCredentials(Long profileId, String username, String rawPassword) {
        FrontendUserProfile profile = repository.findById(profileId).orElseThrow();

        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername != null && !normalizedUsername.equalsIgnoreCase(profile.getUsername())) {
            ensureUsernameAvailable(normalizedUsername, profileId);
            profile.setUsername(normalizedUsername);
        }

        if (rawPassword != null && !rawPassword.isBlank()) {
            validatePassword(rawPassword);
            profile.setPasswordHash(passwordEncoder.encode(rawPassword));
        }

        return repository.save(profile);
    }

    @Transactional
    public FrontendUserProfile setDefaultSignature(Long profileId, Long signatureId) {
        FrontendUserProfile profile = repository.findById(profileId).orElseThrow();
        EmailSignature signature = null;
        if (signatureId != null) {
            signature = emailSignatureRepository.findById(signatureId).orElseThrow();
        }
        profile.setDefaultSignature(signature);
        return repository.save(profile);
    }

    @Transactional
    public void delete(Long id) {
        FrontendUserProfile profile = repository.findById(id).orElseThrow();
        if (profile.isActive() && profile.hasRole(FrontendUserRole.ADMIN) && countOtherActiveAdmins(id) == 0) {
            throw new IllegalStateException("Mindestens ein aktiver Admin muss bestehen bleiben.");
        }
        repository.deleteById(id);
    }

    private void ensureAdminSafetyOnUpdate(FrontendUserProfile existingProfile,
                                           Set<FrontendUserRole> newRoles,
                                           boolean newActiveState) {
        if (existingProfile.getId() == null) {
            return;
        }

        boolean currentlyActiveAdmin = existingProfile.isActive() && existingProfile.hasRole(FrontendUserRole.ADMIN);
        boolean remainsActiveAdmin = newActiveState && newRoles.contains(FrontendUserRole.ADMIN);

        if (currentlyActiveAdmin && !remainsActiveAdmin && countOtherActiveAdmins(existingProfile.getId()) == 0) {
            throw new IllegalStateException("Mindestens ein aktiver Admin muss bestehen bleiben.");
        }
    }

    private long countOtherActiveAdmins(Long excludedProfileId) {
        return repository.findAll().stream()
                .filter(FrontendUserProfile::isActive)
                .filter(profile -> profile.hasRole(FrontendUserRole.ADMIN))
                .filter(profile -> !profile.getId().equals(excludedProfileId))
                .count();
    }

    private Set<FrontendUserRole> normalizeRoleSet(Set<FrontendUserRole> requestedRoles, Set<FrontendUserRole> fallback) {
        LinkedHashSet<FrontendUserRole> normalized = new LinkedHashSet<>();

        if (requestedRoles != null) {
            normalized.addAll(requestedRoles);
        }

        if (normalized.isEmpty() && fallback != null) {
            normalized.addAll(fallback);
        }

        if (normalized.isEmpty()) {
            normalized.add(FrontendUserRole.USER);
        }

        return normalized;
    }

    private void ensureUsernameAvailable(String normalizedUsername, Long currentProfileId) {
        if (normalizedUsername == null) {
            return;
        }

        Optional<FrontendUserProfile> existing = repository.findByUsernameIgnoreCase(normalizedUsername);
        if (existing.isPresent() && (currentProfileId == null || !existing.get().getId().equals(currentProfileId))) {
            throw new IllegalArgumentException("Benutzername ist bereits vergeben.");
        }
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return null;
        }
        return normalized;
    }

    private void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Passwort ist erforderlich.");
        }
        if (rawPassword.length() < 8) {
            throw new IllegalArgumentException("Passwort muss mindestens 8 Zeichen lang sein.");
        }
    }

    private String generateShortCode(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }
        String[] parts = displayName.trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank()) {
                builder.append(Character.toUpperCase(part.charAt(0)));
            }
            if (builder.length() >= 4) {
                break;
            }
        }
        if (builder.length() == 0) {
            return null;
        }
        return builder.toString();
    }
}
