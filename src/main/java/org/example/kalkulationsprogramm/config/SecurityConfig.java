package org.example.kalkulationsprogramm.config;

import java.io.IOException;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Security-Konfiguration für Frontend-Login mit Rollen und Session-Cookie.
 *
 * Zeiterfassungs-Endpoints sind für mobile Clients ohne Login erreichbar
 * (dort greift der bestehende Token-basierte ZeiterfassungSecurityFilter).
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final FrontendUserDetailsService frontendUserDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Zeiterfassungs-PWA: erlaubt ohne Auth (Token-Prüfung via ZeiterfassungSecurityFilter).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain zeiterfassungFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/zeiterfassung", "/zeiterfassung/**", "/api/zeiterfassung/**", "/api/mitarbeiter/by-token/**",
                        "/api/urlaub/**", "/api/kalender/mobile/**",
                        "/api/push/**",
                        "/api/dokumente/**", "/api/images/**",
                        "/api/projekte/**", "/api/anfragen/**", "/api/kunden/**",
                        "/api/lieferanten/**", "/api/produktkategorien/**", "/api/arbeitsgaenge/**",
                        "/api/abwesenheit/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Statische Ressourcen + Health: erlaubt ohne Auth.
     * Wichtig: /error muss hier stehen, sonst gibt Spring Boot
     * nach einem internen Fehler (z.B. 404) wieder 401 zurück.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain staticResourcesFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/", "/index.html", "/favicon.ico", "/app-icon.png", "/assets/**",
                        "/static/**", "/manifest.json", "/sw.js",
                    "/dokument-editor", "/dokument-editor/**",
                    "/login", "/login/**", "/onboarding", "/onboarding/**",
                    "/error", "/error/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Alle API-Endpoints: Session-basierte Authentifizierung via formLogin + CSRF-Schutz.
     * CSRF wird über CookieCsrfTokenRepository aktiviert, damit der Browser-Client
     * das XSRF-TOKEN-Cookie lesen und als X-XSRF-TOKEN-Header mitsenden kann.
     */
    @Bean
    @Order(3)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .csrf(csrf -> {
                    // Use CsrfTokenRequestAttributeHandler (instead of XorCsrfTokenRequestAttributeHandler)
                    // so the raw cookie value is accepted as-is without XOR-masking
                    CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
                    csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(requestHandler)
                        .ignoringRequestMatchers("/api/auth/login", "/api/auth/logout");
                })
            // Force the CSRF cookie to be set on every response (Spring Security 6 deferred token fix)
            .addFilterAfter(new CsrfCookieFilter(), org.springframework.security.web.csrf.CsrfFilter.class)
            .userDetailsService(frontendUserDetailsService)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/logout", "/api/auth/register", "/api/auth/bootstrap-status").permitAll()
                .requestMatchers("/api/auth/me", "/api/auth/me/credentials").authenticated()
                .requestMatchers("/api/firma/**", "/api/settings/**", "/api/frontend-users/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .successHandler((request, response, authentication) -> writeJson(
                    response,
                    HttpServletResponse.SC_OK,
                    Map.of("success", true, "message", "Login erfolgreich.")
                ))
                .failureHandler((request, response, exception) -> writeJson(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    Map.of("success", false, "message", "Benutzername oder Passwort ist falsch.")
                ))
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((request, response, authentication) -> writeJson(
                    response,
                    HttpServletResponse.SC_OK,
                    Map.of("success", true, "message", "Erfolgreich abgemeldet.")
                ))
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> writeJson(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    Map.of("success", false, "message", "Nicht authentifiziert.")
                ))
                .accessDeniedHandler((request, response, accessDeniedException) -> writeJson(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    Map.of("success", false, "message", "Zugriff verweigert.")
                ))
            )
            .httpBasic(AbstractHttpConfigurer::disable)
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
            );

        return http.build();
    }

    /**
     * Catch-all: alle Pfade die nicht durch die obigen Chains abgedeckt werden
     * (z.B. Controller-Pfade ausserhalb von /api/**) erfordern Authentifizierung.
     */
    @Bean
    @Order(4)
    public SecurityFilterChain catchAllFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> {
                    // Use CsrfTokenRequestAttributeHandler (instead of XorCsrfTokenRequestAttributeHandler)
                    // so the raw cookie value is accepted as-is without XOR-masking
                    CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
                    csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(requestHandler);
                })
                .userDetailsService(frontendUserDetailsService)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .headers(headers -> headers
                    .frameOptions(frame -> frame.sameOrigin())
                )
                .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((request, response, authException) -> writeJson(
                        response,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        Map.of("success", false, "message", "Nicht authentifiziert.")
                    ))
                );
        return http.build();
    }

        private void writeJson(HttpServletResponse response, int status, Map<String, Object> body) {
        try {
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(response.getWriter(), body);
        } catch (IOException ignored) {
            response.setStatus(status);
        }
        }

    /**
     * Forces the CSRF token to be loaded on every response so the XSRF-TOKEN
     * cookie is always set. Spring Security 6 uses deferred CSRF tokens by default,
     * which means the cookie might not be set until a state-changing request is made.
     * Without this filter, the first PATCH/PUT/POST/DELETE can fail with 403.
     */
    private static class CsrfCookieFilter extends org.springframework.web.filter.OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                // Accessing the token value forces the deferred token to be generated
                // and the cookie to be written in the response
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}
