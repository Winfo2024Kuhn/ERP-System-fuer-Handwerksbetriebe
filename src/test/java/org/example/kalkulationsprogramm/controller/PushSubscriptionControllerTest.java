package org.example.kalkulationsprogramm.controller;

import java.util.Map;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.WebPushService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(PushSubscriptionController.class)
@AutoConfigureMockMvc(addFilters = false)
class PushSubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WebPushService webPushService;

    @MockBean
    private MitarbeiterRepository mitarbeiterRepository;

    private Mitarbeiter createActiveMitarbeiter(Long id) {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(id);
        m.setVorname("Max");
        m.setNachname("Mustermann");
        m.setAktiv(true);
        m.setLoginToken("valid-token");
        return m;
    }

    @Nested
    @DisplayName("GET /api/push/vapid-key")
    class GetVapidKey {

        @Test
        @DisplayName("Gibt 200 mit VAPID Key zurück bei gültigem Token")
        void gibtVapidKeyZurueck() throws Exception {
            Mitarbeiter m = createActiveMitarbeiter(1L);
            given(mitarbeiterRepository.findByLoginToken("valid-token")).willReturn(Optional.of(m));
            given(webPushService.isEnabled()).willReturn(true);
            given(webPushService.getVapidPublicKey()).willReturn("test-public-key-123");

            mockMvc.perform(get("/api/push/vapid-key").param("token", "valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.publicKey").value("test-public-key-123"))
                    .andExpect(jsonPath("$.enabled").value("true"));
        }

        @Test
        @DisplayName("Gibt 401 zurück bei ungültigem Token")
        void gibt401BeiUngueltigemToken() throws Exception {
            given(mitarbeiterRepository.findByLoginToken("invalid-token")).willReturn(Optional.empty());

            mockMvc.perform(get("/api/push/vapid-key").param("token", "invalid-token"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Gibt 401 zurück bei inaktivem Mitarbeiter")
        void gibt401BeiInaktivemMitarbeiter() throws Exception {
            Mitarbeiter m = createActiveMitarbeiter(1L);
            m.setAktiv(false);
            given(mitarbeiterRepository.findByLoginToken("inactive-token")).willReturn(Optional.of(m));

            mockMvc.perform(get("/api/push/vapid-key").param("token", "inactive-token"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Gibt enabled=false zurück wenn Push deaktiviert")
        void gibtDisabledZurueck() throws Exception {
            Mitarbeiter m = createActiveMitarbeiter(1L);
            given(mitarbeiterRepository.findByLoginToken("valid-token")).willReturn(Optional.of(m));
            given(webPushService.isEnabled()).willReturn(false);

            mockMvc.perform(get("/api/push/vapid-key").param("token", "valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value("false"))
                    .andExpect(jsonPath("$.publicKey").value(""));
        }
    }

    @Nested
    @DisplayName("POST /api/push/subscribe")
    class SubscribeEndpoint {

        @Test
        @DisplayName("Gibt 200 mit subscribed=true bei gültigem Request")
        void subscribedErfolgreich() throws Exception {
            Mitarbeiter m = createActiveMitarbeiter(1L);
            given(mitarbeiterRepository.findByLoginToken("valid-token")).willReturn(Optional.of(m));
            given(webPushService.isEnabled()).willReturn(true);

            String body = objectMapper.writeValueAsString(Map.of(
                    "endpoint", "https://push.example.com/v1/endpoint",
                    "p256dh", "test-p256dh-key",
                    "auth", "test-auth-key"
            ));

            mockMvc.perform(post("/api/push/subscribe")
                            .param("token", "valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.subscribed").value(true));

            verify(webPushService).subscribe(eq(1L), eq("https://push.example.com/v1/endpoint"),
                    eq("test-p256dh-key"), eq("test-auth-key"));
        }

        @Test
        @DisplayName("Gibt 401 zurück bei ungültigem Token")
        void gibt401BeiUngueltigemToken() throws Exception {
            given(mitarbeiterRepository.findByLoginToken("bad-token")).willReturn(Optional.empty());

            mockMvc.perform(post("/api/push/subscribe")
                            .param("token", "bad-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"endpoint\":\"test\",\"p256dh\":\"test\",\"auth\":\"test\"}"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(webPushService);
        }

        @Test
        @DisplayName("Gibt subscribed=false zurück wenn Push deaktiviert")
        void gibtFalseWennDeaktiviert() throws Exception {
            Mitarbeiter m = createActiveMitarbeiter(1L);
            given(mitarbeiterRepository.findByLoginToken("valid-token")).willReturn(Optional.of(m));
            given(webPushService.isEnabled()).willReturn(false);

            mockMvc.perform(post("/api/push/subscribe")
                            .param("token", "valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"endpoint\":\"test\",\"p256dh\":\"test\",\"auth\":\"test\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.subscribed").value(false));
        }
    }

    @Nested
    @DisplayName("POST /api/push/unsubscribe")
    class UnsubscribeEndpoint {

        @Test
        @DisplayName("Gibt 200 mit unsubscribed=true bei gültigem Request")
        void unsubscribedErfolgreich() throws Exception {
            Mitarbeiter m = createActiveMitarbeiter(1L);
            given(mitarbeiterRepository.findByLoginToken("valid-token")).willReturn(Optional.of(m));

            mockMvc.perform(post("/api/push/unsubscribe")
                            .param("token", "valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"endpoint\":\"https://push.example.com/v1/endpoint\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unsubscribed").value(true));

            verify(webPushService).unsubscribe("https://push.example.com/v1/endpoint");
        }

        @Test
        @DisplayName("Gibt 401 zurück bei ungültigem Token")
        void gibt401BeiUngueltigemToken() throws Exception {
            given(mitarbeiterRepository.findByLoginToken("bad-token")).willReturn(Optional.empty());

            mockMvc.perform(post("/api/push/unsubscribe")
                            .param("token", "bad-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"endpoint\":\"test\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Ignoriert leeren Endpoint ohne Fehler")
        void ignoriertLeerenEndpoint() throws Exception {
            Mitarbeiter m = createActiveMitarbeiter(1L);
            given(mitarbeiterRepository.findByLoginToken("valid-token")).willReturn(Optional.of(m));

            mockMvc.perform(post("/api/push/unsubscribe")
                            .param("token", "valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"endpoint\":\"\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unsubscribed").value(true));

            verify(webPushService, never()).unsubscribe(anyString());
        }
    }
}
