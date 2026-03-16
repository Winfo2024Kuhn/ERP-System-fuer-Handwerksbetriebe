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
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

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
                    "/dokument-editor", "/dokument-editor/**",
                    "/login", "/login/**", "/onboarding", "/onboarding/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Alle API-Endpoints: Session-basierte Authentifizierung via formLogin mit CSRF-Schutz.
     *
     * CSRF wird über CookieCsrfTokenRepository aktiviert: Spring Security setzt ein
     * XSRF-TOKEN-Cookie (httpOnly=false), das der SPA-Client lesen und als
     * X-XSRF-TOKEN-Header bei zustandsändernden Requests (POST, PUT, PATCH, DELETE)
     * mitsenden muss.
     */
    @Bean
    @Order(3)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(requestHandler)
                )
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
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
            .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }

    /**
     * Erzwingt das Auflösen des CSRF-Tokens bei jeder Anfrage, damit Spring Security 6's
     * Lazy-Token-Mechanismus das XSRF-TOKEN-Cookie tatsächlich in die Antwort schreibt.
     */
    private static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                // Trigger lazy token resolution so that CookieCsrfTokenRepository writes the
                // XSRF-TOKEN cookie to the response. The return value is intentionally unused.
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
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
}
