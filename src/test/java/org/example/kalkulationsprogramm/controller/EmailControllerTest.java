package org.example.kalkulationsprogramm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

    @MockBean
    private SystemSettingsService systemSettingsService;

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
}
