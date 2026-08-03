package org.example.kalkulationsprogramm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto;
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnUebernehmenRequest;
import org.example.kalkulationsprogramm.service.VerrechnungslohnService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests fuer die Endpoints des Stundensatz-Rechners
 * ("Was muss meine Stunde kosten?").
 */
@WebMvcTest(VerrechnungslohnController.class)
@AutoConfigureMockMvc(addFilters = false)
class VerrechnungslohnControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VerrechnungslohnService verrechnungslohnService;

    private static VerrechnungslohnErgebnisDto ergebnis(int jahr, int interneQuote) {
        VerrechnungslohnErgebnisDto dto = new VerrechnungslohnErgebnisDto();
        dto.setJahr(jahr);
        dto.setModus(VerrechnungslohnErgebnisDto.Modus.HOCHRECHNUNG);
        dto.setInterneQuoteProzent(interneQuote);
        dto.setSelbstkostenProStunde(new BigDecimal("62.50"));
        return dto;
    }

    @Nested
    @DisplayName("GET /api/verrechnungslohn")
    class Berechne {

        @Test
        @DisplayName("Gibt 200 mit dem Rechenergebnis zurueck")
        void gibtErgebnisZurueck() throws Exception {
            given(verrechnungslohnService.berechne(eq(2026), any()))
                    .willReturn(ergebnis(2026, 5));

            mockMvc.perform(get("/api/verrechnungslohn").param("jahr", "2026"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jahr").value(2026))
                    .andExpect(jsonPath("$.modus").value("HOCHRECHNUNG"))
                    .andExpect(jsonPath("$.selbstkostenProStunde").value(62.50));
        }

        @Test
        @DisplayName("Reicht internProzent an den Service durch")
        void reichtInternProzentDurch() throws Exception {
            given(verrechnungslohnService.berechne(2026, 20)).willReturn(ergebnis(2026, 20));

            mockMvc.perform(get("/api/verrechnungslohn")
                            .param("jahr", "2026")
                            .param("internProzent", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.interneQuoteProzent").value(20));

            verify(verrechnungslohnService).berechne(2026, 20);
        }

        @Test
        @DisplayName("Ohne internProzent bekommt der Service null (Standardwert)")
        void ohneInternProzentNull() throws Exception {
            given(verrechnungslohnService.berechne(eq(2026), any())).willReturn(ergebnis(2026, 5));

            mockMvc.perform(get("/api/verrechnungslohn").param("jahr", "2026"))
                    .andExpect(status().isOk());

            verify(verrechnungslohnService).berechne(2026, null);
        }

        @Test
        @DisplayName("Fehlendes Jahr wird abgelehnt")
        void ohneJahrBadRequest() throws Exception {
            mockMvc.perform(get("/api/verrechnungslohn"))
                    .andExpect(status().isBadRequest());

            verify(verrechnungslohnService, never()).berechne(anyInt(), any());
        }

        @Test
        @DisplayName("Jahr ausserhalb 2000-2100 wird abgelehnt")
        void unplausiblesJahrAbgelehnt() throws Exception {
            mockMvc.perform(get("/api/verrechnungslohn").param("jahr", "1999"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get("/api/verrechnungslohn").param("jahr", "2200"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get("/api/verrechnungslohn").param("jahr", "-1"))
                    .andExpect(status().isBadRequest());

            verify(verrechnungslohnService, never()).berechne(anyInt(), any());
        }

        @Test
        @DisplayName("internProzent ausserhalb 0-100 wird abgelehnt")
        void unplausiblesInternProzentAbgelehnt() throws Exception {
            mockMvc.perform(get("/api/verrechnungslohn")
                            .param("jahr", "2026")
                            .param("internProzent", "150"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get("/api/verrechnungslohn")
                            .param("jahr", "2026")
                            .param("internProzent", "-5"))
                    .andExpect(status().isBadRequest());

            verify(verrechnungslohnService, never()).berechne(anyInt(), any());
        }

        @Test
        @DisplayName("Text statt Zahl wird abgelehnt (kein Durchreichen an den Service)")
        void textParameterAbgelehnt() throws Exception {
            mockMvc.perform(get("/api/verrechnungslohn").param("jahr", "'; DROP TABLE arbeitsgang; --"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get("/api/verrechnungslohn")
                            .param("jahr", "2026")
                            .param("internProzent", "<script>alert(1)</script>"))
                    .andExpect(status().isBadRequest());

            verify(verrechnungslohnService, never()).berechne(anyInt(), any());
        }
    }

    @Nested
    @DisplayName("POST /api/verrechnungslohn/uebernehmen")
    class Uebernehmen {

        private String json(int jahr, String basisSatz) throws Exception {
            VerrechnungslohnUebernehmenRequest req = new VerrechnungslohnUebernehmenRequest();
            req.setJahr(jahr);
            req.setBasisSatz(basisSatz == null ? null : new BigDecimal(basisSatz));
            return objectMapper.writeValueAsString(req);
        }

        @Test
        @DisplayName("Gibt 200 mit der Anzahl aktualisierter Arbeitsgaenge zurueck")
        void uebernimmtSatz() throws Exception {
            given(verrechnungslohnService.uebernehmen(any())).willReturn(12);

            mockMvc.perform(post("/api/verrechnungslohn/uebernehmen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(2026, "75.00")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.aktualisierteArbeitsgaenge").value(12))
                    .andExpect(jsonPath("$.jahr").value(2026));
        }

        @Test
        @DisplayName("Fachliche Ablehnung kommt als 400 mit lesbarer Meldung an, nicht als 500")
        void fachlicheAblehnungWirdBadRequest() throws Exception {
            given(verrechnungslohnService.uebernehmen(any()))
                    .willThrow(new IllegalArgumentException(
                            "Der Abschlag fuer die Abteilung \"Schweisserei\" ist zu gross."));

            mockMvc.perform(post("/api/verrechnungslohn/uebernehmen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(2026, "50.00")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("Schweisserei")));
        }

        @Test
        @DisplayName("Ohne basisSatz wird abgelehnt")
        void ohneBasisSatzBadRequest() throws Exception {
            mockMvc.perform(post("/api/verrechnungslohn/uebernehmen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(2026, null)))
                    .andExpect(status().isBadRequest());

            verify(verrechnungslohnService, never()).uebernehmen(any());
        }

        @Test
        @DisplayName("Nicht-positiver basisSatz wird abgelehnt")
        void negativerBasisSatzBadRequest() throws Exception {
            mockMvc.perform(post("/api/verrechnungslohn/uebernehmen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(2026, "0.00")))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(post("/api/verrechnungslohn/uebernehmen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(2026, "-10.00")))
                    .andExpect(status().isBadRequest());

            verify(verrechnungslohnService, never()).uebernehmen(any());
        }

        @Test
        @DisplayName("Unplausibles Jahr wird abgelehnt")
        void unplausiblesJahrBadRequest() throws Exception {
            mockMvc.perform(post("/api/verrechnungslohn/uebernehmen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(1999, "75.00")))
                    .andExpect(status().isBadRequest());

            verify(verrechnungslohnService, never()).uebernehmen(any());
        }

        @Test
        @DisplayName("Kaputtes JSON wird abgelehnt")
        void kaputtesJsonBadRequest() throws Exception {
            mockMvc.perform(post("/api/verrechnungslohn/uebernehmen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ das ist kein json "))
                    .andExpect(status().isBadRequest());

            verify(verrechnungslohnService, never()).uebernehmen(any());
        }
    }

    @Nested
    @DisplayName("Antwortformat")
    class Antwort {

        @Test
        @DisplayName("Meldet die verwendete interne Quote zurueck an die Oberflaeche")
        void spiegeltInterneQuote() throws Exception {
            given(verrechnungslohnService.berechne(eq(2026), any())).willReturn(ergebnis(2026, 5));

            mockMvc.perform(get("/api/verrechnungslohn").param("jahr", "2026"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.interneQuoteProzent").value(5))
                    .andExpect(jsonPath("$.lohnzeilen").isArray())
                    .andExpect(jsonPath("$.stundenzeilen").isArray())
                    .andExpect(jsonPath("$.datenLuecken").isArray());
        }

        @Test
        @DisplayName("Map.of vertraegt kein null - jahr ist im uebernehmen-Response immer gesetzt")
        void jahrImmerImResponse() throws Exception {
            given(verrechnungslohnService.uebernehmen(any())).willReturn(0);

            mockMvc.perform(post("/api/verrechnungslohn/uebernehmen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("jahr", 2026, "basisSatz", new BigDecimal("50.00")))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jahr").value(2026))
                    .andExpect(jsonPath("$.aktualisierteArbeitsgaenge").value(0));
        }
    }
}
