package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.dto.zeitbericht.SteuerberaterBerichtDto;
import org.example.kalkulationsprogramm.service.ZeiterfassungSichtbarkeitService;
import org.example.kalkulationsprogramm.service.zeitbericht.MitarbeiterBerichtPdfService;
import org.example.kalkulationsprogramm.service.zeitbericht.MitarbeiterBerichtService;
import org.example.kalkulationsprogramm.service.zeitbericht.SteuerberaterBerichtPdfService;
import org.example.kalkulationsprogramm.service.zeitbericht.SteuerberaterBerichtService;
import org.example.kalkulationsprogramm.testsupport.SitzungsRechteImTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ZeitberichtController.class)
@Import({SecurityConfig.class, ZeitberichtControllerSecurityTest.Filters.class})
class ZeitberichtControllerSecurityTest {

    @TestConfiguration
    static class Filters {
        @Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    private static final String BASIS = "/api/zeitverwaltung/berichte";

    @Autowired MockMvc mvc;
    @MockitoBean FrontendUserDetailsService users;
    @MockitoBean ZeiterfassungSichtbarkeitService sichtbarkeit;
    @MockitoBean SteuerberaterBerichtService steuerberaterService;
    @MockitoBean SteuerberaterBerichtPdfService steuerberaterPdf;
    @MockitoBean MitarbeiterBerichtService mitarbeiterService;
    @MockitoBean MitarbeiterBerichtPdfService mitarbeiterPdf;

    private final FrontendUserPrincipal principal =
            new FrontendUserPrincipal(70L, "test@example.com", "Max Mustermann", "", true, Set.of());

    @BeforeEach
    void setUp() {
        SitzungsRechteImTest.rechteUnveraendert(users);
        when(sichtbarkeit.darfZeiterfassungSehen(any())).thenReturn(true);
    }

