package org.example.kalkulationsprogramm.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.function.Supplier;

import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login-CSRF und DNS-Rebinding durch die echten Ketten: Die eigene Oberfläche – auch hinter
 * einem TLS-Proxy – meldet sich weiter an; eine fremde Webseite kann weder jemanden anmelden
 * noch über den Browser eines Mitarbeiters die offenen Endpunkte erreichen. Alle Adressen und
 * Namen sind Dummy-Werte.
 */
@WebMvcTest(controllers = LoginCsrfUndDnsRebindingTest.ProbeController.class)
@Import({SecurityConfig.class, ZeiterfassungSecurityFilter.class, LoginCsrfUndDnsRebindingTest.FilterBeans.class,
        LoginCsrfUndDnsRebindingTest.ProbeController.class})
@TestPropertySource(properties = {
        "app.security.allowed-hosts=erp.example.test",
        "zeiterfassung.base-url=https://zeit.example.test"})
class LoginCsrfUndDnsRebindingTest {

    private static final String ERP = "erp-rechner.tail-xxxx.ts.net";
    private static final String FREMD = "angreifer.example";
    private static final String PASSWORT = "Test-Passwort-123";

    @TestConfiguration
    static class FilterBeans {
        @Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
    }

    /** Stellvertretend für die ohne Anmeldung erreichbaren Endpunkte. */
    @RestController
    static class ProbeController {
        @GetMapping("/api/auth/bootstrap-status") String bootstrapStatus() { return "status"; }
        @PostMapping("/api/auth/register") String registrieren() { return "registriert"; }
        @GetMapping("/actuator/health") String health() { return "UP"; }
        @GetMapping("/api/zeiterfassung/projekte") String projekte() { return "projekte"; }
    }

    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ZeiterfassungSecurityFilter netzgrenze;
    @MockitoBean FrontendUserDetailsService users;
    @MockitoBean MitarbeiterRepository mitarbeiter;

    @BeforeEach
    void rechnernameUnabhaengigVomTestrechner() {
        // Sonst hinge das Ergebnis vom Namen des Rechners ab, auf dem der Test läuft.
        Supplier<Set<String>> keinRechnername = Set::of;
        ReflectionTestUtils.setField(netzgrenze, "rechnernamen", keinRechnername);
        ReflectionTestUtils.setField(netzgrenze, "adressen", null);
    }

    // ---------- Login-CSRF ----------

