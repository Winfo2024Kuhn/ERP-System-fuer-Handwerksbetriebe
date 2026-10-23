package org.example.kalkulationsprogramm.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.kalkulationsprogramm.controller.LeistungController;
import org.example.kalkulationsprogramm.controller.ProduktkategorieController;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.LeistungService;
import org.example.kalkulationsprogramm.service.ProduktkategorieAuswertungService;
import org.example.kalkulationsprogramm.service.ProduktkategorieService;
import org.example.kalkulationsprogramm.service.ProduktkategorieVerwaltungService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Die neuen Ordner-Endpunkte laufen durch die echten Security-Ketten: ohne Anmeldung 401,
 * schreibend ohne CSRF-Token 403, angemeldet mit Token erlaubt.
 */
@WebMvcTest(controllers = { ProduktkategorieController.class, LeistungController.class },
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ZeiterfassungSecurityFilter.class))
@Import({ SecurityConfig.class, ProduktkategorieSecurityTest.FilterBeans.class })
class ProduktkategorieSecurityTest {

    @TestConfiguration
    static class FilterBeans {
        @Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
    }

    @Autowired MockMvc mvc;
    @MockitoBean FrontendUserDetailsService users;
    @MockitoBean MitarbeiterRepository mitarbeiter;
    @MockitoBean ProduktkategorieService produktkategorieService;
    @MockitoBean ProduktkategorieVerwaltungService verwaltungService;
    @MockitoBean ProduktkategorieAuswertungService auswertungService;
    @MockitoBean LeistungService leistungService;

    @Test
    void ohneAnmeldungKeinZugriff() throws Exception {
        mvc.perform(get("/api/produktkategorien/auswertung")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/produktkategorien/2/loeschpruefung")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/leistungen/2/verwendung")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/produktkategorien/2/loeschen").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"modus\":\"ALLES\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void schreibenOhneCsrfTokenIstVerboten() throws Exception {
        mvc.perform(post("/api/produktkategorien/2/loeschen").with(user("max.mustermann").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"modus\":\"ALLES\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/produktkategorien/2/verschieben").with(user("max.mustermann").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"parentId\":null}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void angemeldetMitTokenErlaubt() throws Exception {
        mvc.perform(get("/api/produktkategorien/2/loeschpruefung").with(user("max.mustermann").roles("USER")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/produktkategorien/2/loeschen").with(user("max.mustermann").roles("USER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"modus\":\"ALLES\"}"))
                .andExpect(status().isNoContent());
    }
}
