package org.example.kalkulationsprogramm.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.example.kalkulationsprogramm.controller.UrlaubsantragController;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Urlaubsantrag;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.UrlaubsantragService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Echter Controller mit den produktiven Security-Ketten; nur Fachservice und Datenbank sind ersetzt. */
@WebMvcTest(controllers = UrlaubsantragController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ZeiterfassungSecurityFilter.class))
@Import({SecurityConfig.class, MobileUrlaubsantragSecurityTest.FilterBeans.class})
class MobileUrlaubsantragSecurityTest {
    private static final String TOKEN = "12345678-1234-4234-8234-123456789abc";
    @Autowired private MockMvc mvc;
    @MockBean private FrontendUserDetailsService users;
    @MockBean private MitarbeiterRepository mitarbeiter;
    @MockBean private UrlaubsantragService service;

    @TestConfiguration
    static class FilterBeans {
        @Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
    }

    @BeforeEach
    void aktiverMitarbeiter() {
        Mitarbeiter employee = new Mitarbeiter();
        employee.setId(7L);
        employee.setAktiv(true);
        when(mitarbeiter.findByLoginTokenAndAktivTrue(TOKEN)).thenReturn(Optional.of(employee));
    }

    private Urlaubsantrag antrag(long employeeId) {
        Mitarbeiter employee = new Mitarbeiter();
        employee.setId(employeeId);
        employee.setVorname("Max");
        employee.setNachname("Mustermann");
        employee.setLoginToken("vertraulicher-test-token");
        Urlaubsantrag antrag = new Urlaubsantrag();
        antrag.setId(employeeId * 10);
        antrag.setMitarbeiter(employee);
        antrag.setVonDatum(LocalDate.of(2026, 10, 12));
        antrag.setBisDatum(LocalDate.of(2026, 10, 13));
        return antrag;
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?mitarbeiterId=7", "?jahr=2026", "?status=OFFEN", "?status=UNBEKANNT"})
    void lesendeAnfragenOhneIdKoennenKeineFremdenAntraegeAuflisten(String query) throws Exception {
        // Ein versehentlicher Wechsel auf die Büro-Abfrage würde beide Mitarbeiter offenlegen.
        when(service.getOffeneAntraege()).thenReturn(List.of(antrag(7), antrag(8)));
        when(service.getAntraegeByStatus(Urlaubsantrag.Status.OFFEN)).thenReturn(List.of(antrag(7), antrag(8)));
        when(service.getAntraegeByMitarbeiter(7L)).thenReturn(List.of(antrag(7)));
        when(service.getAntraegeByMitarbeiterAndYear(7L, 2026)).thenReturn(List.of(antrag(7)));
        when(service.getAntraegeByMitarbeiterAndStatus(7L, Urlaubsantrag.Status.OFFEN)).thenReturn(List.of(antrag(7)));

        mvc.perform(get("/api/urlaub/antraege" + query).header("X-Auth-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].mitarbeiter.id").value(7))
                .andExpect(jsonPath("$[0].mitarbeiter.loginToken").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"mitarbeiterId\":7,", ""})
    void antragWirdImmerFuerDenTokenInhaberErstellt(String employeeField) throws Exception {
        when(service.createAntrag(eq(7L), eq(LocalDate.of(2026, 10, 12)), eq(LocalDate.of(2026, 10, 13)),
                eq("Testurlaub"), eq(Urlaubsantrag.Typ.URLAUB))).thenReturn(antrag(7));

        mvc.perform(post("/api/urlaub/antraege").header("X-Auth-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + employeeField + "\"von\":\"2026-10-12\",\"bis\":\"2026-10-13\",\"bemerkung\":\"Testurlaub\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mitarbeiter.id").value(7))
                .andExpect(jsonPath("$.mitarbeiter.loginToken").doesNotExist());
    }

    @Test
    void fremdeMitarbeiterDuerfenWederGelesenNochBeantragtWerden() throws Exception {
        mvc.perform(get("/api/urlaub/antraege?mitarbeiterId=8").header("X-Auth-Token", TOKEN))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/urlaub/resturlaub?mitarbeiterId=8").header("X-Auth-Token", TOKEN))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/urlaub/antraege").header("X-Auth-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mitarbeiterId\":8,\"von\":\"2026-10-12\",\"bis\":\"2026-10-13\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void eigenerResturlaubIstErreichbar() throws Exception {
        when(service.getResturlaub(7L, 2026)).thenReturn(12);
        mvc.perform(get("/api/urlaub/resturlaub?mitarbeiterId=7&jahr=2026").header("X-Auth-Token", TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verbleibend").value(12));
    }

    @Test
    void bueroHinweiseMitGesundheitsdatenBleibenAnonymUndMobilGesperrt() throws Exception {
        String path = "/api/langzeitkrankmeldungen/urlaubs-hinweise?mitarbeiterId=7&von=2026-10-12&bis=2026-10-13";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("X-Auth-Token", TOKEN)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/urlaub/antraege/hinweise").header("X-Auth-Token", TOKEN))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    void desktopAntragOhneMitarbeiterIstEinEingabefehler() throws Exception {
        mvc.perform(post("/api/urlaub/antraege").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"von\":\"2026-10-12\",\"bis\":\"2026-10-13\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void ungueltigeUndWiderrufeneTokensBleibenOhneZugriff() throws Exception {
        mvc.perform(get("/api/urlaub/antraege").header("X-Auth-Token", "ungueltig")
                        .with(r -> { r.setRemoteAddr("192.0.2.10"); return r; }))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/urlaub/typen").header("X-Auth-Token", TOKEN)).andExpect(status().isOk());
        // Derselbe Token ist nach Widerruf/Deaktivierung nicht mehr im aktiven Bestand.
        when(mitarbeiter.findByLoginTokenAndAktivTrue(TOKEN)).thenReturn(Optional.empty());
        mvc.perform(get("/api/urlaub/antraege").header("X-Auth-Token", TOKEN)
                        .with(r -> { r.setRemoteAddr("192.0.2.11"); return r; }))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/urlaub/antraege")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({
            "PUT, /api/urlaub/antraege/70/approve", "PUT, /api/urlaub/antraege/70/reject",
            "PUT, /api/urlaub/antraege/70/storno", "DELETE, /api/urlaub/antraege",
            "POST, /api/projekte/preise-nachtragen", "PUT, /api/projekte/42", "DELETE, /api/projekte/42",
            "POST, /api/kunden", "DELETE, /api/kunden/42", "DELETE, /api/anfragen/42",
            "DELETE, /api/lieferanten/42", "DELETE, /api/produktkategorien/42",
            "DELETE, /api/reklamationen/42", "PUT, /api/arbeitsgaenge/42"
    })
    void mobileTokensErreichenKeineVerwaltungsMutation(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path).header("X-Auth-Token", TOKEN))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    void desktopSessionDarfWeiterhinAlleAntraegeLesenUndMitCsrfSchreiben() throws Exception {
        when(service.getOffeneAntraege()).thenReturn(List.of(antrag(7), antrag(8)));
        when(service.createAntrag(eq(8L), any(), any(), any(), eq(Urlaubsantrag.Typ.URLAUB))).thenReturn(antrag(8));
        when(service.approveAntrag(80L)).thenReturn(antrag(8));
        mvc.perform(get("/api/urlaub/antraege"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(post("/api/urlaub/antraege").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mitarbeiterId\":8,\"von\":\"2026-10-12\",\"bis\":\"2026-10-13\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mitarbeiter.id").value(8));
        mvc.perform(put("/api/urlaub/antraege/80/approve")).andExpect(status().isForbidden());
        mvc.perform(put("/api/urlaub/antraege/80/approve").with(csrf())).andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void expliziterMobilerTokenErbtKeineRechteDerDesktopSession() throws Exception {
        mvc.perform(put("/api/urlaub/antraege/80/approve").with(csrf()).header("X-Auth-Token", TOKEN))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
