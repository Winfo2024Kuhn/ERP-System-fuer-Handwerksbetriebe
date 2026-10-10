package org.example.kalkulationsprogramm.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.kalkulationsprogramm.controller.FreigabeInternalController;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.DokumentFreigabeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Fail-closed: Ohne konfigurierten Schlüssel ist /api/internal/** komplett gesperrt. */
@WebMvcTest(controllers = FreigabeInternalController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ZeiterfassungSecurityFilter.class))
@Import({SecurityConfig.class, InternalApiSecurityOhneSchluesselTest.FilterBeans.class})
@TestPropertySource(properties = "website.internal-api.key=")
class InternalApiSecurityOhneSchluesselTest {

    @TestConfiguration
    static class FilterBeans {
        @Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
    }

    @Autowired MockMvc mvc;
    @MockBean FrontendUserDetailsService users;
    @MockBean MitarbeiterRepository mitarbeiter;
    @MockBean DokumentFreigabeService freigabeService;
    @MockBean DateiSpeicherService dateiSpeicherService;

    @Test void leererSchluesselLaesstKeinenZu() throws Exception {
        mvc.perform(get("/api/internal/freigabe/abc")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/internal/freigabe/abc").header(WebsiteInternalApiKeyFilter.HEADER, ""))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/internal/freigabe/abc").header(WebsiteInternalApiKeyFilter.HEADER, " "))
                .andExpect(status().isUnauthorized());
    }
}
