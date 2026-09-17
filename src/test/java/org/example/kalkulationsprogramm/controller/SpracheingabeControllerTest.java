package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.SpracheingabeErgebnis;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.SpracheingabeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueft den Endpunkt-Vertrag aus dem Plan (Statuscodes 200/401/413/400/502)
 * und die Sicherheits-Pflichtcheckliste aus TESTING_SECURITY.md fuer den
 * Token-Parameter. {@code SpracheingabeService} bleibt gemockt - die
 * fachliche Umwandlung ist bereits durch {@code SpracheingabeServiceTest}
 * abgedeckt (Task 1).
 *
 * DSGVO: nur der erfundene Dummy-Mitarbeiter "Max Mustermann", keine echten
 * Namen oder Aufnahmen.
 */
@WebMvcTest(SpracheingabeController.class)
@AutoConfigureMockMvc(addFilters = false)
class SpracheingabeControllerTest {

    private static final byte[] AUFNAHME = {1, 2, 3};

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SpracheingabeService spracheingabeService;

    @MockBean
    private MitarbeiterRepository mitarbeiterRepository;

    private static Mitarbeiter aktiverMitarbeiter() {
        Mitarbeiter mitarbeiter = new Mitarbeiter();
        mitarbeiter.setId(1L);
        mitarbeiter.setVorname("Max");
        mitarbeiter.setNachname("Mustermann");
        mitarbeiter.setAktiv(true);
        mitarbeiter.setLoginToken("valid-token");
        return mitarbeiter;
    }

