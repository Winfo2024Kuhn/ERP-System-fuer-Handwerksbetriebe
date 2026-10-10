package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.NoSuchElementException;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.dto.Postfach.PostfachDto;
import org.example.kalkulationsprogramm.dto.Postfach.PostfachSpeichernRequest;
import org.example.kalkulationsprogramm.dto.Postfach.PostfachTestErgebnis;
import org.example.kalkulationsprogramm.service.PostfachService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Einstellungen → E-Mail → Postfächer: nur Admins, CSRF Pflicht, Fehler in Handwerker-Sprache.
 * Läuft mit der echten SecurityConfig.
 */
@WebMvcTest(controllers = PostfachController.class)
@Import({ SecurityConfig.class, PostfachControllerTest.EchteFilterBeans.class })
class PostfachControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class EchteFilterBeans {
        @org.springframework.context.annotation.Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @MockBean private PostfachService postfachService;
    @MockBean private FrontendUserDetailsService frontendUserDetailsService;

    /** Verschachtelte Testklassen teilen den Kontext – Mock vor jedem Test leeren. */
    @org.junit.jupiter.api.BeforeEach
    void mockLeeren() {
        org.mockito.Mockito.reset(postfachService);
    }

    private static final String GUELTIG = """
            {"emailAdresse":"max@example.com","anzeigename":"Max Mustermann","aktiv":true,
             "hauptpostfach":false,"fuerGeschaeftsdokumente":false,"passwort":"geheim",
             "smtpHost":"mail.example.com","smtpPort":465,"imapHost":"mail.example.com","imapPort":993}
            """;

    private static PostfachDto dto(long id, String adresse) {
        return new PostfachDto(id, adresse, null, true, 0, false, false, null, true, "mail.example.com", 465,
                "mail.example.com", 993, true, null, null, List.of());
    }

    @Nested
    @DisplayName("Zugriff")
    class Zugriff {

        @Test
        void ohneAnmeldung401() throws Exception {
            mockMvc.perform(get("/api/postfaecher")).andExpect(status().isUnauthorized());
            verifyNoInteractions(postfachService);
        }

