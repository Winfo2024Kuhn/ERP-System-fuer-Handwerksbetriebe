package org.example.kalkulationsprogramm.config;

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

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:changeme}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (repository.countByUsernameIsNotNull() > 0) {
            return;
        }

        String normalizedUsername = (adminUsername == null || adminUsername.isBlank())
                ? "admin"
                : adminUsername.trim().toLowerCase(Locale.ROOT);

        String bootstrapPassword = (adminPassword == null || adminPassword.isBlank())
                ? "changeme123"
                : adminPassword;

        FrontendUserProfile admin = new FrontendUserProfile();
        admin.setDisplayName("Administrator");
        admin.setShortCode("ADM");
        admin.setUsername(normalizedUsername);
        admin.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
        admin.setActive(true);
        admin.setRoleSet(new LinkedHashSet<>(Set.of(FrontendUserRole.ADMIN, FrontendUserRole.USER)));

        repository.save(admin);
        log.info("Bootstrap-Admin für Frontend-Login wurde angelegt: username={}", normalizedUsername);
    }
}
