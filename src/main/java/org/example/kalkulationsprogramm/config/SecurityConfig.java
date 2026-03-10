package org.example.kalkulationsprogramm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security-Konfiguration mit HTTP Basic Auth.
 *
 * Zugänge werden über Umgebungsvariablen konfiguriert:
 *   APP_ADMIN_USER  (default: admin)
 *   APP_ADMIN_PASS  (default: changeme)
 *
 * Zeiterfassungs-Endpoints sind für mobile Clients ohne Login erreichbar
 * (dort greift der bestehende Token-basierte ZeiterfassungSecurityFilter).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.admin.username:admin}")
    private String adminUser;

    @Value("${app.admin.password:changeme}")
    private String adminPassword;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        var admin = User.builder()
                .username(adminUser)
                .password(encoder.encode(adminPassword))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    /**
     * Zeiterfassungs-PWA: erlaubt ohne Auth (Token-Prüfung via ZeiterfassungSecurityFilter).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain zeiterfassungFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/zeiterfassung/**", "/api/zeiterfassung/**", "/api/mitarbeiter/by-token/**",
                        "/api/urlaub/**", "/api/kalender/mobile/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Statische Ressourcen + Health: erlaubt ohne Auth.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain staticResourcesFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/", "/index.html", "/favicon.ico", "/assets/**",
                        "/static/**", "/manifest.json", "/sw.js",
                        "/dokument-editor", "/dokument-editor/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Alle API-Endpoints: HTTP Basic Auth erforderlich.
     */
    @Bean
    @Order(3)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
