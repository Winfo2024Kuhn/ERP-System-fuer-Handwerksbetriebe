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

    @Value("${app.admin.username:}")
    private String adminUsername;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        // Ohne explizit konfigurierte Zugangsdaten (Produktiv-Server via
        // application-local.properties) wird KEIN Admin vorab angelegt.
        // Die Einrichtungsphase bleibt offen: Der erste Nutzer registriert
        // sich selbst im Browser und wird automatisch Admin.
        if (adminUsername == null || adminUsername.isBlank()
                || adminPassword == null || adminPassword.isBlank()) {
            log.info("Kein Bootstrap-Admin konfiguriert (app.admin.username/password) – "
                    + "der erste Nutzer registriert sich selbst im Browser und wird Admin.");
            return;
        }

        if (repository.countByUsernameIsNotNull() > 0) {
            return;
        }

        String normalizedUsername = adminUsername.trim().toLowerCase(Locale.ROOT);

        FrontendUserProfile admin = new FrontendUserProfile();
        admin.setDisplayName("Administrator");
        admin.setShortCode("ADM");
        admin.setUsername(normalizedUsername);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setActive(true);
        admin.setRoleSet(new LinkedHashSet<>(Set.of(FrontendUserRole.ADMIN, FrontendUserRole.USER)));

        repository.save(admin);

        log.info("Bootstrap-Admin fuer Frontend-Login wurde angelegt: username={}", normalizedUsername);
    }
}
