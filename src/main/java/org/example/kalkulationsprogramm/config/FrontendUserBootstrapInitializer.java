package org.example.kalkulationsprogramm.config;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class FrontendUserBootstrapInitializer implements ApplicationRunner {

    private final FrontendUserProfileRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:}")
    private String adminUsername;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (repository.countByUsernameIsNotNull() > 0) {
            return;
        }

        String normalizedUsername = (adminUsername == null || adminUsername.isBlank())
                ? "admin"
                : adminUsername.trim().toLowerCase(Locale.ROOT);

        final String bootstrapPassword;
        final boolean passwordGenerated;
        if (adminPassword == null || adminPassword.isBlank()) {
            bootstrapPassword = generateSecurePassword();
            passwordGenerated = true;
        } else {
            bootstrapPassword = adminPassword;
            passwordGenerated = false;
        }

        FrontendUserProfile admin = new FrontendUserProfile();
        admin.setDisplayName("Administrator");
        admin.setShortCode("ADM");
        admin.setUsername(normalizedUsername);
        admin.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
        admin.setActive(true);
        admin.setRoleSet(new LinkedHashSet<>(Set.of(FrontendUserRole.ADMIN, FrontendUserRole.USER)));

        repository.save(admin);

        if (passwordGenerated) {
            log.warn("==========================================================");
            log.warn("Bootstrap-Admin angelegt: username={}", normalizedUsername);
            log.warn("Einmaliges Passwort (bitte sofort aendern): {}", bootstrapPassword);
            log.warn("Setzen Sie APP_ADMIN_PASS, um ein eigenes Passwort zu verwenden.");
            log.warn("==========================================================");
        } else {
            log.info("Bootstrap-Admin fuer Frontend-Login wurde angelegt: username={}", normalizedUsername);
        }
    }

    private static String generateSecurePassword() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
