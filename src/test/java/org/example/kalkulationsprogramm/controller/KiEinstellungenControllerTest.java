package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.dto.Ki.KiEinstellungenDto;
import org.example.kalkulationsprogramm.dto.Ki.KiModellListeDto;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.SystemSettingsService.TestResult;
import org.example.kalkulationsprogramm.service.ki.KiAnbieter;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterTyp;
import org.example.kalkulationsprogramm.service.ki.KiDienst;
import org.example.kalkulationsprogramm.service.ki.KiEinstellungen;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiFunktionsWahl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Einstellungen → KI-Funktionen: nur Admins, nur mit CSRF, verständliche 400er. Nur Test-Schlüssel. */
@WebMvcTest(KiEinstellungenController.class)
@Import({ SecurityConfig.class, KiEinstellungenControllerTest.Filters.class })
class KiEinstellungenControllerTest {

    private static final String SQL = "'; DROP TABLE system_setting; --";
    private static final String XSS = "<script>alert(1)</script>";

    @TestConfiguration
    static class Filters {
        @Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired private MockMvc mvc;
    @MockitoBean private FrontendUserDetailsService users;
    @MockitoBean private FrontendUserProfileRepository profiles;
    @MockitoBean private KiDienst kiDienst;
    @MockitoBean private KiEinstellungen einstellungen;

    private final KiAnbieter mistral = mock(KiAnbieter.class);

    @BeforeEach
    void setUp() {
        when(mistral.typ()).thenReturn(KiAnbieterTyp.MISTRAL);
        when(kiDienst.anbieter(anyString())).thenAnswer(aufruf -> {
            if ("MISTRAL".equals(aufruf.getArgument(0))) {
                return mistral;
            }
            throw new IllegalArgumentException("Diesen KI-Anbieter gibt es nicht.");
        });
        when(kiDienst.uebersicht()).thenReturn(new KiEinstellungenDto("MISTRAL", true, true, List.of(), List.of()));
    }

    @Test
    void ohneAnmeldungAbgewiesen() throws Exception {
        mvc.perform(get("/api/settings/ki")).andExpect(status().isUnauthorized());
        verifyNoInteractions(kiDienst);
    }