        @Test
        @WithMockUser(roles = "USER")
        void normalerBenutzer403() throws Exception {
            mockMvc.perform(get("/api/postfaecher")).andExpect(status().isForbidden());
            mockMvc.perform(post("/api/postfaecher").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(postfachService);
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void ohneCsrf403() throws Exception {
            mockMvc.perform(post("/api/postfaecher").contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/postfaecher/5")).andExpect(status().isForbidden());
            verifyNoInteractions(postfachService);
        }
    }

    @Nested
    @DisplayName("Admin")
    @WithMockUser(roles = "ADMIN")
    class Admin {

        @Test
        void listeOhnePasswort() throws Exception {
            given(postfachService.alle()).willReturn(List.of(dto(1, "info@example.com")));

            mockMvc.perform(get("/api/postfaecher"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].emailAdresse").value("info@example.com"))
                    .andExpect(jsonPath("$[0].passwortGesetzt").value(true))
                    .andExpect(jsonPath("$[0].passwort").doesNotExist())
                    .andExpect(jsonPath("$[0].passwortVerschluesselt").doesNotExist());
        }

        @Test
        void anlegen201() throws Exception {
            given(postfachService.anlegen(any())).willReturn(dto(5, "max@example.com"));

            mockMvc.perform(post("/api/postfaecher").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(5));
        }

        @Test
        void ungueltigeEingabe400MitMeldung() throws Exception {
            given(postfachService.anlegen(any()))
                    .willThrow(new IllegalArgumentException("Bitte eine gültige E-Mail-Adresse eintragen."));

            mockMvc.perform(post("/api/postfaecher").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"emailAdresse\":\"<script>alert(1)</script>\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Bitte eine gültige E-Mail-Adresse eintragen."));
        }

        @Test
        void aendern() throws Exception {
            given(postfachService.aendern(eq(5L), any(PostfachSpeichernRequest.class))).willReturn(dto(5, "max@example.com"));

            mockMvc.perform(put("/api/postfaecher/5").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.emailAdresse").value("max@example.com"));
        }

        @Test
        void unbekannt404() throws Exception {
            given(postfachService.aendern(eq(Long.MAX_VALUE), any()))
                    .willThrow(new NoSuchElementException("Postfach nicht gefunden."));

            mockMvc.perform(put("/api/postfaecher/" + Long.MAX_VALUE).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Postfach nicht gefunden."));
        }

        @Test
        void sqlInjectionAlsIdIst400() throws Exception {
            mockMvc.perform(delete("/api/postfaecher/'; DROP TABLE email_absender; --").with(csrf()))
                    .andExpect(status().isBadRequest());
            verify(postfachService, never()).loeschen(any());
        }

        @Test
        void loeschen204() throws Exception {
            mockMvc.perform(delete("/api/postfaecher/5").with(csrf())).andExpect(status().isNoContent());
            verify(postfachService).loeschen(5L);
        }

        @Test
        void loeschenMitMails400() throws Exception {
            willThrow(new IllegalArgumentException("Dieses Postfach hat schon E-Mails – bitte stattdessen ausschalten."))
                    .given(postfachService).loeschen(6L);

            mockMvc.perform(delete("/api/postfaecher/6").with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Dieses Postfach hat schon E-Mails – bitte stattdessen ausschalten."));
        }

        @Test
        void verbindungTesten() throws Exception {
            given(postfachService.teste(any())).willReturn(new PostfachTestErgebnis(true, false, "Versand: ok · Abruf: Anmeldung fehlgeschlagen"));

            mockMvc.perform(post("/api/postfaecher/test").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":5,\"smtpHost\":\"mail.example.com\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.versandOk").value(true))
                    .andExpect(jsonPath("$.abrufOk").value(false));
        }
    }

    @Nested
    @DisplayName("Sichtbarkeit (Etappe 2)")
    class Sichtbarkeit {

        private static final String NUR_BESTIMMTE = """
                {"emailAdresse":"max@example.com","aktiv":true,"hauptpostfach":false,
                 "sichtbarFuerAlle":false,"abteilungIds":[2],"benutzerIds":[7]}
                """;

        @Test
        @WithMockUser(roles = "USER")
        void normalerBenutzerDarfSichtbarkeitNichtAendern403() throws Exception {
            mockMvc.perform(put("/api/postfaecher/5").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(NUR_BESTIMMTE))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(postfachService);
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void felderKommenBeimServiceAn() throws Exception {
            given(postfachService.aendern(eq(5L), any(PostfachSpeichernRequest.class))).willReturn(dto(5, "max@example.com"));

            mockMvc.perform(put("/api/postfaecher/5").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(NUR_BESTIMMTE))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<PostfachSpeichernRequest> captor =
                    org.mockito.ArgumentCaptor.forClass(PostfachSpeichernRequest.class);
            verify(postfachService).aendern(eq(5L), captor.capture());
            org.assertj.core.api.Assertions.assertThat(captor.getValue().sichtbarFuerAlle()).isFalse();
            org.assertj.core.api.Assertions.assertThat(captor.getValue().abteilungIds()).containsExactly(2L);
            org.assertj.core.api.Assertions.assertThat(captor.getValue().benutzerIds()).containsExactly(7L);
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void ohneFelderBleibenSieNull() throws Exception {
            given(postfachService.aendern(eq(5L), any(PostfachSpeichernRequest.class))).willReturn(dto(5, "max@example.com"));

            mockMvc.perform(put("/api/postfaecher/5").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<PostfachSpeichernRequest> captor =
                    org.mockito.ArgumentCaptor.forClass(PostfachSpeichernRequest.class);
            verify(postfachService).aendern(eq(5L), captor.capture());
            org.assertj.core.api.Assertions.assertThat(captor.getValue().sichtbarFuerAlle()).isNull();
            org.assertj.core.api.Assertions.assertThat(captor.getValue().abteilungIds()).isNull();
            org.assertj.core.api.Assertions.assertThat(captor.getValue().benutzerIds()).isNull();
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void antwortEnthaeltSichtbarkeit() throws Exception {
            given(postfachService.alle()).willReturn(List.of(new PostfachDto(5L, "max@example.com", null, true, 0, false,
                    false, null, true, null, null, null, null, false, null, null, List.of(), false,
                    List.of(new PostfachDto.AbteilungRefDto(2L, "Büro")),
                    List.of(new PostfachDto.BenutzerRefDto(7L, "Max Mustermann")), false)));

            mockMvc.perform(get("/api/postfaecher"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].sichtbarFuerAlle").value(false))
                    .andExpect(jsonPath("$[0].sichtbarFuerAbteilungen[0].id").value(2))
                    .andExpect(jsonPath("$[0].sichtbarFuerAbteilungen[0].name").value("Büro"))
                    .andExpect(jsonPath("$[0].sichtbarFuerBenutzer[0].id").value(7))
                    .andExpect(jsonPath("$[0].sichtbarFuerBenutzer[0].displayName").value("Max Mustermann"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void unbekannteIdGibt400MitMeldung() throws Exception {
            given(postfachService.aendern(eq(5L), any()))
                    .willThrow(new IllegalArgumentException("Diese Abteilung gibt es nicht (mehr)."));

            for (String id : List.of(String.valueOf(Long.MAX_VALUE), "-1", "0")) {
                mockMvc.perform(put("/api/postfaecher/5").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"emailAdresse\":\"max@example.com\",\"abteilungIds\":[" + id + "]}"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").value("Diese Abteilung gibt es nicht (mehr)."));
            }
        }

        // ---- PUT /api/postfaecher/{id}/sichtbarkeit ----

        private static final String SICHTBARKEIT = "{\"sichtbarFuerAlle\":false,\"abteilungIds\":[2],\"benutzerIds\":[7]}";

        @Test
        void nurSichtbarkeitOhneAnmeldung401() throws Exception {
            mockMvc.perform(put("/api/postfaecher/5/sichtbarkeit").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(SICHTBARKEIT))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(postfachService);
        }

        @Test
        @WithMockUser(roles = "USER")
        void nurSichtbarkeitNormalerBenutzer403() throws Exception {
            mockMvc.perform(put("/api/postfaecher/5/sichtbarkeit").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(SICHTBARKEIT))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(postfachService);
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void nurSichtbarkeitOhneCsrf403() throws Exception {
            mockMvc.perform(put("/api/postfaecher/5/sichtbarkeit").contentType(MediaType.APPLICATION_JSON)
                            .content(SICHTBARKEIT))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(postfachService);
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void nurSichtbarkeit200() throws Exception {
            given(postfachService.sichtbarkeitAendern(eq(5L), any())).willReturn(new PostfachDto(5L, "max@example.com",
                    null, true, 0, false, false, null, true, null, null, null, null, false, null, null, List.of(), false,
                    List.of(new PostfachDto.AbteilungRefDto(2L, "Büro")), List.of(new PostfachDto.BenutzerRefDto(7L, "Erika Mustermann")), false));

            mockMvc.perform(put("/api/postfaecher/5/sichtbarkeit").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(SICHTBARKEIT))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sichtbarFuerAlle").value(false))
                    .andExpect(jsonPath("$.sichtbarFuerAbteilungen[0].name").value("Büro"))
                    .andExpect(jsonPath("$.sichtbarFuerBenutzer[0].displayName").value("Erika Mustermann"));

            org.mockito.ArgumentCaptor<org.example.kalkulationsprogramm.dto.Postfach.PostfachSichtbarkeitRequest> captor =
                    org.mockito.ArgumentCaptor.forClass(org.example.kalkulationsprogramm.dto.Postfach.PostfachSichtbarkeitRequest.class);
            verify(postfachService).sichtbarkeitAendern(eq(5L), captor.capture());
            org.assertj.core.api.Assertions.assertThat(captor.getValue().abteilungIds()).containsExactly(2L);
            org.assertj.core.api.Assertions.assertThat(captor.getValue().benutzerIds()).containsExactly(7L);
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void nurSichtbarkeitHauptpostfach400() throws Exception {
            given(postfachService.sichtbarkeitAendern(eq(1L), any()))
                    .willThrow(new IllegalArgumentException("Das Hauptpostfach sieht jeder im Betrieb."));

            mockMvc.perform(put("/api/postfaecher/1/sichtbarkeit").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(SICHTBARKEIT))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Das Hauptpostfach sieht jeder im Betrieb."));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void nurSichtbarkeitUnbekannteAbteilung400() throws Exception {
            given(postfachService.sichtbarkeitAendern(eq(5L), any()))
                    .willThrow(new IllegalArgumentException("Diese Abteilung gibt es nicht (mehr)."));

            mockMvc.perform(put("/api/postfaecher/5/sichtbarkeit").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sichtbarFuerAlle\":false,\"abteilungIds\":[" + Long.MAX_VALUE + "],\"benutzerIds\":[]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Diese Abteilung gibt es nicht (mehr)."));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void nurSichtbarkeitUnbekanntesPostfach404() throws Exception {
            given(postfachService.sichtbarkeitAendern(any(), any()))
                    .willThrow(new NoSuchElementException("Postfach nicht gefunden."));

            for (String id : List.of(String.valueOf(Long.MAX_VALUE), "-1", "0")) {
                mockMvc.perform(put("/api/postfaecher/" + id + "/sichtbarkeit").with(csrf())
                                .contentType(MediaType.APPLICATION_JSON).content(SICHTBARKEIT))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.message").value("Postfach nicht gefunden."));
            }
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void nurSichtbarkeitAngriffsTextAlsPfad400() throws Exception {
            for (String boese : List.of("'; DROP TABLE email_absender; --", "<img src=x onerror=alert(1)>")) {
                mockMvc.perform(put("/api/postfaecher/{id}/sichtbarkeit", boese).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON).content(SICHTBARKEIT))
                        .andExpect(status().isBadRequest());
            }
            // Mit Schrägstrich ("</script>") trifft der Pfad gar keinen Endpoint – abgewiesen ist er trotzdem.
            mockMvc.perform(put("/api/postfaecher/{id}/sichtbarkeit", "<script>alert(1)</script>").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(SICHTBARKEIT))
                    .andExpect(status().is4xxClientError());
            mockMvc.perform(put("/api/postfaecher/5/sichtbarkeit").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sichtbarFuerAlle\":false,\"abteilungIds\":[\"<script>alert(1)</script>\"]}"))
                    .andExpect(status().isBadRequest());
            verify(postfachService, never()).sichtbarkeitAendern(any(), any());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void textStattIdIst400() throws Exception {
            for (String boese : List.of("\"'; DROP TABLE abteilung; --\"", "\"<script>alert(1)</script>\"")) {
                mockMvc.perform(put("/api/postfaecher/5").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"emailAdresse\":\"max@example.com\",\"benutzerIds\":[" + boese + "]}"))
                        .andExpect(status().isBadRequest());
            }
            verify(postfachService, never()).aendern(any(), any());
        }
    }

    @Nested
    @DisplayName("Läuft aus")
    @WithMockUser(roles = "ADMIN")
    class LaeuftAus {

        @Test
        void feldKommtAnUndGehtZurueck() throws Exception {
            given(postfachService.aendern(eq(6L), any(PostfachSpeichernRequest.class))).willReturn(new PostfachDto(6L,
                    "betrieb@t-online.de", null, true, 0, false, false, null, true, null, null, null, null, true, null,
                    null, List.of(), true, List.of(), List.of(), true));

            mockMvc.perform(put("/api/postfaecher/6").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"emailAdresse\":\"betrieb@t-online.de\",\"laeuftAus\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.laeuftAus").value(true));

            org.mockito.ArgumentCaptor<PostfachSpeichernRequest> captor =
                    org.mockito.ArgumentCaptor.forClass(PostfachSpeichernRequest.class);
            verify(postfachService).aendern(eq(6L), captor.capture());
            org.assertj.core.api.Assertions.assertThat(captor.getValue().laeuftAus()).isTrue();
        }

        @Test
        void hauptpostfachAuslaufen400() throws Exception {
            given(postfachService.aendern(eq(1L), any())).willThrow(new IllegalArgumentException(
                    "Das Hauptpostfach kann nicht auslaufen. Bitte zuerst ein anderes Postfach zum Hauptpostfach machen."));

            mockMvc.perform(put("/api/postfaecher/1").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"emailAdresse\":\"info@example.com\",\"hauptpostfach\":true,\"laeuftAus\":true}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "Das Hauptpostfach kann nicht auslaufen. Bitte zuerst ein anderes Postfach zum Hauptpostfach machen."));
        }

        @Test
        void textStattWahrheitswert400() throws Exception {
            mockMvc.perform(put("/api/postfaecher/6").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"emailAdresse\":\"betrieb@t-online.de\",\"laeuftAus\":\"<script>alert(1)</script>\"}"))
                    .andExpect(status().isBadRequest());
            verify(postfachService, never()).aendern(any(), any());
        }
    }
}