    /**
     * {@link SpracheingabeService.AufnahmeZuGross} hat absichtlich einen
     * paketprivaten Konstruktor (nur {@code SpracheingabeService} und dessen
     * eigener Test duerfen sie werfen). Dieser Test liegt im Controller-Paket
     * und muss die Ausnahme daher wie der Controller selbst nur als Typ
     * behandeln - ueber Reflection erzeugt, nie mit {@code new} direkt.
     */
    private static SpracheingabeService.AufnahmeZuGross aufnahmeZuGross() {
        try {
            Constructor<SpracheingabeService.AufnahmeZuGross> konstruktor =
                    SpracheingabeService.AufnahmeZuGross.class.getDeclaredConstructor();
            konstruktor.setAccessible(true);
            return konstruktor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Nested
    @DisplayName("POST /api/spracheingabe/transkribieren")
    class Transkribieren {

        @Test
        @DisplayName("Gueltiger aktiver Mitarbeiter bekommt den transkribierten Text")
        void gueltigerMitarbeiterBekommtText() throws Exception {
            given(mitarbeiterRepository.findByLoginToken("valid-token"))
                    .willReturn(Optional.of(aktiverMitarbeiter()));
            given(spracheingabeService.transkribiere(any(InputStream.class), eq("audio/webm")))
                    .willReturn(new SpracheingabeErgebnis("Heute Gelaender montiert."));

            mockMvc.perform(post("/api/spracheingabe/transkribieren")
                            .param("token", "valid-token")
                            .contentType("audio/webm")
                            .content(AUFNAHME))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.text").value("Heute Gelaender montiert."));

            verify(spracheingabeService, times(1))
                    .transkribiere(any(InputStream.class), eq("audio/webm"));
        }

        @Test
        @DisplayName("Unbekannter Token gibt 401, Service bleibt unberuehrt")
        void unbekannterTokenGibt401() throws Exception {
            given(mitarbeiterRepository.findByLoginToken("unknown-token"))
                    .willReturn(Optional.empty());

            mockMvc.perform(post("/api/spracheingabe/transkribieren")
                            .param("token", "unknown-token")
                            .contentType("audio/webm")
                            .content(AUFNAHME))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(spracheingabeService);
        }

        @Test
        @DisplayName("Inaktiver Mitarbeiter gibt 401, Service bleibt unberuehrt")
        void inaktiverMitarbeiterGibt401() throws Exception {
            Mitarbeiter inaktiv = aktiverMitarbeiter();
            inaktiv.setAktiv(false);
            given(mitarbeiterRepository.findByLoginToken("valid-token"))
                    .willReturn(Optional.of(inaktiv));

            mockMvc.perform(post("/api/spracheingabe/transkribieren")
                            .param("token", "valid-token")
                            .contentType("audio/webm")
                            .content(AUFNAHME))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(spracheingabeService);
        }

        @Test
        @DisplayName("Service wirft AufnahmeZuGross -> 413")
        void zuGrosseAufnahmeGibt413() throws Exception {
            given(mitarbeiterRepository.findByLoginToken("valid-token"))
                    .willReturn(Optional.of(aktiverMitarbeiter()));
            given(spracheingabeService.transkribiere(any(InputStream.class), any()))
                    .willThrow(aufnahmeZuGross());

            mockMvc.perform(post("/api/spracheingabe/transkribieren")
                            .param("token", "valid-token")
                            .contentType("audio/webm")
                            .content(AUFNAHME))
                    .andExpect(status().isPayloadTooLarge())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Die Aufnahme ist zu lang. Bitte kuerzer diktieren."));
        }

        @Test
        @DisplayName("Service wirft IllegalArgumentException -> 400")
        void ungueltigesFormatGibt400() throws Exception {
            given(mitarbeiterRepository.findByLoginToken("valid-token"))
                    .willReturn(Optional.of(aktiverMitarbeiter()));
            given(spracheingabeService.transkribiere(any(InputStream.class), any()))
                    .willThrow(new IllegalArgumentException("Dieses Audioformat wird nicht unterstuetzt."));

            mockMvc.perform(post("/api/spracheingabe/transkribieren")
                            .param("token", "valid-token")
                            .contentType("audio/webm")
                            .content(AUFNAHME))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Dieses Audioformat wird nicht unterstuetzt."));
        }

        @Test
        @DisplayName("Service wirft IllegalStateException -> 502, Antwort ohne interne Details")
        void geminiFehlerGibt502OhneDetails() throws Exception {
            given(mitarbeiterRepository.findByLoginToken("valid-token"))
                    .willReturn(Optional.of(aktiverMitarbeiter()));
            given(spracheingabeService.transkribiere(any(InputStream.class), any()))
                    .willThrow(new IllegalStateException("Geheimes Transkript von Max Mustermann"));

            mockMvc.perform(post("/api/spracheingabe/transkribieren")
                            .param("token", "valid-token")
                            .contentType("audio/webm")
                            .content(AUFNAHME))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Die Spracherkennung ist gerade nicht erreichbar."))
                    .andExpect(content().string(not(containsString("Geheimes Transkript"))));
        }

        @Test
        @DisplayName("Sicherheitscheckliste: SQL-Injection im Token -> 401 statt 500, Repository bekommt den String unveraendert")
        void sqlInjectionImTokenGibt401() throws Exception {
            String payload = "'; DROP TABLE x; --";
            given(mitarbeiterRepository.findByLoginToken(payload)).willReturn(Optional.empty());

            mockMvc.perform(post("/api/spracheingabe/transkribieren")
                            .param("token", payload)
                            .contentType("audio/webm")
                            .content(AUFNAHME))
                    .andExpect(status().isUnauthorized());

            verify(mitarbeiterRepository).findByLoginToken(payload);
            verifyNoInteractions(spracheingabeService);
        }

        @Test
        @DisplayName("Sicherheitscheckliste: XSS im Token -> 401 statt 500, Repository bekommt den String unveraendert")
        void xssImTokenGibt401() throws Exception {
            String payload = "<script>alert(1)</script>";
            given(mitarbeiterRepository.findByLoginToken(payload)).willReturn(Optional.empty());

            mockMvc.perform(post("/api/spracheingabe/transkribieren")
                            .param("token", payload)
                            .contentType("audio/webm")
                            .content(AUFNAHME))
                    .andExpect(status().isUnauthorized());

            verify(mitarbeiterRepository).findByLoginToken(payload);
            verifyNoInteractions(spracheingabeService);
        }

        @Test
        @DisplayName("Ohne Content-Type-Header bekommt der Service null und der 400-Pfad greift")
        void ohneContentTypeGibtNullAnDenServiceUnd400() throws Exception {
            given(mitarbeiterRepository.findByLoginToken("valid-token"))
                    .willReturn(Optional.of(aktiverMitarbeiter()));
            given(spracheingabeService.transkribiere(any(InputStream.class), isNull()))
                    .willThrow(new IllegalArgumentException("Dieses Audioformat wird nicht unterstuetzt."));

            mockMvc.perform(post("/api/spracheingabe/transkribieren")
                            .param("token", "valid-token")
                            .content(AUFNAHME))
                    .andExpect(status().isBadRequest());

            verify(spracheingabeService).transkribiere(any(InputStream.class), isNull());
        }
    }
}
