package org.example.kalkulationsprogramm.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.junit.jupiter.api.Test;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.Optional;
import java.util.Map;
import jakarta.servlet.http.Cookie;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MobileApiAuthenticationTest.ProbeController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ZeiterfassungSecurityFilter.class))
@Import({SecurityConfig.class, MobileApiAuthenticationTest.ProbeController.class, MobileApiAuthenticationTest.FilterBeans.class})
class MobileApiAuthenticationTest {
    @Autowired MockMvc mvc;
    @MockBean FrontendUserDetailsService users;
    @TestConfiguration static class FilterBeans {
        @Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
    }
    @MockBean MitarbeiterRepository mitarbeiter;

    @Test void anonymeProjektabfrageWirdAbgewiesen() throws Exception {
        mvc.perform(get("/api/zeiterfassung/projekte")).andExpect(status().isUnauthorized());
    }

    private static final String TOKEN = "12345678-1234-4234-8234-123456789abc";
    private void validToken() {
        Mitarbeiter employee = new Mitarbeiter(); employee.setId(7L); employee.setAktiv(true);
        when(mitarbeiter.findByLoginTokenAndAktivTrue(TOKEN)).thenReturn(Optional.of(employee));
    }
    @Test void gueltigerTokenErlaubtLesenUndBuchen() throws Exception {
        validToken();
        mvc.perform(get("/api/zeiterfassung/projekte").header("X-Auth-Token", TOKEN)).andExpect(status().isOk());
        mvc.perform(post("/api/zeiterfassung/start").header("X-Auth-Token", TOKEN).contentType("application/json")
            .content("{\"token\":\"" + TOKEN + "\"}")).andExpect(status().isOk()).andExpect(content().string(TOKEN));
    }
    @Test void widerrufenerTokenWirdBeimNaechstenAufrufAbgewiesen() throws Exception {
        validToken();
        mvc.perform(get("/api/zeiterfassung/projekte").header("X-Auth-Token", TOKEN)
            .with(r -> { r.setRemoteAddr("203.0.113.40"); return r; })).andExpect(status().isOk());
        when(mitarbeiter.findByLoginTokenAndAktivTrue(TOKEN)).thenReturn(Optional.empty());
        mvc.perform(get("/api/zeiterfassung/projekte").header("X-Auth-Token", TOKEN)
            .with(r -> { r.setRemoteAddr("203.0.113.40"); return r; })).andExpect(status().isUnauthorized());
        org.mockito.Mockito.verify(mitarbeiter, org.mockito.Mockito.times(2)).findByLoginTokenAndAktivTrue(TOKEN);
    }
    @Test void anonymeUndCookieSchreibzugriffeWerdenVerweigert() throws Exception {
        validToken();
        mvc.perform(post("/api/zeiterfassung/start").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/zeiterfassung/start").cookie(new Cookie("ze_token", TOKEN)).contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/images/test.jpg").cookie(new Cookie("ze_token", TOKEN))).andExpect(status().isOk());
    }
    @Test void urlKodiertesCookieLaedtBildUndUngueltigeKodierungWirdAbgewiesen() throws Exception {
        validToken();
        mvc.perform(get("/api/images/test.jpg").cookie(new Cookie("ze_token", TOKEN.replace("-", "%2D"))))
            .andExpect(status().isOk());
        mvc.perform(get("/api/images/test.jpg").cookie(new Cookie("ze_token", "%kaputt"))
            .with(r -> { r.setRemoteAddr("203.0.113.41"); return r; }))
            .andExpect(status().isUnauthorized());
    }
    @Test void mobilerTokenErlaubtKeineVerwaltungUndKeineFremdeIdentitaet() throws Exception {
        validToken();
        mvc.perform(post("/api/projekte/preise-nachtragen").header("X-Auth-Token", TOKEN)).andExpect(status().isForbidden());
        mvc.perform(get("/api/zeiterfassung/projekte").header("X-Auth-Token", TOKEN).header("X-Mitarbeiter-Id", "8"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/zeiterfassung/projekte").header("X-Auth-Token", TOKEN).header("X-User-Profile-Id", "1"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/urlaub/antraege").header("X-Auth-Token", TOKEN).param("mitarbeiterId", "8"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/urlaub/antraege").header("X-Auth-Token", TOKEN).contentType("application/json")
            .content("{\"mitarbeiterId\":8}")).andExpect(status().isForbidden());
    }
    @Test void manipulierterBodyTokenKannNichtAmLimiterVorbeiraten() throws Exception {
        validToken();
        mvc.perform(post("/api/zeiterfassung/start").header("X-Auth-Token", TOKEN).contentType("application/json")
            .content("{\"token\":\"anderer-token\"}")).andExpect(status().isForbidden());
    }
    @Test void bodyPruefungGiltAuchFuerAndereJsonSchreibweisen() throws Exception {
        validToken();
        for (String typ : new String[]{"APPLICATION/JSON", "application/vnd.erp+json", "application/json;charset=UTF-8"}) {
            mvc.perform(post("/api/zeiterfassung/start").header("X-Auth-Token", TOKEN).contentType(typ)
                .content("{\"mitarbeiterId\":8}")).andExpect(status().isForbidden());
        }
    }
    @Test void mehrfacheMitarbeiterIdImQuerystringWirdAbgewiesen() throws Exception {
        validToken();
        mvc.perform(get("/api/zeiterfassung/projekte").header("X-Auth-Token", TOKEN)
            .param("mitarbeiterId", "7").param("mitarbeiterId", "8")).andExpect(status().isForbidden());
    }
    @Test void jsonErkennung() {
        org.assertj.core.api.Assertions.assertThat(MobileTokenAuthenticationFilter.istJson("Application/Json")).isTrue();
        org.assertj.core.api.Assertions.assertThat(MobileTokenAuthenticationFilter.istJson("application/problem+json")).isTrue();
        org.assertj.core.api.Assertions.assertThat(MobileTokenAuthenticationFilter.istJson("multipart/form-data; boundary=x")).isFalse();
        org.assertj.core.api.Assertions.assertThat(MobileTokenAuthenticationFilter.istJson("text/plain")).isFalse();
        org.assertj.core.api.Assertions.assertThat(MobileTokenAuthenticationFilter.istJson(null)).isFalse();
        org.assertj.core.api.Assertions.assertThat(MobileTokenAuthenticationFilter.istJson("kaputt;;")).isTrue();
    }
    @Test void dritterFehlversuchSperrtAuchBeiWechselndemTokenUndGefaelschtenHeadern() throws Exception {
        for (int i = 1; i <= 3; i++) {
            mvc.perform(get("/api/mitarbeiter/by-token/falsch-" + i)
                .with(r -> { r.setRemoteAddr("203.0.113.25"); return r; })
                .header("X-Forwarded-For", "192.0.2." + i).header("CF-Connecting-IP", "192.0.2." + i))
                .andExpect(status().is(i == 3 ? 429 : 401));
        }
        mvc.perform(get("/api/zeiterfassung/projekte").header("X-Auth-Token", TOKEN)
                .with(r -> { r.setRemoteAddr("203.0.113.25"); return r; }))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }
    @Test @WithMockUser void publicIngressErbtKeineDesktopSession() throws Exception {
        mvc.perform(get("/api/zeiterfassung/projekte").header("X-ERP-Public-Mobile", "1"))
            .andExpect(status().isUnauthorized());
        validToken();
        mvc.perform(get("/api/zeiterfassung/projekte").header("X-ERP-Public-Mobile", "1").header("X-Auth-Token", TOKEN))
            .andExpect(status().isOk());
    }
    @Test @WithMockUser void bueroAbfrageMitAltemTokenSperrtNichtDasFirmennetz() throws Exception {
        // Büro fragt z. B. den Saldo eines ausgeschiedenen Mitarbeiters ab: 401, aber kein Fehlversuch.
        when(mitarbeiter.findByLoginTokenAndAktivTrue("87654321-4321-4321-8321-cba987654321")).thenReturn(Optional.empty());
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/zeiterfassung/saldo/87654321-4321-4321-8321-cba987654321")
                .with(r -> { r.setRemoteAddr("192.168.10.30"); return r; }))
                .andExpect(status().isUnauthorized());
        }
    }
    @Test @WithMockUser void desktopSessionBleibtNutzbar() throws Exception {
        mvc.perform(get("/api/zeiterfassung/projekte")).andExpect(status().isOk());
        mvc.perform(post("/api/projekte/preise-nachtragen")).andExpect(status().isForbidden());
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/zeiterfassung/projekte") String projekte() { return "geschuetzte Projekte"; }
        @GetMapping("/api/images/test.jpg") String image() { return "image"; }
        @PostMapping("/api/zeiterfassung/start") String start(@RequestBody Map<String,String> body) { return body.get("token"); }
        @PostMapping("/api/projekte/preise-nachtragen") String verwaltung() { return "verwaltung"; }
        @GetMapping("/api/urlaub/antraege") String urlaub(@RequestParam(required=false) Long mitarbeiterId) { return "urlaub"; }
    }
}
