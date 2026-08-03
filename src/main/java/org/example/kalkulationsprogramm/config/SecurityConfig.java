package org.example.kalkulationsprogramm.config;

import java.io.IOException;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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
 * <p><b>Achtung:</b> Die Pfade der {@link #zeiterfassungFilterChain(HttpSecurity)}
 * sind vollständig unauthentifiziert erreichbar. Es gibt dort KEINE
 * Token-Prüfung auf Chain-Ebene — Details und bekannte Lücken siehe Javadoc
 * an {@link #ZEITERFASSUNG_PATHS}.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final FrontendUserDetailsService frontendUserDetailsService;
    private final CloudflareAccessJwtFilter cloudflareAccessJwtFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Public S2S-Eingang für den Anfrage-Funnel der Marketing-Webseite.
     * Erreichbar nur über Cloudflare-Tunnel + Access Service Token; der
     * {@link CloudflareAccessJwtFilter} prüft den signierten CF-Access-JWT als
     * zweite Schicht (Defense-in-Depth). Spring-Auth ist bewusst ausgeschaltet.
     *
     * <p><b>CSRF-Threat-Model (CodeQL java/spring-disabled-csrf-protection):</b>
     * CSRF wird hier bewusst und sicher deaktiviert:
     * <ul>
     *   <li>Die Chain ist <i>stateless</i> — kein Session-Cookie, kein
     *       HTTP-Basic, keine vom Browser automatisch mitgesendete
     *       Authentifizierung. CSRF setzt aber genau das voraus.</li>
     *   <li>Authentifizierung erfolgt ausschliesslich ueber den
     *       {@code Cf-Access-Jwt-Assertion}-Header, den die Cloudflare-Edge
     *       NUR fuer Requests mit gueltigem Access Service Token setzt.
     *       Browser koennen diesen Header weder selbststaendig setzen noch
     *       das zugehoerige {@code CF-Access-Client-Secret} ausliefern
     *       (Service-Token-Geheimnis liegt im Backend der Marketing-Seite).</li>
     *   <li>Es ist KEIN CORS konfiguriert — cross-origin POSTs aus dem
     *       Browser scheitern bereits am fehlenden CORS-Header.</li>
     * </ul>
     * Damit ist keine Angriffsflaeche fuer Cross-Site-Request-Forgery
     * vorhanden; ein CSRF-Token waere reiner Ballast ohne Schutzwirkung.
     */
    @Bean
    @Order(0)
    public SecurityFilterChain funnelFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/internal/**")
                // CSRF disabled: stateless JWT-authed S2S endpoint, see Javadoc above for full threat model.
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(cloudflareAccessJwtFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }

    /**
     * Pfade der {@link #zeiterfassungFilterChain(HttpSecurity)}.
     *
     * <p><b>Alles hier ist ohne jede Authentifizierung erreichbar</b> — inklusive
     * schreibender Operationen. Die Chain hat {@code permitAll()} und {@code csrf.disable()},
     * und weil sie {@code @Order(1)} hat, erreichen diese Pfade die
     * {@link #apiFilterChain(HttpSecurity)} (Session-Login + CSRF + Rollen) nie.
     *
     * <p><b>Kein Token-Schutz:</b> Der {@link ZeiterfassungSecurityFilter} prüft
     * <i>keine</i> Token. Er vergleicht die Client-IP gegen lokale Präfixe (inkl.
     * Tailscale {@code 100.}) und lässt lokale IPs komplett ungeprüft durch; für
     * externe IPs greift nur eine Pfad-Whitelist, ebenfalls ohne Auth. Einzelne
     * Controller lösen zwar einen Mitarbeiter über {@code ?token=} bzw.
     * {@code X-Auth-Token} auf, andere vertrauen aber den frei wählbaren Headern
     * {@code X-Mitarbeiter-Id} / {@code X-User-Profile-Id}. Ein Chain-weiter
     * Schutz existiert nicht.
     *
     * <p><b>Bekannte Lücke:</b> Die Wildcards {@code /api/projekte/**},
     * {@code /api/anfragen/**}, {@code /api/kunden/**}, {@code /api/lieferanten/**},
     * {@code /api/produktkategorien/**} und {@code /api/arbeitsgaenge/**} öffnen die
     * kompletten Controller (~134 Endpoints, davon ~78 schreibend), obwohl die PWA
     * nur einen kleinen Teil davon braucht. Aktuell durch den VPN-Betrieb abgefedert,
     * beim Ziel "öffentlicher Server mit reinem Login" aber nicht mehr tragbar.
     * Der Ist-Zustand ist in {@code ZeiterfassungFilterChainMatcherTest} festgeschrieben;
     * beim Eingrenzen schlägt der Test bewusst fehl und muss mit angepasst werden.
     */
    static final String[] ZEITERFASSUNG_PATHS = {
            "/zeiterfassung", "/zeiterfassung/**", "/api/zeiterfassung/**", "/api/mitarbeiter/by-token/**",
            "/api/urlaub/**", "/api/kalender/mobile/**",
            "/api/push/**",
            "/api/dokumente/**", "/api/images/**",
            "/api/projekte/**", "/api/anfragen/**", "/api/kunden/**",
            "/api/lieferanten/**", "/api/produktkategorien/**", "/api/arbeitsgaenge/**",
            "/api/abwesenheit/**",
            // Reklamations-Seiten der PWA: liefen bisher gegen die apiFilterChain und
            // damit fuer Mobile-Nutzer in 401. Bewusst eng gefasst — PATCH /{id}/status
            // und GET /lieferscheine/search bleiben hinter dem Login.
            // Hinweis: /api/reklamationen/* deckt methodenunabhaengig auch
            // DELETE /api/reklamationen/{id} mit ab.
            "/api/reklamationen/lieferant/**", "/api/reklamationen/*", "/api/reklamationen/*/bilder",
            // Feiertags-Lookup im Urlaubsantrag. Exakter Pfad, damit
            // POST /feiertage/regenerieren nicht mit geoeffnet wird.
            "/api/zeitverwaltung/feiertage/zwischen",
            // Buchhaltungs-Belegerfassung: NUR der Mobile-Subpath ist Token-only.
            // PC-Endpoints wie /api/buchhaltung/belege bleiben in der apiFilterChain
            // (Session-Auth + CSRF). Auth-Pruefung im Controller via Mitarbeiter-Token.
            "/api/buchhaltung/mobile/**"
    };

    /**
     * Zeiterfassungs-PWA: erreichbar ohne Login.
     *
     * <p>Welche Pfade das betrifft und warum das derzeit ungeschützt ist,
     * steht an {@link #ZEITERFASSUNG_PATHS}.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain zeiterfassungFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(ZEITERFASSUNG_PATHS)
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
                // System-Signatur fuer automatische E-Mails ist firmenweite Konfiguration
                // (wirkt auf Mahnungen, Auto-Auftragsbestaetigungen) — nur Admins duerfen sie umbiegen.
                .requestMatchers(HttpMethod.PUT, "/api/email/signatures/*/system-default").hasRole("ADMIN")
                // Verrechnungslohn-Uebernahme schreibt Stundensaetze fuer alle Arbeitsgaenge
                // eines Jahres - kalkulatorische Massenmutation, nur Admin.
                .requestMatchers(HttpMethod.POST, "/api/verrechnungslohn/uebernehmen").hasRole("ADMIN")
                // Manueller Mahn-Lauf verschickt echte E-Mails an Kunden - nur Admin.
                .requestMatchers(HttpMethod.POST, "/api/mahnwesen/lauf").hasRole("ADMIN")
                // E-Mail-Admin-Backfills sind betriebsweite, schreibende Massenoperationen
                // auf Belegen/Attachments - nur Admin (VPN ersetzt keine Authentifizierung).
                .requestMatchers(HttpMethod.POST, "/api/emails/admin/**").hasRole("ADMIN")
                // Lieferantendokumente löschen ist eine irreversible Admin-Aktion
                .requestMatchers(HttpMethod.DELETE, "/api/lieferant-dokumente/**").hasRole("ADMIN")
                // Projekt-Wartungsaktionen schreiben ueber den gesamten Bestand - nur Admin.
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
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
