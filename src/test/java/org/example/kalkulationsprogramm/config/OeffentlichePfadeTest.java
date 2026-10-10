package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

/**
 * Schreibt fest, dass vom Actuator nur der Health-Check ohne Login erreichbar
 * ist. Das nächtliche Update-Skript braucht ihn; alle anderen Actuator-Endpoints
 * (env, beans, heapdump ...) würden Konfiguration und Interna preisgeben.
 *
 * <p>Geprüft wird mit {@link AntPathMatcher}, weil Spring Security 6.2 die
 * Patterns aus {@code securityMatcher(String...)} mit Ant-Semantik auflöst.
 */
class OeffentlichePfadeTest {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private static boolean ohneLoginErreichbar(String pfad) {
        return Arrays.stream(SecurityConfig.OEFFENTLICHE_PFADE)
                .anyMatch(pattern -> MATCHER.match(pattern, pfad));
    }

    @Test
    @DisplayName("Health-Check ist ohne Login erreichbar (Nachtupdate braucht ihn)")
    void healthCheckIstOffen() {
        assertThat(ohneLoginErreichbar("/actuator/health")).isTrue();
    }

    @Test
    @DisplayName("Andere Actuator-Endpoints bleiben hinter dem Login")
    void andereActuatorEndpointsSindZu() {
        assertThat(ohneLoginErreichbar("/actuator")).isFalse();
        assertThat(ohneLoginErreichbar("/actuator/env")).isFalse();
        assertThat(ohneLoginErreichbar("/actuator/beans")).isFalse();
        assertThat(ohneLoginErreichbar("/actuator/heapdump")).isFalse();
        assertThat(ohneLoginErreichbar("/actuator/health/db")).isFalse();
        assertThat(ohneLoginErreichbar("/actuator/health/../env")).isFalse();
    }

    @Test
    @DisplayName("Statische Seiten und Login bleiben ohne Login erreichbar")
    void bestehendeOeffentlichePfadeUnveraendert() {
        assertThat(ohneLoginErreichbar("/")).isTrue();
        assertThat(ohneLoginErreichbar("/login")).isTrue();
        assertThat(ohneLoginErreichbar("/assets/index-abc123.js")).isTrue();
        assertThat(ohneLoginErreichbar("/error")).isTrue();
        assertThat(ohneLoginErreichbar("/api/kunden")).isFalse();
    }

    @Test
    @DisplayName("Actuator gibt nur den Health-Endpoint frei, ohne Details")
    void actuatorKonfigurationIstEng() throws Exception {
        // Direkt aus src/main lesen: src/test/resources bringt eine eigene
        // application.properties mit, die den Classpath-Treffer ueberdeckt.
        Properties basis = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("src", "main", "resources", "application.properties"))) {
            basis.load(in);
        }
        assertThat(basis.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(basis.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
        assertThat(basis.getProperty("management.endpoint.health.show-components")).isEqualTo("never");
    }

    @Test
    @DisplayName("Docker-Profil (Kundenserver) ueberschreibt die Actuator-Einstellungen nicht")
    void dockerProfilLaesstActuatorUnveraendert() throws Exception {
        String docker = Files.readString(Path.of("src", "main", "resources", "application-docker.properties"));
        assertThat(docker).doesNotContain("management.");
    }
}
