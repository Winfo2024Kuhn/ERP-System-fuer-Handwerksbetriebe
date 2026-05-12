package org.example.kalkulationsprogramm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.BelegDto;
import org.example.kalkulationsprogramm.service.BelegService;
import org.example.kalkulationsprogramm.service.MwstRechnerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc-Tests fuer {@link BelegController} — Fokus liegt auf dem neuen
 * Steuerberater-Export-Endpoint aus Issue #58 (Happy-Path, Auth-Fehlerfall
 * und Validierung des Datumsbereichs). Auth-Mapping laeuft analog zu den
 * anderen Controller-Tests: {@code addFilters = false} + Auth-Token als
 * Request-Principal, damit Spring den {@link FrontendUserPrincipal}
 * aufloesen kann.
 */
@WebMvcTest(BelegController.class)
@AutoConfigureMockMvc(addFilters = false)
class BelegControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BelegService belegService;

    @MockBean
    private MwstRechnerService mwstRechnerService;

    private UsernamePasswordAuthenticationToken testAuth() {
        FrontendUserPrincipal principal = new FrontendUserPrincipal(
                42L, "max.mustermann", "Max Mustermann", "hash", true,
                Set.of(FrontendUserRole.ADMIN));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("GET /api/buchhaltung/steuerberater-export")
    class SteuerberaterExport {

        @Test
        @DisplayName("Happy-Path: Liefert Eintraege mit Firma-Anteilen bei TEILWEISE")
        void happyPath_lieferEintraege() throws Exception {
            Mitarbeiter caller = new Mitarbeiter();
            caller.setId(42L);
            given(belegService.findCaller(any(), any())).willReturn(caller);
            given(belegService.darfSehen(caller)).willReturn(true);

            BelegDto.SteuerberaterExportEntry vollstaendig = BelegDto.SteuerberaterExportEntry.builder()
                    .belegId(1L)
                    .belegDatum(LocalDate.of(2026, 4, 3))
                    .belegNummer("RE-2026-001")
                    .lieferantName("Max Mustermann GmbH")
                    .belegKategorie("KASSE_AUSGABE")
                    .betragNetto(new BigDecimal("100.00"))
                    .betragBrutto(new BigDecimal("119.00"))
                    .betragMwst(new BigDecimal("19.00"))
                    .aufteilungsModus("VOLLSTAENDIG")
                    .build();

            BelegDto.SteuerberaterExportEntry teilweise = BelegDto.SteuerberaterExportEntry.builder()
                    .belegId(2L)
                    .belegDatum(LocalDate.of(2026, 4, 10))
                    .belegNummer("BON-2026-007")
                    .lieferantName("Max Mustermann GmbH")
                    .belegKategorie("KASSE_AUSGABE")
                    // bei TEILWEISE: nur die Firma-Anteile
                    .betragNetto(new BigDecimal("25.21"))
                    .betragBrutto(new BigDecimal("30.00"))
                    .betragMwst(new BigDecimal("4.79"))
                    .aufteilungsModus("TEILWEISE")
                    .gesamtBruttoOriginal(new BigDecimal("178.50"))
                    .anzahlPositionenGesamt(10)
                    .anzahlPositionenFirma(1)
                    .positionenHinweis("Schrauben Edelstahl")
                    .build();

            given(belegService.listeFuerSteuerberaterExport(
                    eq(LocalDate.of(2026, 4, 1)),
                    eq(LocalDate.of(2026, 4, 30))))
                    .willReturn(List.of(vollstaendig, teilweise));

            mockMvc.perform(get("/api/buchhaltung/steuerberater-export")
                            .param("von", "2026-04-01")
                            .param("bis", "2026-04-30")
                            .principal(testAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].belegNummer").value("RE-2026-001"))
                    .andExpect(jsonPath("$[0].aufteilungsModus").value("VOLLSTAENDIG"))
                    .andExpect(jsonPath("$[0].betragBrutto").value(119.00))
                    .andExpect(jsonPath("$[1].belegNummer").value("BON-2026-007"))
                    .andExpect(jsonPath("$[1].aufteilungsModus").value("TEILWEISE"))
                    // Firma-Anteil, NICHT die Gesamt-Belegsumme
                    .andExpect(jsonPath("$[1].betragBrutto").value(30.00))
                    .andExpect(jsonPath("$[1].gesamtBruttoOriginal").value(178.50))
                    .andExpect(jsonPath("$[1].anzahlPositionenFirma").value(1))
                    .andExpect(jsonPath("$[1].anzahlPositionenGesamt").value(10))
                    .andExpect(jsonPath("$[1].positionenHinweis").value("Schrauben Edelstahl"));
        }

        @Test
        @DisplayName("Ohne Berechtigung darfSehen -> 403")
        void ohneSichtBerechtigung_liefert403() throws Exception {
            Mitarbeiter caller = new Mitarbeiter();
            caller.setId(42L);
            given(belegService.findCaller(any(), any())).willReturn(caller);
            given(belegService.darfSehen(caller)).willReturn(false);

            mockMvc.perform(get("/api/buchhaltung/steuerberater-export")
                            .param("von", "2026-04-01")
                            .param("bis", "2026-04-30")
                            .principal(testAuth()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Ohne Authentifizierung (kein Caller, kein Token) -> 403")
        void ohneAuth_liefert403() throws Exception {
            given(belegService.findCaller(any(), any())).willReturn(null);

            mockMvc.perform(get("/api/buchhaltung/steuerberater-export")
                            .param("von", "2026-04-01")
                            .param("bis", "2026-04-30"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("bis vor von -> 400 Bad Request")
        void bisVorVon_liefert400() throws Exception {
            Mitarbeiter caller = new Mitarbeiter();
            caller.setId(42L);
            given(belegService.findCaller(any(), any())).willReturn(caller);
            given(belegService.darfSehen(caller)).willReturn(true);

            mockMvc.perform(get("/api/buchhaltung/steuerberater-export")
                            .param("von", "2026-04-30")
                            .param("bis", "2026-04-01")
                            .principal(testAuth()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Ungueltiges Datum -> 400 (kein silent-fallback auf 'alles')")
        void ungueltigesDatum_liefert400() throws Exception {
            Mitarbeiter caller = new Mitarbeiter();
            caller.setId(42L);
            given(belegService.findCaller(any(), any())).willReturn(caller);
            given(belegService.darfSehen(caller)).willReturn(true);

            mockMvc.perform(get("/api/buchhaltung/steuerberater-export")
                            .param("von", "heute")
                            .param("bis", "2026-04-30")
                            .principal(testAuth()))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(get("/api/buchhaltung/steuerberater-export")
                            .param("von", "2026-04-01")
                            .param("bis", "morgen")
                            .principal(testAuth()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Ohne Datumsparameter laeuft trotzdem durch (Service kriegt null/null)")
        void ohneDatum_liefertLeereListe() throws Exception {
            Mitarbeiter caller = new Mitarbeiter();
            caller.setId(42L);
            given(belegService.findCaller(any(), any())).willReturn(caller);
            given(belegService.darfSehen(caller)).willReturn(true);
            given(belegService.listeFuerSteuerberaterExport(null, null)).willReturn(List.of());

            mockMvc.perform(get("/api/buchhaltung/steuerberater-export")
                            .principal(testAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }
}
