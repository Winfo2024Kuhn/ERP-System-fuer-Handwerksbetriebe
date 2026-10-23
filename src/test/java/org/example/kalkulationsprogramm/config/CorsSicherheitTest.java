package org.example.kalkulationsprogramm.config;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CORS durch die echten Security-Ketten: Nur die eingetragene Webseite darf mitlesen (nicht
 * schreiben), fremde Webseiten gar nicht, die eigene Oberfläche hinter einem TLS-Proxy wird nicht ausgesperrt und die
 * Website-Schnittstelle /api/internal/** hat gar kein CORS. Alle Adressen sind Dummy-Werte.
 */
@WebMvcTest(controllers = CorsSicherheitTest.ProbeController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ZeiterfassungSecurityFilter.class))
@Import({SecurityConfig.class, CorsSicherheitTest.ProbeController.class, CorsSicherheitTest.FilterBeans.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=https://intranet.example.test",
        "website.internal-api.key=test-schluessel-nur-fuer-tests"})
class CorsSicherheitTest {

    private static final String ERLAUBT = "https://intranet.example.test";
    private static final String FREMD = "https://angreifer.example";
    private static final String TOKEN = "12345678-1234-4234-8234-123456789abc";
    private static final String ACAO = "Access-Control-Allow-Origin";

    @TestConfiguration
    static class FilterBeans {
        @Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/cors-probe") String lesen() { return "kundenliste"; }
        @PostMapping("/api/cors-probe") String speichern() { return "gespeichert"; }
        @GetMapping("/api/zeiterfassung/projekte") String projekte() { return "projekte"; }
    }

    @Autowired MockMvc mvc;
    @MockitoBean FrontendUserDetailsService users;
    @MockitoBean MitarbeiterRepository mitarbeiter;

    @Test
    void eingetrageneWebseiteDarfMitAnmeldungLesen() throws Exception {
        mvc.perform(get("/api/cors-probe").header("Origin", ERLAUBT).with(user("max.mustermann")))
                .andExpect(status().isOk())
                .andExpect(header().string(ACAO, ERLAUBT))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void preflightDerEingetragenenWebseiteKlapptVorDerAnmeldung() throws Exception {
        mvc.perform(preflight("/api/cors-probe", ERLAUBT))
                .andExpect(status().isOk())
                .andExpect(header().string(ACAO, ERLAUBT))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void eingetrageneWebseiteDarfWederSchreibenNochEigeneHeaderSenden() throws Exception {
        for (String methode : new String[]{"POST", "PUT", "PATCH", "DELETE"}) {
            mvc.perform(options("/api/cors-probe").header("Origin", ERLAUBT).header("Access-Control-Request-Method", methode))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(ACAO));
        }
        for (String eigenerHeader : new String[]{"X-XSRF-TOKEN", "X-Auth-Token"}) {
            mvc.perform(preflight("/api/zeiterfassung/projekte", ERLAUBT).header("Access-Control-Request-Headers", eigenerHeader))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(ACAO));
        }
    }

    @Test
    void preflightFremderWebseitenWirdAbgelehnt() throws Exception {
        String[] fremde = {FREMD, "null", "http://intranet.example.test", "https://intranet.example.test:8443",
                "https://intranet.example.test.angreifer.example", "https://sub.intranet.example.test"};
        for (String origin : fremde) {
            mvc.perform(preflight("/api/cors-probe", origin))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(ACAO));
        }
    }

    @Test
    void fremdeWebseiteBekommtKeineLeserechte() throws Exception {
        mvc.perform(get("/api/cors-probe").header("Origin", FREMD).with(user("max.mustermann")))
                .andExpect(header().doesNotExist(ACAO))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void eigeneOberflaecheHinterTlsProxyDarfSpeichern() throws Exception {
        // tailscale serve: Browser meldet https, beim ERP kommt die Anfrage als http an.
        mvc.perform(post("/api/cors-probe").header("Origin", "https://erp.example.test")
                        .with(r -> { r.setScheme("http"); r.setServerName("erp.example.test"); r.setServerPort(80); return r; })
                        .with(user("max.mustermann")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("gespeichert"))
                .andExpect(header().doesNotExist(ACAO));
    }

    @Test
    void schreibendeAnfrageEinerEingetragenenAdresseWirdNichtAbgewiesen() throws Exception {
        // Wer die eigene ERP-Adresse einträgt, darf weiter speichern – nur ohne CORS-Header.
        mvc.perform(post("/api/cors-probe").header("Origin", ERLAUBT).with(user("max.mustermann")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("gespeichert"))
                .andExpect(header().doesNotExist(ACAO));
    }

    @Test
    void handyAppKetteFolgtDerselbenListe() throws Exception {
        mvc.perform(preflight("/api/zeiterfassung/projekte", ERLAUBT))
                .andExpect(status().isOk())
                .andExpect(header().string(ACAO, ERLAUBT));
        mvc.perform(preflight("/api/zeiterfassung/projekte", FREMD))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(ACAO));

        Mitarbeiter mitarbeiterIn = new Mitarbeiter();
        mitarbeiterIn.setId(7L);
        mitarbeiterIn.setAktiv(true);
        when(mitarbeiter.findByLoginTokenAndAktivTrue(TOKEN)).thenReturn(Optional.of(mitarbeiterIn));
        mvc.perform(get("/api/zeiterfassung/projekte").header("Origin", FREMD).header("X-Auth-Token", TOKEN))
                .andExpect(header().doesNotExist(ACAO));
    }

    @Test
    void websiteSchnittstelleHatKeinCorsAuchNichtFuerEingetrageneWebseiten() throws Exception {
        mvc.perform(preflight("/api/internal/anfrage", ERLAUBT)).andExpect(header().doesNotExist(ACAO));
        mvc.perform(post("/api/internal/anfrage").header("Origin", ERLAUBT)
                        .header("X-ERP-Website-Key", "test-schluessel-nur-fuer-tests")
                        .contentType("application/json").content("{}"))
                .andExpect(header().doesNotExist(ACAO));
    }

    @Test
    void seitenAusserhalbVonApiHabenKeinCors() throws Exception {
        // Handy-App-, Static- und Catch-All-Kette: kein CORS ausserhalb von /api/**.
        for (String pfad : new String[]{"/zeiterfassung/", "/login", "/actuator/health", "/irgendeine-seite"}) {
            mvc.perform(get(pfad).header("Origin", ERLAUBT)).andExpect(header().doesNotExist(ACAO));
            mvc.perform(preflight(pfad, ERLAUBT)).andExpect(header().doesNotExist(ACAO));
        }
    }

    private static MockHttpServletRequestBuilder preflight(String pfad, String origin) {
        return options(pfad).header("Origin", origin).header("Access-Control-Request-Method", "GET");
    }
}
