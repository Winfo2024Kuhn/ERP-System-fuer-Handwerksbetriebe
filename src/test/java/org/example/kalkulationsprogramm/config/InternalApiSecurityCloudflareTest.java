package org.example.kalkulationsprogramm.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import java.util.Optional;
import org.example.kalkulationsprogramm.controller.FreigabeInternalController;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.DokumentFreigabeService;
import org.example.kalkulationsprogramm.service.FreigabeCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Mit aktivem Cloudflare Access zählt nur der JWT; der Schlüssel allein genügt nicht. */
@WebMvcTest(controllers = FreigabeInternalController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ZeiterfassungSecurityFilter.class))
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"website.internal-api.key=test-schluessel-nur-fuer-tests",
        "cloudflare.access.enabled=true"})
class InternalApiSecurityCloudflareTest {

    private static final String KEY = "test-schluessel-nur-fuer-tests";

    @Autowired MockMvc mvc;
    @Autowired CloudflareAccessJwtFilter cloudflareFilter;
    @MockitoBean FrontendUserDetailsService users;
    @MockitoBean MitarbeiterRepository mitarbeiter;
    @MockitoBean DokumentFreigabeService freigabeService;
    @MockitoBean FreigabeCodeService freigabeCodeService;
    @MockitoBean DateiSpeicherService dateiSpeicherService;

    @BeforeEach void jwtPruefungErsetzen() throws Exception {
        @SuppressWarnings("unchecked")
        ConfigurableJWTProcessor<SecurityContext> processor = mock(ConfigurableJWTProcessor.class);
        when(processor.process(anyString(), isNull())).thenAnswer(inv -> {
            if (!"gueltiger.test.jwt".equals(inv.getArgument(0))) {
                throw new IllegalArgumentException("ungültig");
            }
            return null;
        });
        cloudflareFilter.overrideProcessorForTesting(processor);
    }

    @Test void gueltigerJwtOhneSchluesselPassiert() throws Exception {
        when(freigabeService.findByUuidUndAktualisiereStatus("x")).thenReturn(Optional.empty());
        mvc.perform(get("/api/internal/freigabe/x").header("Cf-Access-Jwt-Assertion", "gueltiger.test.jwt"))
                .andExpect(status().isNotFound());
    }

    @Test void schluesselOhneJwtWirdAbgewiesen() throws Exception {
        mvc.perform(get("/api/internal/freigabe/x").header(WebsiteInternalApiKeyFilter.HEADER, KEY))
                .andExpect(status().isUnauthorized());
    }

    @Test void ungueltigerJwtWirdAbgewiesenAuchMitSchluessel() throws Exception {
        mvc.perform(get("/api/internal/freigabe/x").header("Cf-Access-Jwt-Assertion", "falsch")
                .header(WebsiteInternalApiKeyFilter.HEADER, KEY)).andExpect(status().isUnauthorized());
    }
}
