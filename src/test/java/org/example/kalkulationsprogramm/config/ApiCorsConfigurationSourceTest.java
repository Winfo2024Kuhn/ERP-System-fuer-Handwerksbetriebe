package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;

/** Liste erlaubter fremder Webseiten und die Entscheidung je Anfrage (ohne Spring-Kontext). */
class ApiCorsConfigurationSourceTest {

    private static final String INTRANET = "https://intranet.example.test";

    @Test
    void genaueAdressenWerdenVereinheitlicht() {
        assertThat(ApiCorsConfigurationSource.normalisieren(INTRANET)).isEqualTo(INTRANET);
        assertThat(ApiCorsConfigurationSource.normalisieren("HTTPS://Intranet.Example.TEST/")).isEqualTo(INTRANET);
        assertThat(ApiCorsConfigurationSource.normalisieren("http://192.168.0.10:8080")).isEqualTo("http://192.168.0.10:8080");
        assertThat(ApiCorsConfigurationSource.normalisieren("http://[::1]:5173")).isEqualTo("http://[::1]:5173");
    }

    @Test
    void standardportWirdWeggelassenWieImBrowser() {
        assertThat(ApiCorsConfigurationSource.normalisieren("https://intranet.example.test:443")).isEqualTo(INTRANET);
        assertThat(ApiCorsConfigurationSource.normalisieren("http://192.168.0.10:80")).isEqualTo("http://192.168.0.10");
        assertThat(ApiCorsConfigurationSource.normalisieren("http://intranet.example.test:443"))
                .isEqualTo("http://intranet.example.test:443");
    }

    @Test
    void platzhalterPfadeUndKaputtesWerdenNichtErlaubt() {
        String[] ungueltig = {
                "*", "https://*", "https://*.ts.net", "null", "intranet.example.test",
                "https://intranet.example.test/pfad", "https://intranet.example.test?x=1",
                "https://intranet.example.test#x", "https://max@intranet.example.test",
                "ftp://intranet.example.test", "javascript:alert(1)", "file:///etc/passwd",
                "<script>alert(1)</script>", "'; DROP TABLE x; --", "https://"};
        for (String eintrag : ungueltig) {
            assertThat(ApiCorsConfigurationSource.normalisieren(eintrag)).as(eintrag).isNull();
        }
    }

    @Test
    void listeUebergehtLeeresUngueltigesUndDoppeltes() {
        assertThat(ApiCorsConfigurationSource.erlaubteOrigins(null)).isEmpty();
        assertThat(ApiCorsConfigurationSource.erlaubteOrigins("  ")).isEmpty();
        assertThat(ApiCorsConfigurationSource.erlaubteOrigins(
                " " + INTRANET + " , ,https://INTRANET.example.test/, *, https://lager.example.test:8443"))
                .containsExactly(INTRANET, "https://lager.example.test:8443");
    }

    @Test
    void eingetrageneWebseiteBekommtLeserechte() {
        ApiCorsConfigurationSource quelle = new ApiCorsConfigurationSource(INTRANET);

        CorsConfiguration config = quelle.getCorsConfiguration(anfrage("GET", "/api/kunden", INTRANET));

        assertThat(config).isNotNull();
        assertThat(config.checkOrigin(INTRANET)).isEqualTo(INTRANET);
        assertThat(config.getAllowCredentials()).isTrue();
    }

    @Test
    void fremdeWebseiteUndEigeneOberflaecheBekommenKeineCorsRegel() {
        ApiCorsConfigurationSource quelle = new ApiCorsConfigurationSource(INTRANET);

        assertThat(quelle.getCorsConfiguration(anfrage("GET", "/api/kunden", "https://angreifer.example"))).isNull();
        // Eigene Oberfläche hinter tailscale serve: Origin https, Anfrage kommt als http an.
        assertThat(quelle.getCorsConfiguration(anfrage("POST", "/api/kunden", "https://erp.example.test"))).isNull();
        assertThat(quelle.getCorsConfiguration(anfrage("GET", "/api/kunden", null))).isNull();
    }

    @Test
    void eigeneAdresseInDerListeSperrtSpeichernNicht() {
        ApiCorsConfigurationSource quelle = new ApiCorsConfigurationSource("https://erp.example.test");

        // Hinter tailscale serve hält Spring die eigene Oberfläche für fremd; schreiben bleibt ohne CORS-Regel.
        assertThat(quelle.getCorsConfiguration(anfrage("POST", "/api/kunden", "https://erp.example.test"))).isNull();
        assertThat(quelle.getCorsConfiguration(anfrage("DELETE", "/api/kunden/1", "https://erp.example.test"))).isNull();
        assertThat(quelle.getCorsConfiguration(anfrage("GET", "/api/kunden", "https://erp.example.test"))).isNotNull();
    }

    @Test
    void nurApiPfadeHabenCors() {
        ApiCorsConfigurationSource quelle = new ApiCorsConfigurationSource(INTRANET);

        assertThat(quelle.getCorsConfiguration(anfrage("GET", "/zeiterfassung/index.html", INTRANET))).isNull();
        assertThat(quelle.getCorsConfiguration(anfrage("GET", "/apix/kunden", INTRANET))).isNull();
    }

    @Test
    void preflightEinerFremdenWebseiteWirdAbgelehnt() throws Exception {
        ApiCorsConfigurationSource quelle = new ApiCorsConfigurationSource(INTRANET);

        assertThat(verarbeitePreflight(quelle, "https://angreifer.example", "GET").getStatus()).isEqualTo(403);
        assertThat(verarbeitePreflight(quelle, INTRANET, "GET").getStatus()).isEqualTo(200);
    }

    @Test
    void eingetrageneWebseiteDarfNurLesen() throws Exception {
        ApiCorsConfigurationSource quelle = new ApiCorsConfigurationSource(INTRANET);

        for (String methode : new String[]{"POST", "PUT", "PATCH", "DELETE"}) {
            assertThat(verarbeitePreflight(quelle, INTRANET, methode).getStatus()).as(methode).isEqualTo(403);
        }
    }

    @Test
    void ohneEintragIstKeineFremdeWebseiteErlaubt() throws Exception {
        ApiCorsConfigurationSource quelle = new ApiCorsConfigurationSource("");

        MockHttpServletResponse antwort = verarbeitePreflight(quelle, INTRANET, "GET");

        assertThat(antwort.getStatus()).isEqualTo(403);
        assertThat(antwort.getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(quelle.getCorsConfiguration(anfrage("GET", "/api/kunden", INTRANET))).isNull();
    }

    private static MockHttpServletResponse verarbeitePreflight(ApiCorsConfigurationSource quelle, String origin,
            String methode) throws Exception {
        MockHttpServletRequest preflight = anfrage("OPTIONS", "/api/kunden", origin);
        preflight.addHeader("Access-Control-Request-Method", methode);
        MockHttpServletResponse antwort = new MockHttpServletResponse();
        new DefaultCorsProcessor().processRequest(quelle.getCorsConfiguration(preflight), preflight, antwort);
        return antwort;
    }

    private static MockHttpServletRequest anfrage(String methode, String pfad, String origin) {
        MockHttpServletRequest anfrage = new MockHttpServletRequest(methode, pfad);
        anfrage.setScheme("http");
        anfrage.setServerName("erp.example.test");
        anfrage.setServerPort(80);
        if (origin != null) {
            anfrage.addHeader("Origin", origin);
        }
        return anfrage;
    }
}