    @Test
    @WithMockUser(roles = "USER")
    void ohneAdminrechtVerboten() throws Exception {
        mvc.perform(get("/api/settings/ki")).andExpect(status().isForbidden());
        mvc.perform(put("/api/settings/ki/anbieter/MISTRAL/schluessel").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\":\"test-mistral-123\"}"))
                .andExpect(status().isForbidden());
        verify(einstellungen, never()).speichereSchluessel(any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void ohneCsrfVerboten() throws Exception {
        mvc.perform(put("/api/settings/ki").contentType(MediaType.APPLICATION_JSON)
                .content("{\"anbieter\":\"MISTRAL\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/settings/ki/anbieter/MISTRAL/schluessel")).andExpect(status().isForbidden());
        verify(einstellungen, never()).speichereAuswahl(any(), any(Boolean.class));
        verify(einstellungen, never()).entferneSchluessel(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void ladenUndAnbieterWaehlen() throws Exception {
        mvc.perform(get("/api/settings/ki"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anbieter").value("MISTRAL"));

        when(einstellungen.ersatzErlaubt()).thenReturn(true);
        mvc.perform(put("/api/settings/ki").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"anbieter\":\"MISTRAL\"}"))
                .andExpect(status().isOk());
        verify(einstellungen).speichereAuswahl(KiAnbieterTyp.MISTRAL, true);

        mvc.perform(put("/api/settings/ki").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"anbieter\":\"MISTRAL\",\"ersatzErlaubt\":false}"))
                .andExpect(status().isOk());
        verify(einstellungen).speichereAuswahl(KiAnbieterTyp.MISTRAL, false);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unbekannterAnbieterGibt400() throws Exception {
        for (String typ : List.of("OPENAI", SQL, XSS)) {
            mvc.perform(put("/api/settings/ki").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"anbieter\":\"" + typ.replace("\"", "\\\"") + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Diesen KI-Anbieter gibt es nicht."));
        }
        mvc.perform(delete("/api/settings/ki/anbieter/OPENAI/schluessel").with(csrf()))
                .andExpect(status().isBadRequest());
        verify(einstellungen, never()).speichereAuswahl(any(), any(Boolean.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void schluesselSpeichernEntfernenTesten() throws Exception {
        mvc.perform(put("/api/settings/ki/anbieter/MISTRAL/schluessel").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\":\"test-mistral-123\"}"))
                .andExpect(status().isOk());
        verify(einstellungen).speichereSchluessel(mistral, "test-mistral-123");

        doThrow(new IllegalArgumentException("Bitte einen Schlüssel eintragen."))
                .when(einstellungen).speichereSchluessel(mistral, "");
        mvc.perform(put("/api/settings/ki/anbieter/MISTRAL/schluessel").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bitte einen Schlüssel eintragen."));

        mvc.perform(delete("/api/settings/ki/anbieter/MISTRAL/schluessel").with(csrf()))
                .andExpect(status().isOk());
        verify(einstellungen).entferneSchluessel(mistral);

        when(kiDienst.teste(eq(mistral), isNull())).thenReturn(TestResult.success("Schlüssel gültig."));
        mvc.perform(post("/api/settings/ki/anbieter/MISTRAL/test").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Schlüssel gültig."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void modelleSpeichernMitUngueltigemNamen() throws Exception {
        doThrow(new IllegalArgumentException("Der Modellname für „Standard“ ist ungültig."))
                .when(einstellungen).speichereModelle(eq(mistral), anyMap());

        mvc.perform(put("/api/settings/ki/anbieter/MISTRAL/modelle").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"STANDARD\":\"" + XSS + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Der Modellname für „Standard“ ist ungültig."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void funktionWaehlenUndZuruecksetzen() throws Exception {
        mvc.perform(put("/api/settings/ki/funktionen/DOKUMENT_ANALYSE").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"anbieter\":\"MISTRAL\",\"modell\":\"mistral-large-latest\"}"))
                .andExpect(status().isOk());
        verify(kiDienst).waehleFuerFunktion(KiFunktion.DOKUMENT_ANALYSE,
                new KiFunktionsWahl(KiAnbieterTyp.MISTRAL, "mistral-large-latest"));

        mvc.perform(put("/api/settings/ki/funktionen/DOKUMENT_ANALYSE").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"anbieter\":null}"))
                .andExpect(status().isOk());
        verify(kiDienst).waehleFuerFunktion(KiFunktion.DOKUMENT_ANALYSE, null);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void funktionMitUngueltigenAngaben() throws Exception {
        mvc.perform(put("/api/settings/ki/funktionen/" + "GIBTS_NICHT").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"anbieter\":\"MISTRAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Diese KI-Funktion gibt es nicht."));

        doThrow(new IllegalArgumentException("Mistral kann das in diesem Programm noch nicht (Sprache)."))
                .when(kiDienst).waehleFuerFunktion(eq(KiFunktion.SPRACHEINGABE), any());
        mvc.perform(put("/api/settings/ki/funktionen/SPRACHEINGABE").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"anbieter\":\"MISTRAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mistral kann das in diesem Programm noch nicht (Sprache)."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void modellListe() throws Exception {
        when(kiDienst.modellListe()).thenReturn(new KiModellListeDto(
                List.of(new KiModellListeDto.Modell("MISTRAL", "Mistral", "mistral-large-latest", "Mistral Large", true, false)),
                List.of("Google Gemini: Für diesen Anbieter ist noch kein Schlüssel hinterlegt.")));

        mvc.perform(get("/api/settings/ki/modelle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelle[0].modell").value("mistral-large-latest"))
                .andExpect(jsonPath("$.modelle[0].bilder").value(true))
                .andExpect(jsonPath("$.modelle[0].ton").value(false))
                .andExpect(jsonPath("$.hinweise[0]").exists());
    }
}
