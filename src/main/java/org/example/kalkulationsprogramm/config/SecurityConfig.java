package org.example.kalkulationsprogramm.config;

import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.springframework.security.web.csrf.CsrfFilter;

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
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/** Security-Konfiguration mit getrennten Desktop- und Mobile-Rechten. */
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
     * CORS für /api/**: nur ausdrücklich eingetragene fremde Webseiten, Standard keine
     * (siehe {@link ApiCorsConfigurationSource}). Wird in den Ketten ausdrücklich gesetzt;
     * die Kette für /api/internal/** schaltet CORS ab.
     */
    @Bean
    ApiCorsConfigurationSource apiCorsConfigurationSource(
            @Value("${app.cors.allowed-origins:}") String erlaubteOrigins) {
        return new ApiCorsConfigurationSource(erlaubteOrigins);
    }

    /**
     * S2S-Eingang für Anfrage-Funnel, Freigabe-Seite und Analytics-Snapshot der Firmen-Website.
     * Jeder Request braucht den gemeinsamen Schlüssel im Header {@code X-ERP-Website-Key}
     * ({@link WebsiteInternalApiKeyFilter}, fail-closed ohne konfigurierten Schlüssel). Ist
     * {@code cloudflare.access.enabled=true}, ist stattdessen ein gültiger Cloudflare-Access-JWT
     * ({@link CloudflareAccessJwtFilter}) Pflicht und ersetzt den Schlüssel. Spring-Auth (Session/Formular) ist bewusst
     * ausgeschaltet. Von außen ist der Pfad zusätzlich durch {@link ZeiterfassungSecurityFilter}
     * gesperrt; der Schlüssel schützt vor Geräten im Firmen- und Tailscale-Netz.
     *
     * <p><b>CSRF-Threat-Model (CodeQL java/spring-disabled-csrf-protection):</b>
     * CSRF bleibt eingeschaltet, nimmt aber alle Pfade dieser Kette per
     * {@code ignoringRequestMatchers("/api/internal/**")} aus – bewusst und sicher:
     * <ul>
     *   <li>Die Chain ist <i>stateless</i> — kein Session-Cookie, kein
     *       HTTP-Basic, keine vom Browser automatisch mitgesendete
     *       Authentifizierung. CSRF setzt aber genau das voraus.</li>
     *   <li>Authentifizierung erfolgt ausschliesslich ueber explizit gesetzte Header:
     *       den {@code X-ERP-Website-Key} oder den {@code Cf-Access-Jwt-Assertion}, den
     *       die Cloudflare-Edge NUR fuer Requests mit gueltigem Access Service Token
     *       setzt. Browser senden beides nie automatisch mit; die Geheimnisse liegen
     *       im Backend der Marketing-Seite.</li>
     *   <li>CORS ist hier ausdrücklich aus — cross-origin Anfragen aus dem
     *       Browser bekommen nie einen CORS-Header.</li>
     * </ul>
     * Damit ist keine Angriffsflaeche fuer Cross-Site-Request-Forgery
     * vorhanden; ein CSRF-Token waere reiner Ballast ohne Schutzwirkung.
     */
    @Bean
    @Order(0)
    public SecurityFilterChain funnelFilterChain(HttpSecurity http,
            @Value("${website.internal-api.key:}") String websiteKey) throws Exception {
        http
                .securityMatcher("/api/internal/**")
                // Nur Server-zu-Server, kein Browser: keine fremde Webseite darf hier mitlesen.
                .cors(AbstractHttpConfigurer::disable)
                // CSRF ignored: stateless key/JWT-authed S2S endpoint, see Javadoc above for full threat model.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/internal/**"))
                .addFilterBefore(cloudflareAccessJwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new WebsiteInternalApiKeyFilter(websiteKey, objectMapper), CloudflareAccessJwtFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }

    /** Gemeinsame API-Bereiche: immer authentifiziert; MobileApiPolicy begrenzt mobile Methoden. */
    static final String[] ZEITERFASSUNG_PATHS = {
            "/zeiterfassung", "/zeiterfassung/**", "/api/zeiterfassung/**", "/api/mitarbeiter/by-token/**",
            "/api/urlaub/**", "/api/kalender/mobile/**",
            "/api/push/**",
            "/api/dokumente/**", "/api/images/**",
            "/api/projekte/**", "/api/anfragen/**", "/api/kunden/**",
            "/api/lieferanten/**", "/api/produktkategorien/**", "/api/arbeitsgaenge/**",
            "/api/abwesenheit/**",
            // Gemeinsame Reklamationspfade. MobileApiPolicy verweigert unter anderem
            // Statusänderungen und Löschungen; Desktop-Zugriffe benötigen eine Session.
            "/api/reklamationen/lieferant/**", "/api/reklamationen/*", "/api/reklamationen/*/bilder",
            // Feiertags-Lookup im Urlaubsantrag; Regenerieren bleibt in der Desktop-Kette.
            "/api/zeitverwaltung/feiertage/zwischen",
            // Mobile Belegerfassung: Token-Prüfung im Filter plus fachliche Controller-Rechte.
            // Desktop-Buchhaltung bleibt in der apiFilterChain (Session + CSRF).
            "/api/buchhaltung/mobile/**",
            // Mobiles Diktat mit zentraler Token-Prüfung und zusätzlichen Controller-Rechten.
            "/api/spracheingabe/**"
    };

    @Bean
    @Order(1)
    public SecurityFilterChain zeiterfassungFilterChain(HttpSecurity http,
            ObjectProvider<MitarbeiterRepository> mitarbeiter, ApiCorsConfigurationSource apiCors,
            @Value("${zeiterfassung.security.trusted-proxies:}") String trustedProxies) throws Exception {
        var mobileFilter = new MobileTokenAuthenticationFilter(
                token -> mitarbeiter.getObject().findByLoginTokenAndAktivTrue(token),
                new TokenAttemptLimiter(), new ClientIpResolver(trustedProxies), objectMapper);
        http.securityMatcher(ZEITERFASSUNG_PATHS)
                .cors(cors -> cors.configurationSource(apiCors))
                .addFilterBefore(mobileFilter, CsrfFilter.class)
                .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    // Nur explizit geprüfte Tokens, niemals automatisch gesendete Cookies.
                    .ignoringRequestMatchers(request -> Boolean.TRUE.equals(request.getAttribute(MobileTokenAuthenticationFilter.EXPLICIT_TOKEN))))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                // Desktop-Sitzungen kommen auch hier vorbei (/api/lieferanten/**, /api/projekte/**).
                .addFilterBefore(new SitzungsRechteFilter(frontendUserDetailsService), AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/zeiterfassung", "/zeiterfassung/**").permitAll()
                    // Kunden löschen, aus Datenschutzgründen sperren oder anonymisieren: nur Admins (Issue #264).
                    .requestMatchers(HttpMethod.DELETE, "/api/kunden/*").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/kunden/*/datenschutz-sperre").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/kunden/*/anonymisieren").hasRole("ADMIN")
                    // Arbeitsgänge und ihre Stundensätze pflegt nur der Admin (Stammdaten → Arbeitsgänge).
                    // Lesen bleibt offen: Zeiterfassung, Kalender und Projekte brauchen die Liste.
                    .requestMatchers(HttpMethod.POST, "/api/arbeitsgaenge", "/api/arbeitsgaenge/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/arbeitsgaenge/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/arbeitsgaenge/**").hasRole("ADMIN")
                    .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((request, response, exception) -> writeJson(response, 401, Map.of("error", "Anmeldung erforderlich.")))
                    .accessDeniedHandler((request, response, exception) -> writeJson(response, 403, Map.of("error", "Zugriff verweigert."))));
        return http.build();
    }

    /**
     * Pfade der {@link #staticResourcesFilterChain(HttpSecurity)}: ohne Login erreichbar.
     *
     * <p>Vom Actuator ist bewusst nur {@code /actuator/health} offen (exakter Pfad,
     * kein Wildcard). Das nächtliche Update-Skript auf den Kundenservern fragt ihn
     * ohne Login ab; Details liefert er nicht ({@code show-details=never}).
     * Festgeschrieben in {@code OeffentlichePfadeTest}.
     */
    static final String[] OEFFENTLICHE_PFADE = {
            "/", "/index.html", "/favicon.ico", "/app-icon.png", "/assets/**",
            "/static/**", "/manifest.json", "/sw.js",
            "/dokument-editor", "/dokument-editor/**",
            "/login", "/login/**", "/onboarding", "/onboarding/**",
            "/error", "/error/**",
            "/actuator/health",
            // Update-Abfrage des OpenFile Launchers: läuft außerhalb des Browsers, ohne Login.
            // Nur lesend, Antwort unterschrieben (OpenFileLauncherService); von außen sperrt
            // ZeiterfassungSecurityFilter wie alles außer der Handy-App.
            "/openfile-launcher/aktuell",
            // Mac-Einrichtung per Terminal-Befehl: curl ohne Login. Nur lesend; gilt nur mit einem
            // Einmal-Link, den ein Admin erzeugt hat (10 Minuten, einmal einlösbar, EinmalLinks).
            "/openfile-launcher/mac/**"
    };

    /**
     * Statische Ressourcen + Health: erlaubt ohne Auth.
     * Wichtig: /error muss hier stehen, sonst gibt Spring Boot
     * nach einem internen Fehler (z.B. 404) wieder 401 zurück.
     *
     * <p><b>CSRF (CodeQL java/spring-disabled-csrf-protection):</b> bleibt aktiv, nimmt aber
     * genau die Pfade dieser Kette aus. Session-Cookies kommen hier zwar mit, es gibt aber
     * keine schreibenden Endpoints (nur Dateien, Health und die Fehlerseite) – ein Token
     * hätte nichts zu schützen. Deshalb gilt:
     * <ul>
     *   <li>Neue Mappings unter diesen Pfaden nur lesend (GET/HEAD); schreibende gehören unter
     *       {@code /api/**}. {@code OeffentlichePfadeCsrfTest} schlägt sonst fehl.</li>
     *   <li>Von hier nie per Forward in eine geschützte Kette springen: Der CsrfFilter
     *       dieser Kette markiert den Request als geprüft, der Filter der Zielkette
     *       würde dann übersprungen.</li>
     * </ul>
     */
    @Bean
    @Order(2)
    public SecurityFilterChain staticResourcesFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(OEFFENTLICHE_PFADE)
                .csrf(csrf -> csrf.ignoringRequestMatchers(OEFFENTLICHE_PFADE))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Wartungs- und Massenläufe, die man nur direkt aufruft (siehe docs/ADMIN_ENDPUNKTE.md).
     * Gesperrt für jede HTTP-Methode, nur Admins kommen durch. Festgeschrieben in
     * {@code AdminMassenlaeufeSecurityTest}.
     */
    static final String[] ADMIN_MASSENLAEUFE = {
            "/api/emails/import",
            "/api/emails/scan-spam",
            "/api/emails/scan-inquiries",
            "/api/emails/scan-assignments",
            "/api/emails/scan-steuerberater",
            "/api/emails/backfill-parents",
            "/api/emails/spam-model/rescore",
            "/api/emails/spam-model/bootstrap",
            "/api/lieferant-dokumente/process-assigned-emails",
            "/api/lieferant-dokumente/process-email/*",
            "/api/lieferant-dokumente/lieferant/*/process-emails",
            "/api/zeitverwaltung/saldo-cache/warmup"
    };

    /**
     * Alle API-Endpoints: Session-basierte Authentifizierung via formLogin + CSRF-Schutz.
     * CSRF wird über CookieCsrfTokenRepository aktiviert, damit der Browser-Client
     * das XSRF-TOKEN-Cookie lesen und als X-XSRF-TOKEN-Header mitsenden kann.
     * Anmelden und Abmelden gehen ohne Token nur von der eigenen Oberfläche
     * ({@link AnmeldungVonEigenerSeite}, Schutz vor Login-CSRF).
     */
    @Bean
    @Order(3)
    public SecurityFilterChain apiFilterChain(HttpSecurity http, ApiCorsConfigurationSource apiCors) throws Exception {
        http
                .securityMatcher("/api/**")
                .cors(cors -> cors.configurationSource(apiCors))
                .csrf(csrf -> {
                    // Use CsrfTokenRequestAttributeHandler (instead of XorCsrfTokenRequestAttributeHandler)
                    // so the raw cookie value is accepted as-is without XOR-masking
                    CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
                    csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(requestHandler)
                        .ignoringRequestMatchers(new AnmeldungVonEigenerSeite());
                })
            // Force the CSRF cookie to be set on every response (Spring Security 6 deferred token fix)
            .addFilterAfter(new CsrfCookieFilter(), org.springframework.security.web.csrf.CsrfFilter.class)
            .addFilterBefore(new SitzungsRechteFilter(frontendUserDetailsService), AuthorizationFilter.class)
            .userDetailsService(frontendUserDetailsService)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/logout", "/api/auth/register", "/api/auth/bootstrap-status").permitAll()
                .requestMatchers("/api/auth/me", "/api/auth/me/credentials").authenticated()
                // Steuerberater-Kontakte und Firmenstammdaten werden beim Versand der
                // Stundenaufstellung und beim Belegexport nur gelesen (Ansprechpartner
                // auswaehlen, Firmenname im Betreff). Das duerfen alle angemeldeten
                // Benutzer; Anlegen/Aendern/Loeschen bleibt Admin-Sache.
                // Das Firmenlogo ist kein Geheimnis und darf ebenfalls jeder Angemeldete laden.
                .requestMatchers(HttpMethod.GET,
                        "/api/firma",
                        "/api/firma/logo",
                        "/api/firma/steuerberater",
                        "/api/firma/steuerberater/*").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/abteilungen/*/berechtigungen").hasRole("ADMIN")
                // Abteilungen anlegen, umbenennen, löschen (Einstellungen → Abteilungen & Rechte). Lesen bleibt offen,
                // weil der Mitarbeiter-Editor und der Monatsabschluss die Liste brauchen.
                .requestMatchers(HttpMethod.POST, "/api/abteilungen").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/abteilungen/*").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/abteilungen/*").hasRole("ADMIN")
                // Feiertage anlegen, anpassen, löschen wirkt auf Soll und Lohn aller Mitarbeiter (Issue #222).
                .requestMatchers(HttpMethod.POST, "/api/zeitverwaltung/feiertage",
                        "/api/zeitverwaltung/feiertage/regenerieren").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/zeitverwaltung/feiertage/*").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/zeitverwaltung/feiertage/*").hasRole("ADMIN")
                .requestMatchers("/api/firma/**", "/api/settings/**", "/api/frontend-users/**", "/api/postfaecher", "/api/postfaecher/**").hasRole("ADMIN")
                // Einrichtungs-Dateien des OpenFile Launchers gibt es nur unter Einstellungen (Admin)
                .requestMatchers("/api/openfile-launcher/**").hasRole("ADMIN")
                // System-Signatur fuer automatische E-Mails ist firmenweite Konfiguration
                // (wirkt auf Mahnungen, Auto-Auftragsbestaetigungen) — nur Admins duerfen sie umbiegen.
                .requestMatchers(HttpMethod.PUT, "/api/email/signatures/*/system-default").hasRole("ADMIN")
                // Firmen-Vorlagen legen das Signatur-Design fuer alle Mitarbeiter fest - nur Admins.
                .requestMatchers(HttpMethod.PUT, "/api/email/signatures/*/firmen-vorlage").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/email/signatures/*/firmen-vorlage").hasRole("ADMIN")
                // Formularwesen (Briefpapier/Dokumentvorlagen) pflegt nur der Admin. Lesen bleibt für alle,
                // weil der Dokument-Editor die Vorlagen beim Erstellen von Angeboten und Rechnungen lädt.
                // Die Dokumentnummer vergibt das System beim Erstellen eines Dokuments – bleibt offen.
                .requestMatchers(HttpMethod.POST, "/api/formulare/dokumentnummer").authenticated()
                // Mailtexte für Dokumente gelten für alle künftigen Mails: lesen alle, ändern nur Admins (Issue #271).
                .requestMatchers(HttpMethod.POST, "/api/email-textvorlagen", "/api/email-textvorlagen/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/email-textvorlagen", "/api/email-textvorlagen/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/email-textvorlagen", "/api/email-textvorlagen/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/formulare/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/formulare/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/formulare/**").hasRole("ADMIN")
                // Verrechnungslohn-Uebernahme schreibt Stundensaetze fuer alle Arbeitsgaenge
                // eines Jahres - kalkulatorische Massenmutation, nur Admin.
                .requestMatchers(HttpMethod.POST, "/api/verrechnungslohn/uebernehmen").hasRole("ADMIN")
                // Manueller Mahn-Lauf verschickt echte E-Mails an Kunden - nur Admin.
                .requestMatchers(HttpMethod.POST, "/api/mahnwesen/lauf").hasRole("ADMIN")
                // E-Mail-Admin-Backfills sind betriebsweite, schreibende Massenoperationen
                // auf Belegen/Attachments - nur Admin (VPN ersetzt keine Authentifizierung).
                .requestMatchers(HttpMethod.POST, "/api/emails/admin/**").hasRole("ADMIN")
                // Massenläufe ohne Knopf in der Oberfläche: gehen über alle Mails, Lieferanten-
                // Dokumente oder Zeitkonten und kosten teils KI-Aufrufe - nur Admin.
                .requestMatchers(ADMIN_MASSENLAEUFE).hasRole("ADMIN")
                // Lieferantendokumente löschen ist eine irreversible Admin-Aktion
                .requestMatchers(HttpMethod.DELETE, "/api/lieferant-dokumente/**").hasRole("ADMIN")
                // Backfill der Dokumentenketten schreibt über den gesamten Lieferanten-Bestand.
                // Bewusst ohne Button: nur Admins rufen ihn direkt auf. Absichtlich NICHT
                // unter /api/lieferanten/** – das liegt in der mobilen Chain (Token oder Session).
                .requestMatchers(HttpMethod.POST,
                        "/api/lieferant-dokumente/relink-all",
                        "/api/lieferant-dokumente/lieferant/*/relink").hasRole("ADMIN")
                // Duplikat-Liste zeigt Nummern und Lieferanten aller Dokumenttypen; die Massen-Neuanalyse
                // eines Lieferanten ueberschreibt Geschaeftsdaten aller Typen - beides nur Admin.
                .requestMatchers(HttpMethod.GET, "/api/lieferant-dokumente/duplicates").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/lieferant-dokumente/lieferant/*/reanalyze").hasRole("ADMIN")
                // Projekt-Wartungsaktionen schreiben ueber den gesamten Bestand - nur Admin.
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // Beiträge-Modul pusht direkt in den öffentlichen Website-Auftritt. MVP-mäßig
                // nur Admin. TODO: später über Abteilung.darfBeitraegeVeroeffentlichen granularer
                // steuern, analog zu darfFreigabeAnnahmePushen (siehe AbteilungBerechtigungController).
                .requestMatchers("/api/beitraege/**").hasRole("ADMIN")
                // FRITZ!Box-Zugangsdaten und Nachholen der Telefondaten - nur Admin.
                // Das Sehen von Anrufen regelt das Abteilungs-Recht darfTelefonSehen im Controller.
                .requestMatchers("/api/telefon/einstellungen", "/api/telefon/einstellungen/**", "/api/telefon/admin/**").hasRole("ADMIN")
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
                .addFilterBefore(new SitzungsRechteFilter(frontendUserDetailsService), AuthorizationFilter.class)
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
        } catch (IOException | JacksonException ignored) {
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