    @Test
    void eigeneOberflaecheHinterTlsProxyMeldetSichAn() throws Exception {
        when(users.loadUserByUsername("max.mustermann")).thenReturn(
                User.withUsername("max.mustermann").password(passwordEncoder.encode(PASSWORT)).roles("USER").build());

        mvc.perform(anmelden().with(ueberTailscaleServe(ERP)).header("Origin", "https://" + ERP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void fremdeWebseiteKannNiemandenAnmelden() throws Exception {
        for (String origin : new String[]{"https://" + FREMD, "null", "https://" + ERP + ":8443"}) {
            mvc.perform(anmelden().with(ueberTailscaleServe(ERP)).header("Origin", origin))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(users);
    }

    @Test
    void mitCsrfTokenEntscheidetDerTokenNichtDieHerkunft() throws Exception {
        // Die Oberfläche schickt den Token bei jedem POST mit; dann zählt die Origin-Prüfung nicht.
        when(users.loadUserByUsername("max.mustermann")).thenReturn(
                User.withUsername("max.mustermann").password(passwordEncoder.encode(PASSWORT)).roles("USER").build());

        mvc.perform(anmelden().with(ueberTailscaleServe(ERP)).header("Origin", "https://" + FREMD).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void anmeldungOhneOriginUndVomEntwicklungsserverWieBisher() throws Exception {
        when(users.loadUserByUsername(anyString())).thenThrow(new UsernameNotFoundException("unbekannt"));

        mvc.perform(anmelden().with(ausDemBuero("192.168.178.20", 8080)))
                .andExpect(status().isUnauthorized());
        mvc.perform(anmelden().with(ausDemBuero("localhost", 8080)).header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized());
        verify(users, times(2)).loadUserByUsername("max.mustermann");
    }

    @Test
    void fremdeWebseiteKannNiemandenAbmelden() throws Exception {
        mvc.perform(post("/api/auth/logout").with(ueberTailscaleServe(ERP)).header("Origin", "https://" + FREMD)
                        .with(user("max.mustermann")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").with(ueberTailscaleServe(ERP)).header("Origin", "https://" + ERP)
                        .with(user("max.mustermann")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ---------- DNS-Rebinding ----------

    @Test
    void dnsRebindingErreichtKeinenOffenenEndpunkt() throws Exception {
        // Die fremde Webseite hat ihren Namen auf das ERP umgebogen; der Browser schickt ihn als Host mit.
        MockHttpServletRequestBuilder[] anfragen = {
                get("/api/auth/bootstrap-status"),
                post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"),
                anmelden().header("Origin", "http://" + FREMD + ":8080"),
                get("/actuator/health")};
        for (MockHttpServletRequestBuilder anfrage : anfragen) {
            mvc.perform(anfrage.with(ausDemBuero(FREMD, 8080)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value(ZeiterfassungSecurityFilter.FREMDE_ADRESSE));
        }
        // Auch über tailscale serve (ein Büro-PC im Tailnet) kommt der fremde Name nicht durch.
        mvc.perform(get("/api/auth/bootstrap-status").with(ueberTailscaleServe(FREMD)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(users);
    }

    @Test
    void gefaelschteWeiterleitungsHeaderMachenDieAnfrageNurZuExtern() throws Exception {
        // Das Skript der fremden Webseite setzt selbst X-Forwarded-For oder den Funnel-Header.
        // Dann gilt die Anfrage als von außen – und dort sind die offenen Endpunkte gesperrt.
        String[][] header = {{"X-Forwarded-For", "100.101.102.103"}, {"Tailscale-Funnel-Request", "?1"}};
        for (String[] gefaelscht : header) {
            MockHttpServletRequestBuilder[] anfragen = {
                    post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"),
                    anmelden().header("Origin", "http://" + FREMD + ":8080"),
                    get("/actuator/health")};
            for (MockHttpServletRequestBuilder anfrage : anfragen) {
                mvc.perform(anfrage.with(ausDemBuero(FREMD, 8080)).header(gefaelscht[0], gefaelscht[1]))
                        .andExpect(status().isForbidden())
                        .andExpect(content().string(Matchers.not(Matchers.containsString("registriert"))))
                        .andExpect(content().string(Matchers.not(Matchers.containsString("UP"))));
            }
        }
        verifyNoInteractions(users);
    }

    @Test
    void websiteSchnittstelleNurUnterEigenerAdresse() throws Exception {
        // Der Website-Server ruft das ERP über *.ts.net auf und kommt bis zur Schlüsselprüfung (hier ohne Schlüssel: 401).
        mvc.perform(post("/api/internal/anfrage").with(ueberTailscaleServe(ERP)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/internal/anfrage").with(ausDemBuero(FREMD, 8080)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(ZeiterfassungSecurityFilter.FREMDE_ADRESSE));
    }

    @Test
    void browserSiehtEinenSatzOhneDieFremdeAdresse() throws Exception {
        mvc.perform(get("/").accept(MediaType.TEXT_HTML).with(ausDemBuero(FREMD, 8080)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(Matchers.containsString(ZeiterfassungSecurityFilter.FREMDE_ADRESSE)))
                .andExpect(content().string(Matchers.not(Matchers.containsString(FREMD))));
    }

    @Test
    void bisherigeAdressenBleibenErreichbar() throws Exception {
        String[] adressen = {"192.168.178.20", "localhost", "erp-pc", "app", "erp-pc.fritz.box", ERP,
                "[fd7a:115c:a1e0::1]", "erp.example.test", "zeit.example.test"};
        for (String host : adressen) {
            mvc.perform(get("/api/auth/bootstrap-status").with(ausDemBuero(host, 8080)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("status"));
        }
        // Health-Abfrage des Updaters aus dem Docker-Netz (http://app:8080/actuator/health).
        mvc.perform(get("/actuator/health").with(ausDemBuero("app", 8080)).with(r -> { r.setRemoteAddr("172.18.0.5"); return r; }))
                .andExpect(status().isOk());
    }

    @Test
    void vonAussenEntscheidetWeiterDieNetzgrenze() throws Exception {
        // cloudflared mit eigener Domain: extern, nur die Handy-App – ohne Eintrag in allowed-hosts.
        mvc.perform(get("/api/zeiterfassung/projekte")
                        .with(r -> { r.setRemoteAddr("127.0.0.1"); r.setServerName("zeiterfassung.firma.example"); return r; })
                        .header("CF-Connecting-IP", "203.0.113.8").header("X-Forwarded-For", "203.0.113.8"))
                .andExpect(status().isUnauthorized());
    }

    private static MockHttpServletRequestBuilder anmelden() {
        return post("/api/auth/login").param("username", "max.mustermann").param("password", PASSWORT);
    }

    /** Browser im Büronetz direkt auf das ERP. */
    private static RequestPostProcessor ausDemBuero(String host, int port) {
        return r -> {
            r.setRemoteAddr("192.168.178.30");
            r.setScheme("http");
            r.setServerName(host);
            r.setServerPort(port);
            return r;
        };
    }

    /** tailscale serve: Browser meldet https, beim ERP kommt http von localhost an, Host bleibt erhalten. */
    private static RequestPostProcessor ueberTailscaleServe(String host) {
        return r -> {
            r.setRemoteAddr("127.0.0.1");
            r.addHeader("X-Forwarded-For", "100.101.102.103");
            r.setScheme("http");
            r.setServerName(host);
            r.setServerPort(80);
            return r;
        };
    }
}