    @Test
    void ohneAnmeldungIst401() throws Exception {
        mvc.perform(get(BASIS + "/steuerberater").param("jahr", "2026").param("monat", "10"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(BASIS + "/mitarbeiter/pdf").param("mitarbeiterIds", "1")
                .param("von", "2026-10-01").param("bis", "2026-10-31")).andExpect(status().isUnauthorized());
        verifyNoInteractions(steuerberaterService, mitarbeiterService);
    }

    @Test
    void ohneRechtZeiterfassungIst403() throws Exception {
        when(sichtbarkeit.darfZeiterfassungSehen(any())).thenReturn(false);
        mvc.perform(get(BASIS + "/steuerberater/pdf").with(user(principal)).param("jahr", "2026").param("monat", "10"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(steuerberaterService, steuerberaterPdf);
    }

    @Test
    void steuerberaterVorschauAlsJson() throws Exception {
        var zeile = new SteuerberaterBerichtDto.Zeile(1L, "Max Mustermann", d("8.0"), d("148.0"),
                d("16.0"), d("8.0"), d("4.0"), d("0.0"), d("-3.5"), d("7.5"), true);
        // Ohne Parameter geht null an den Service – er nimmt dann die Admin-Einstellung (hier: Ist).
        when(steuerberaterService.erstelle(2026, 10, List.of(1L, 2L), null))
                .thenReturn(new SteuerberaterBerichtDto(2026, 10, SteuerberaterBerichtDto.Stundenart.IST, List.of(zeile), true));

        mvc.perform(get(BASIS + "/steuerberater").with(user(principal))
                        .param("jahr", "2026").param("monat", "10").param("mitarbeiterIds", "1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zeilen[0].name").value("Max Mustermann"))
                .andExpect(jsonPath("$.zeilen[0].arbeitsstunden").value(148.0))
                .andExpect(jsonPath("$.zeilen[0].stundenkontoStand").value(-3.5))
                .andExpect(jsonPath("$.zeilen[0].resturlaubTage").value(7.5))
                .andExpect(jsonPath("$.zeilen[0].tagessoll").value(8.0))
                .andExpect(jsonPath("$.zeilen[0].fortbildungStunden").value(0.0))
                // Keine Gesamtstunden für den Steuerberater: weder Summenzeile noch Sollstunden je Mitarbeiter
                .andExpect(jsonPath("$.summe").doesNotExist())
                .andExpect(jsonPath("$.zeilen[0].sollStunden").doesNotExist())
                .andExpect(jsonPath("$.stundenart").value("IST"))
                .andExpect(jsonPath("$.alleFestgeschrieben").value(true));
    }

    @Test
    void steuerberaterPdfMitDateinameUndOhneZwischenspeicher() throws Exception {
        var bericht = new SteuerberaterBerichtDto(2026, 3, SteuerberaterBerichtDto.Stundenart.IST, List.of(), true);
        when(steuerberaterService.erstelle(eq(2026), eq(3), any(), eq(SteuerberaterBerichtDto.Stundenart.IST))).thenReturn(bericht);
        when(steuerberaterPdf.erzeuge(bericht, false, true)).thenReturn("%PDF".getBytes());

        mvc.perform(get(BASIS + "/steuerberater/pdf").with(user(principal))
                        .param("jahr", "2026").param("monat", "3").param("mitResturlaub", "false")
                        .param("stundenart", "IST"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"Stundenaufstellung_2026-03.pdf\""))
                .andExpect(content().bytes("%PDF".getBytes()));
    }

    @Test
    void mitarbeiterPdfReichtFilterDurch() throws Exception {
        LocalDate von = LocalDate.of(2026, 10, 1);
        LocalDate bis = LocalDate.of(2026, 10, 31);
        when(mitarbeiterService.erstelle(List.of(1L, 2L), von, bis)).thenReturn(List.of());
        when(mitarbeiterPdf.erzeuge(List.of(), true, false)).thenReturn("%PDF".getBytes());

        mvc.perform(get(BASIS + "/mitarbeiter/pdf").with(user(principal)).param("mitarbeiterIds", "1,2")
                        .param("von", "2026-10-01").param("bis", "2026-10-31").param("mitUnterschrift", "false"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"Zeitbericht_2026-10-01_bis_2026-10-31.pdf\""));
        verify(mitarbeiterPdf).erzeuge(List.of(), true, false);
    }

    @Test
    void falscheEingabenSind400() throws Exception {
        when(steuerberaterService.erstelle(anyInt(), anyInt(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte einen gültigen Monat wählen."));
        mvc.perform(get(BASIS + "/steuerberater").with(user(principal)).param("jahr", "2026").param("monat", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bitte einen gültigen Monat wählen."));

        for (String falsch : List.of("'; DROP TABLE x; --", "<script>alert(1)</script>", "abc", "1".repeat(10001))) {
            mvc.perform(get(BASIS + "/steuerberater").with(user(principal)).param("jahr", falsch).param("monat", "1"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get(BASIS + "/steuerberater/pdf").with(user(principal)).param("jahr", "2026").param("monat", "1")
                    .param("stundenart", falsch)).andExpect(status().isBadRequest());
            mvc.perform(get(BASIS + "/mitarbeiter/pdf").with(user(principal)).param("mitarbeiterIds", falsch)
                    .param("von", "2026-10-01").param("bis", "2026-10-31")).andExpect(status().isBadRequest());
            mvc.perform(get(BASIS + "/mitarbeiter/pdf").with(user(principal)).param("mitarbeiterIds", "1")
                    .param("von", falsch).param("bis", "2026-10-31")).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(mitarbeiterService);
        verify(steuerberaterService, org.mockito.Mockito.times(1)).erstelle(anyInt(), anyInt(), any(), any());
        verifyNoInteractions(mitarbeiterPdf);
        org.mockito.Mockito.verify(steuerberaterPdf, org.mockito.Mockito.never()).erzeuge(any(), anyBoolean(), anyBoolean());
    }

    private static BigDecimal d(String wert) {
        return new BigDecimal(wert);
    }
}
