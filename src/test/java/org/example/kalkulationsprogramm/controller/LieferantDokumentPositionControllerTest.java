package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.dto.Bestellung.DokumentPositionenDto;
import org.example.kalkulationsprogramm.service.BelegService;
import org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService;
import org.example.kalkulationsprogramm.service.LieferantDokumentZuordnungService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = LieferantDokumentPositionController.class)
@Import({ SecurityConfig.class, LieferantDokumentPositionControllerTest.EchteFilterBeans.class })
class LieferantDokumentPositionControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class EchteFilterBeans {
        @org.springframework.context.annotation.Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    private static final String BASIS = "/api/bestellungen-uebersicht/positionen/";
    private static final String GUELTIG = """
            {"positionen":[{"positionId":1,"projektId":3}],"ziele":[{"projektId":3,"beschreibung":"Material"}]}""";

    @Autowired private MockMvc mockMvc;

    @MockBean private LieferantDokumentZuordnungService zuordnungService;
    @MockBean private GeminiDokumentAnalyseService analyseService;
    @MockBean private BelegService belegService;
    @MockBean private FrontendUserDetailsService frontendUserDetailsService;

    /** @MockBean wird in @Nested-Klassen nicht zurückgesetzt – daher hier von Hand. */
    @AfterEach
    void mocksZuruecksetzen() {
        org.mockito.Mockito.reset(zuordnungService, analyseService, belegService);
    }

