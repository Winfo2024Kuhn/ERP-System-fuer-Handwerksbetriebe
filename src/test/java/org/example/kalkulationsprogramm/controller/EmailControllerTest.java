package org.example.kalkulationsprogramm.controller;

import java.util.Optional;

import org.example.kalkulationsprogramm.dto.Email.EmailBeautifyRequest;
import org.example.kalkulationsprogramm.dto.Email.EmailPreviewRequest;
import org.example.kalkulationsprogramm.dto.Email.EmailSendRequest;
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.EmailAiService;
import org.example.kalkulationsprogramm.service.EmailSignatureService;
import org.example.kalkulationsprogramm.service.FrontendUserProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(EmailController.class)
@AutoConfigureMockMvc(addFilters = false)
class EmailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjektDokumentRepository dokumentRepository;

    @MockBean
    private AnfrageDokumentRepository anfrageDokumentRepository;

    @MockBean
    private AnfrageRepository anfrageRepository;

    @MockBean
    private org.example.kalkulationsprogramm.repository.EmailRepository emailRepository;

    @MockBean
    private EmailAiService emailAiService;

    @MockBean
    private EmailSignatureService emailSignatureService;

    @MockBean
    private FrontendUserProfileService frontendUserProfileService;

    @MockBean
    private DateiSpeicherService dateiSpeicherService;

    @Nested
    @DisplayName("POST /api/email/beautify")
    class Beautify {

        @Test
        @DisplayName("Beautify gibt formatierten Body zurück")
        void beautifyErfolgreich() throws Exception {
            given(emailAiService.beautify("Hallo Test", null))
                    .willReturn("Sehr geehrte Damen und Herren, ...");

            EmailBeautifyRequest request = new EmailBeautifyRequest();
            request.setBody("Hallo Test");

            mockMvc.perform(post("/api/email/beautify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.body").value("Sehr geehrte Damen und Herren, ..."));
        }

        @Test
        @DisplayName("Leerer Body gibt leeren String zurück")
        void leererBodyGibtLeerenString() throws Exception {
            EmailBeautifyRequest request = new EmailBeautifyRequest();
            request.setBody("");

            mockMvc.perform(post("/api/email/beautify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.body").value(""));
        }

        @Test
        @DisplayName("Null Body gibt leeren String zurück")
        void nullBodyGibtLeerenString() throws Exception {
            EmailBeautifyRequest request = new EmailBeautifyRequest();

            mockMvc.perform(post("/api/email/beautify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.body").value(""));
        }

        @Test
        @DisplayName("AI-Fehler gibt 502 zurück")
        void aiFehlerGibt502() throws Exception {
            given(emailAiService.beautify(any(), any()))
                    .willThrow(new RuntimeException("AI unavailable"));

            EmailBeautifyRequest request = new EmailBeautifyRequest();
            request.setBody("Test");

            mockMvc.perform(post("/api/email/beautify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadGateway());
        }

        @Test
        @DisplayName("SQL Injection im Body wird als String behandelt")
        void sqlInjectionImBody() throws Exception {
            String sqlPayload = "'; DROP TABLE emails; --";
            given(emailAiService.beautify(sqlPayload, null)).willReturn(sqlPayload);

            EmailBeautifyRequest request = new EmailBeautifyRequest();
            request.setBody(sqlPayload);

            mockMvc.perform(post("/api/email/beautify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("XSS im Body")
        void xssImBody() throws Exception {
            String xssPayload = "<script>alert('xss')</script>";
            given(emailAiService.beautify(xssPayload, null)).willReturn(xssPayload);

            EmailBeautifyRequest request = new EmailBeautifyRequest();
            request.setBody(xssPayload);

            mockMvc.perform(post("/api/email/beautify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("XML Content-Type wird abgelehnt")
        void xmlContentTypeWirdAbgelehnt() throws Exception {
            mockMvc.perform(post("/api/email/beautify")
                            .contentType(MediaType.APPLICATION_XML)
                            .content("<request />"))
                    .andExpect(status().isUnsupportedMediaType());
        }
    }

    @Nested
    @DisplayName("POST /api/email/preview")
    class Preview {

        @Test
        @DisplayName("Unbekanntes Dokument und kein Anfrage gibt 404")
        void unbekanntesDokumentGibt404() throws Exception {
            given(dokumentRepository.findById(999L)).willReturn(Optional.empty());
            given(anfrageDokumentRepository.findById(999L)).willReturn(Optional.empty());

            EmailPreviewRequest request = new EmailPreviewRequest();
            request.setDokumentId(999L);

            mockMvc.perform(post("/api/email/preview")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/email/send")
    class Send {

        @Test
        @DisplayName("Unbekanntes Dokument gibt 404")
        void unbekanntesDokumentGibt404() throws Exception {
            given(dokumentRepository.findById(999L)).willReturn(Optional.empty());

            EmailSendRequest request = new EmailSendRequest();
            request.setDokumentId(999L);
            request.setRecipient("test@example.com");
            request.setSubject("Test");

            mockMvc.perform(post("/api/email/send")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/email/preview/anfrage")
    class PreviewAnfrage {

        @Test
        @DisplayName("Unbekanntes Anfragesdokument gibt 404")
        void unbekanntesAnfrageGibt404() throws Exception {
            given(anfrageDokumentRepository.findById(999L)).willReturn(Optional.empty());

            EmailPreviewRequest request = new EmailPreviewRequest();
            request.setDokumentId(999L);

            mockMvc.perform(post("/api/email/preview/anfrage")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/email/send/anfrage")
    class SendAnfrage {

        @Test
        @DisplayName("Unbekanntes Anfragesdokument gibt 404")
        void unbekanntesAnfrageGibt404() throws Exception {
            given(anfrageDokumentRepository.findById(999L)).willReturn(Optional.empty());

            EmailSendRequest request = new EmailSendRequest();
            request.setDokumentId(999L);
            request.setRecipient("test@example.com");

            mockMvc.perform(post("/api/email/send/anfrage")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/email/generate-reklamation")
    class GenerateReklamation {

        @Test
        @DisplayName("Erfolgreiche Generierung gibt JSON zurück")
        void generiereReklamationEmail() throws Exception {
            given(emailAiService.generateReklamationEmail(any(), any(), any()))
                    .willReturn("{\"subject\":\"Reklamation Ware\",\"body\":\"<p>Sehr geehrte Damen und Herren...</p>\"}");

            String requestJson = "{\"beschreibung\":\"Ware beschädigt\",\"lieferantName\":\"TestLieferant\",\"bildUrls\":[]}";

            mockMvc.perform(post("/api/email/generate-reklamation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Leere Beschreibung gibt 400 zurück")
        void leereBeschreibungGibt400() throws Exception {
            String requestJson = "{\"beschreibung\":\"\",\"lieferantName\":\"Test\"}";

            mockMvc.perform(post("/api/email/generate-reklamation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("AI-Fehler gibt 502 zurück")
        void aiFehlerGibt502() throws Exception {
            given(emailAiService.generateReklamationEmail(any(), any(), any()))
                    .willThrow(new RuntimeException("AI unavailable"));

            String requestJson = "{\"beschreibung\":\"Ware beschädigt\",\"lieferantName\":\"Test\",\"bildUrls\":[]}";

            mockMvc.perform(post("/api/email/generate-reklamation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadGateway());
        }

        @Test
        @DisplayName("SQL Injection in Beschreibung wird als String behandelt")
        void sqlInjectionInBeschreibung() throws Exception {
            String sqlPayload = "'; DROP TABLE reklamationen; --";
            given(emailAiService.generateReklamationEmail(any(), any(), any()))
                    .willReturn("{\"subject\":\"Reklamation\",\"body\":\"text\"}");

            String requestJson = objectMapper.writeValueAsString(
                    java.util.Map.of("beschreibung", sqlPayload, "lieferantName", "Test"));

            mockMvc.perform(post("/api/email/generate-reklamation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("XSS in Beschreibung wird als String behandelt")
        void xssInBeschreibung() throws Exception {
            String xssPayload = "<script>alert(1)</script>";
            given(emailAiService.generateReklamationEmail(any(), any(), any()))
                    .willReturn("{\"subject\":\"Reklamation\",\"body\":\"text\"}");

            String requestJson = objectMapper.writeValueAsString(
                    java.util.Map.of("beschreibung", xssPayload, "lieferantName", "Test"));

            mockMvc.perform(post("/api/email/generate-reklamation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Überlange Beschreibung gibt 400 zurück")
        void ueberlangeBeschreibungGibt400() throws Exception {
            String longText = "A".repeat(10001);
            String requestJson = objectMapper.writeValueAsString(
                    java.util.Map.of("beschreibung", longText, "lieferantName", "Test"));

            mockMvc.perform(post("/api/email/generate-reklamation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("XML Content-Type wird abgelehnt")
        void xmlContentTypeWirdAbgelehnt() throws Exception {
            mockMvc.perform(post("/api/email/generate-reklamation")
                            .contentType(MediaType.APPLICATION_XML)
                            .content("<request />"))
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        @DisplayName("Überlanger Lieferantname gibt 400")
        void ueberlangerLieferantNameGibt400() throws Exception {
            String longName = "A".repeat(201);
            String requestJson = objectMapper.writeValueAsString(
                    java.util.Map.of("beschreibung", "Test", "lieferantName", longName));

            mockMvc.perform(post("/api/email/generate-reklamation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest());
        }
    }
}
