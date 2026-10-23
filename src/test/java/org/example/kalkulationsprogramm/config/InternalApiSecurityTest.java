package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import org.example.kalkulationsprogramm.controller.AnalyticsSnapshotIngressController;
import org.example.kalkulationsprogramm.controller.AnfrageFunnelController;
import org.example.kalkulationsprogramm.controller.FreigabeInternalController;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.AnfrageFunnelService;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.DokumentFreigabeService;
import org.example.kalkulationsprogramm.service.FreigabeCodeService;
import org.example.kalkulationsprogramm.service.WebsiteAnalyticsSnapshotService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

/** /api/internal/** verlangt den gemeinsamen Website-Schlüssel (Dummy-Wert, kein echtes Geheimnis). */
@WebMvcTest(controllers = {AnfrageFunnelController.class, FreigabeInternalController.class,
        AnalyticsSnapshotIngressController.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ZeiterfassungSecurityFilter.class))
@Import({SecurityConfig.class, InternalApiSecurityTest.FilterBeans.class})
@TestPropertySource(properties = "website.internal-api.key=test-schluessel-nur-fuer-tests")
class InternalApiSecurityTest {

    private static final String KEY = "test-schluessel-nur-fuer-tests";
    private static final String UUID = "11111111-1111-4111-8111-111111111111";

    @TestConfiguration
    static class FilterBeans {
        @Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
    }

    @Autowired MockMvc mvc;
    @MockitoBean FrontendUserDetailsService users;
    @MockitoBean MitarbeiterRepository mitarbeiter;
    @MockitoBean AnfrageFunnelService anfrageFunnelService;
    @MockitoBean DokumentFreigabeService freigabeService;
    @MockitoBean FreigabeCodeService freigabeCodeService;
    @MockitoBean DateiSpeicherService dateiSpeicherService;
    @MockitoBean WebsiteAnalyticsSnapshotService snapshotService;

    private static final ResultMatcher NICHT_401 = result ->
            assertThat(result.getResponse().getStatus()).isNotEqualTo(401);

    @Test void ohneSchluesselAlle401() throws Exception {
        mvc.perform(get("/api/internal/freigabe/" + UUID)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(get("/api/internal/freigabe/" + UUID + "/pdf")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/internal/freigabe/" + UUID + "/pdf")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/internal/freigabe/" + UUID + "/akzeptieren")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/internal/freigabe/" + UUID + "/code")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/internal/anfrage").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/internal/analytics-snapshot").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        Mockito.verifyNoInteractions(anfrageFunnelService, freigabeService, freigabeCodeService, snapshotService);
    }

    @Test void falscherOderLeererSchluesselWirdAbgewiesen() throws Exception {
        for (String falsch : new String[]{"falsch", KEY + "x", KEY.substring(1), " ", "'; DROP TABLE x; --"}) {
            mvc.perform(get("/api/internal/freigabe/" + UUID).header(WebsiteInternalApiKeyFilter.HEADER, falsch))
                    .andExpect(status().isUnauthorized());
        }
        Mockito.verifyNoInteractions(freigabeService);
    }

    @Test void fremderHeaderErsetztDenSchluesselNicht() throws Exception {
        mvc.perform(get("/api/internal/freigabe/" + UUID)
                .header("Cf-Access-Jwt-Assertion", "irgendein.jwt.token")
                .header("Authorization", "Bearer " + KEY)
                .header("X-Auth-Token", KEY)).andExpect(status().isUnauthorized());
    }

    @Test void mitSchluesselGehenAlleDreiControllerDurch() throws Exception {
        Mockito.when(freigabeService.findByUuidUndAktualisiereStatus(UUID)).thenReturn(Optional.empty());

        mvc.perform(get("/api/internal/freigabe/" + UUID).header(WebsiteInternalApiKeyFilter.HEADER, KEY))
                .andExpect(status().isNotFound());
        Mockito.when(freigabeCodeService.fordereCodeAn(UUID))
                .thenThrow(new IllegalArgumentException(DokumentFreigabeService.UNBEKANNTE_UUID_MESSAGE));
        mvc.perform(post("/api/internal/freigabe/" + UUID + "/code").header(WebsiteInternalApiKeyFilter.HEADER, KEY))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/internal/anfrage").header(WebsiteInternalApiKeyFilter.HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(NICHT_401);
        mvc.perform(post("/api/internal/analytics-snapshot").header(WebsiteInternalApiKeyFilter.HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(NICHT_401);
    }

    @Test void schluesselMitUmgebendenLeerzeichenWirdToleriert() throws Exception {
        mvc.perform(get("/api/internal/freigabe/" + UUID).header(WebsiteInternalApiKeyFilter.HEADER, " " + KEY + " "))
                .andExpect(status().isNotFound());
    }

    @Test void pfadvariantenErreichenNieEinenController() throws Exception {
        String[] pfade = {"/api/internal//freigabe/x", "/api/internal;x=1/freigabe/x",
                "/api/internal/freigabe/x;jsessionid=1", "/API/INTERNAL/freigabe/x",
                "/api/internal/%2e%2e/freigabe/x", "/api/internal/freigabe/%2e%2e/x"};
        for (String pfad : pfade) {
            int status;
            try {
                status = mvc.perform(get(java.net.URI.create(pfad))).andReturn().getResponse().getStatus();
            } catch (org.springframework.security.web.firewall.RequestRejectedException abgewiesen) {
                status = 400;
            }
            assertThat(status).as(pfad).isGreaterThanOrEqualTo(400).isNotEqualTo(404);
        }
        Mockito.verifyNoInteractions(freigabeService, anfrageFunnelService, snapshotService);
    }
}
