package org.example.kalkulationsprogramm.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.controller.AbwesenheitController;
import org.example.kalkulationsprogramm.controller.UrlaubUebertragController;
import org.example.kalkulationsprogramm.controller.UrlaubsantragController;
import org.example.kalkulationsprogramm.controller.ZeiterfassungSichtbarkeitController;
import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.MitarbeiterArt;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.AbwesenheitService;
import org.example.kalkulationsprogramm.service.GesundheitsdatenSichtService;
import org.example.kalkulationsprogramm.service.UrlaubUebertragService;
import org.example.kalkulationsprogramm.service.UrlaubsantragService;
import org.example.kalkulationsprogramm.service.ZeiterfassungSichtbarkeitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Echte Security-Ketten + echter Interceptor: Ohne das Recht „Zeiterfassung im
 * Menü sehen“ gibt das Backend die Zeiterfassungs-Daten am PC nicht heraus
 * (Issue #268); Team-Kalender, Sichtbarkeits-Abfrage und Handy bleiben frei.
 * Dummy-Daten: Max Mustermann.
 */
@WebMvcTest(controllers = { UrlaubsantragController.class, AbwesenheitController.class,
        ZeiterfassungSichtbarkeitController.class, UrlaubUebertragController.class },
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ZeiterfassungSecurityFilter.class))
@Import({ SecurityConfig.class, ZeiterfassungSichtbarkeitService.class, ZeiterfassungZugriffInterceptor.class,
        ZeiterfassungZugriffSecurityTest.FilterBeans.class })
class ZeiterfassungZugriffSecurityTest {
    private static final String TOKEN = "12345678-1234-4234-8234-123456789abc";

    @Autowired private MockMvc mvc;
    @MockitoBean private FrontendUserDetailsService users;
    @MockitoBean private MitarbeiterRepository mitarbeiter;
    @MockitoBean private FrontendUserProfileRepository profiles;
    @MockitoBean private UrlaubsantragService urlaubsantragService;
    @MockitoBean private AbwesenheitService abwesenheitService;
    @MockitoBean private GesundheitsdatenSichtService gesundheitsdatenSichtService;
    @MockitoBean private UrlaubUebertragService urlaubUebertragService;

    @TestConfiguration
    static class FilterBeans {
        @Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
    }

    @BeforeEach
    void grunddaten() {
        org.example.kalkulationsprogramm.testsupport.SitzungsRechteImTest.rechteUnveraendert(users);
        Mitarbeiter handy = new Mitarbeiter();
        handy.setId(7L);
        handy.setAktiv(true);
        when(mitarbeiter.findByLoginTokenAndAktivTrue(TOKEN)).thenReturn(Optional.of(handy));
        when(urlaubsantragService.getOffeneAntraege()).thenReturn(List.of());
        when(urlaubsantragService.getAntraegeByMitarbeiter(any())).thenReturn(List.of());
        when(abwesenheitService.getAllAbwesenheitenForZeitraum(any(), any())).thenReturn(List.of());
        when(gesundheitsdatenSichtService.sichtFuer(any())).thenReturn(new GesundheitsdatenSichtService.Sicht(false, 9L));
    }

    private FrontendUserPrincipal pcBenutzer(boolean abteilungsRecht) {
        Abteilung werkstatt = new Abteilung();
        werkstatt.setDarfZeiterfassungSehen(abteilungsRecht);
        Mitarbeiter max = new Mitarbeiter();
        max.setId(9L);
        max.setVorname("Max");
        max.setNachname("Mustermann");
        max.setAktiv(true);
        max.setArt(MitarbeiterArt.MENSCH);
        max.setAbteilungen(Set.of(werkstatt));
        FrontendUserProfile profil = new FrontendUserProfile();
        profil.setId(70L);
        profil.setActive(true);
        profil.setMitarbeiter(max);
        when(profiles.findById(70L)).thenReturn(Optional.of(profil));
        return new FrontendUserPrincipal(70L, "max@example.com", "Max Mustermann", "", true, Set.of(FrontendUserRole.USER));
    }

    @Test
    void ohneRecht_zeiterfassungsDatenGesperrt_teamKalenderUndSichtbarkeitFrei() throws Exception {
        FrontendUserPrincipal max = pcBenutzer(false);

        mvc.perform(get("/api/urlaub/antraege").with(user(max)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ZeiterfassungZugriffInterceptor.MELDUNG));
        mvc.perform(get("/api/abwesenheit/mitarbeiter/7").with(user(max)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/zeitkonto/korrekturen/mitarbeiter/7").with(user(max)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/abwesenheit/team").param("von", "2026-10-01").param("bis", "2026-10-31").with(user(max)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/zeitverwaltung/sichtbarkeit").with(user(max)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.darfZeiterfassungSehen").value(false));

        verify(urlaubsantragService, never()).getOffeneAntraege();
    }

    @Test
    void resturlaubUebertrag_ohneRechtUndVomHandyGesperrt() throws Exception {
        FrontendUserPrincipal max = pcBenutzer(false);

        mvc.perform(get("/api/urlaub/uebertrag").param("mitarbeiterId", "7").param("jahr", "2027").with(user(max)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ZeiterfassungZugriffInterceptor.MELDUNG));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/urlaub/uebertrag")
                        .with(user(max))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json").content("{\"mitarbeiterId\":7,\"jahr\":2027,\"tage\":3}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/urlaub/uebertrag").param("mitarbeiterId", "7").param("jahr", "2027")
                        .header("X-Auth-Token", TOKEN))
                .andExpect(status().isForbidden());

        org.mockito.Mockito.verifyNoInteractions(urlaubUebertragService);
    }

    @Test
    void resturlaubUebertrag_mitRechtErreichbar() throws Exception {
        FrontendUserPrincipal max = pcBenutzer(true);

        mvc.perform(get("/api/urlaub/uebertrag").param("mitarbeiterId", "7").param("jahr", "2027").with(user(max)))
                .andExpect(status().isOk());
        verify(urlaubUebertragService).laden(7L, 2027);
    }

    @Test
    void mitRecht_zeiterfassungsDatenErreichbar() throws Exception {
        FrontendUserPrincipal max = pcBenutzer(true);

        mvc.perform(get("/api/urlaub/antraege").with(user(max))).andExpect(status().isOk());
        mvc.perform(get("/api/zeitverwaltung/sichtbarkeit").with(user(max)))
                .andExpect(jsonPath("$.darfZeiterfassungSehen").value(true));
        verify(urlaubsantragService).getOffeneAntraege();
    }

    @Test
    void adminOhneVerknuepftenMitarbeiter_erreichbar() throws Exception {
        FrontendUserPrincipal admin = new FrontendUserPrincipal(71L, "admin@example.com", "Erika Musterfrau", "", true,
                Set.of(FrontendUserRole.ADMIN));

        mvc.perform(get("/api/urlaub/antraege").with(user(admin))).andExpect(status().isOk());
    }

    @Test
    void handyToken_bleibtVonDerSperreUnberuehrt() throws Exception {
        mvc.perform(get("/api/urlaub/antraege").param("mitarbeiterId", "7").header("X-Auth-Token", TOKEN))
                .andExpect(status().isOk());
        verify(urlaubsantragService).getAntraegeByMitarbeiter(7L);
    }
}