    private static RequestPostProcessor csrf() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf();
    }

    private static DokumentPositionenDto.Uebersicht uebersicht() {
        return new DokumentPositionenDto.Uebersicht(10L, "RECHNUNG", true, new BigDecimal("100"),
                new BigDecimal("119"), new BigDecimal("100"), BigDecimal.ZERO, false, false,
                List.of(new DokumentPositionenDto.Position(1L, 1, "WARE", "MAT-001", "Flachstahl 50x5",
                        BigDecimal.TEN, "m", new BigDecimal("10"), "€/m", new BigDecimal("100"),
                        null, null, null, null, null, null, null)));
    }

    @Nested
    class Anmeldung {

        @Test
        @DisplayName("Ohne Anmeldung: 401")
        void ohneLogin() throws Exception {
            mockMvc.perform(get(BASIS + "10")).andExpect(status().isUnauthorized());
            mockMvc.perform(post(BASIS + "10/zuordnen").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(GUELTIG)).andExpect(status().isUnauthorized());
            verifyNoInteractions(zuordnungService, analyseService);
        }

        @Test
        @WithMockUser
        @DisplayName("Ändernde Aufrufe ohne CSRF-Token: 403")
        void ohneCsrf() throws Exception {
            mockMvc.perform(post(BASIS + "10/zuordnen").contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(BASIS + "10/auslesen")).andExpect(status().isForbidden());
            verifyNoInteractions(zuordnungService, analyseService);
        }
    }

    @Nested
    @WithMockUser
    class Abrufen {

        @Test
        void liefertPositionen() throws Exception {
            given(zuordnungService.positionsUebersicht(10L)).willReturn(uebersicht());

            mockMvc.perform(get(BASIS + "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.auslesbar").value(true))
                    .andExpect(jsonPath("$.positionen[0].bezeichnung").value("Flachstahl 50x5"));
        }

        @Test
        void unbekanntesDokument() throws Exception {
            given(zuordnungService.positionsUebersicht(any()))
                    .willThrow(new NoSuchElementException("Geschäftsdokument nicht gefunden"));

            mockMvc.perform(get(BASIS + Long.MAX_VALUE)).andExpect(status().isNotFound());
        }

        @ParameterizedTest
        @ValueSource(strings = { "0", "-1" })
        void ungueltigeId(String id) throws Exception {
            mockMvc.perform(get(BASIS + id)).andExpect(status().isBadRequest());
            verifyNoInteractions(zuordnungService);
        }

        @Test
        void sqlInjectionInDerIdWirdAbgelehnt() throws Exception {
            mockMvc.perform(get(BASIS + "1'; DROP TABLE lieferant_dokument_position; --"))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(zuordnungService);
        }
    }

    @Nested
    @WithMockUser
    class Auslesen {

        @Test
        void liestNachUndLiefertUebersicht() throws Exception {
            given(analyseService.positionenNachlesen(10L)).willReturn(1);
            given(zuordnungService.positionsUebersicht(10L)).willReturn(uebersicht());

            mockMvc.perform(post(BASIS + "10/auslesen").with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.positionen.length()").value(1));
        }

        @Test
        void typOhnePositionen() throws Exception {
            given(analyseService.positionenNachlesen(10L))
                    .willThrow(new IllegalArgumentException("Für diesen Dokumenttyp werden keine Positionen ausgelesen"));

            mockMvc.perform(post(BASIS + "10/auslesen").with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Für diesen Dokumenttyp werden keine Positionen ausgelesen"));
        }

        @Test
        void kiScheitert() throws Exception {
            given(analyseService.positionenNachlesen(10L))
                    .willThrow(new org.example.kalkulationsprogramm.service.PositionenNichtLesbarException("Die KI konnte die Positionen nicht vollständig lesen"));

            mockMvc.perform(post(BASIS + "10/auslesen").with(csrf()))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void dokumentFehlt() throws Exception {
            given(analyseService.positionenNachlesen(10L)).willThrow(new NoSuchElementException("Dokument nicht gefunden"));

            mockMvc.perform(post(BASIS + "10/auslesen").with(csrf())).andExpect(status().isNotFound());
        }
    }

    @Nested
    @WithMockUser
    class Zuordnen {

        @Test
        void speichert() throws Exception {
            given(zuordnungService.speichereNachPositionen(eq(10L), any(), any())).willReturn(1);

            mockMvc.perform(post(BASIS + "10/zuordnen").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(GUELTIG))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.zuordnungen").value(1));
        }

        @Test
        void fachlicherFehler() throws Exception {
            given(zuordnungService.speichereNachPositionen(eq(10L), any(), any()))
                    .willThrow(new IllegalArgumentException("Eine Position hat noch kein Projekt."));

            mockMvc.perform(post(BASIS + "10/zuordnen").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(GUELTIG))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Eine Position hat noch kein Projekt."));
        }

        @Test
        void vorschau() throws Exception {
            given(zuordnungService.vorschau(eq(10L), anyList())).willReturn(new DokumentPositionenDto.Vorschau(
                    List.of(), 0, BigDecimal.TEN, BigDecimal.ZERO, null, true, null));

            mockMvc.perform(post(BASIS + "10/vorschau").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(GUELTIG))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.speicherbar").value(true));
            verify(zuordnungService).vorschau(eq(10L), anyList());
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "{\"positionen\":[{\"positionId\":-1,\"projektId\":3}]}",
                "{\"positionen\":[{\"positionId\":0,\"projektId\":3}]}",
                "{\"positionen\":[{\"projektId\":3}]}",
                "{\"positionen\":[{\"positionId\":1,\"projektId\":-5}]}",
                "{\"positionen\":[null]}",
                "{}",
        })
        void ungueltigeEingaben(String body) throws Exception {
            mockMvc.perform(post(BASIS + "10/zuordnen").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(zuordnungService);
        }

        @Test
        void ueberlangeBeschreibung() throws Exception {
            String body = "{\"positionen\":[{\"positionId\":1,\"projektId\":3}],\"ziele\":[{\"projektId\":3,"
                    + "\"beschreibung\":\"" + "x".repeat(10_001) + "\"}]}";

            mockMvc.perform(post(BASIS + "10/zuordnen").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(zuordnungService);
        }

        @Test
        void scriptInBeschreibungWirdNurAlsTextWeitergegeben() throws Exception {
            given(zuordnungService.speichereNachPositionen(eq(10L), any(), any())).willReturn(1);
            String body = "{\"positionen\":[{\"positionId\":1,\"projektId\":3}],\"ziele\":[{\"projektId\":3,"
                    + "\"beschreibung\":\"<script>alert(1)</script>\"}]}";

            mockMvc.perform(post(BASIS + "10/zuordnen").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Erfolgreich 1 Zuordnung(en) gespeichert"));
        }
    }
}
