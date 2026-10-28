package org.example.kalkulationsprogramm.controller;

import tools.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.dto.Email.EmailBeautifyRequest;
import org.example.kalkulationsprogramm.dto.Email.EmailPreviewRequest;
import org.example.kalkulationsprogramm.dto.Email.EmailSchreibstil;
import org.example.kalkulationsprogramm.dto.Email.EmailSendRequest;
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.DokumentFreigabeService;
import org.example.kalkulationsprogramm.service.EmailAbsenderService;
import org.example.kalkulationsprogramm.service.EmailAiService;
import org.example.kalkulationsprogramm.service.EmailSignatureService;
import org.example.kalkulationsprogramm.service.FrontendUserProfileService;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmailController.class)
@AutoConfigureMockMvc(addFilters = false)
class EmailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private org.example.kalkulationsprogramm.service.mail.SentMailArchiver sentMailArchiver;

    @MockitoBean
    private org.example.kalkulationsprogramm.service.FirmeninformationService firmeninformationService;

    @MockitoBean
    private ProjektDokumentRepository dokumentRepository;

    @MockitoBean
    private AnfrageDokumentRepository anfrageDokumentRepository;

    @MockitoBean
    private AnfrageRepository anfrageRepository;

    @MockitoBean
    private org.example.kalkulationsprogramm.repository.EmailRepository emailRepository;

    @MockitoBean
    private EmailAiService emailAiService;

    @MockitoBean
    private EmailSignatureService emailSignatureService;

    @MockitoBean
    private FrontendUserProfileService frontendUserProfileService;

    @MockitoBean
    private DateiSpeicherService dateiSpeicherService;

    @MockitoBean
    private SystemSettingsService systemSettingsService;

    @MockitoBean
    private DokumentFreigabeService dokumentFreigabeService;

    @MockitoBean
    private EmailAbsenderService emailAbsenderService;

    @Nested
    @DisplayName("POST /api/email/beautify")
    class Beautify {

        @Test
        @DisplayName("Beautify gibt formatierten Body zurück")
        void beautifyErfolgreich() throws Exception {
            given(emailAiService.beautify("Hallo Test", null, EmailSchreibstil.RECHTSCHREIBUNG))
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
            given(emailAiService.beautify(any(), any(), any()))
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
            given(emailAiService.beautify(sqlPayload, null, EmailSchreibstil.RECHTSCHREIBUNG)).willReturn(sqlPayload);

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
            given(emailAiService.beautify(xssPayload, null, EmailSchreibstil.RECHTSCHREIBUNG)).willReturn(xssPayload);

            EmailBeautifyRequest request = new EmailBeautifyRequest();
            request.setBody(xssPayload);

            mockMvc.perform(post("/api/email/beautify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Gewählter Stil wird an die KI weitergegeben (Groß-/Kleinschreibung egal)")
        void stilWirdWeitergegeben() throws Exception {
            given(emailAiService.beautify("hi max, passt morgen?", "Hallo Max, wann kommt ihr?", EmailSchreibstil.VERLAUF))
                    .willReturn("Hi Max, passt dir morgen?");

            EmailBeautifyRequest request = new EmailBeautifyRequest();
            request.setBody("hi max, passt morgen?");
            request.setContext("Hallo Max, wann kommt ihr?");
            request.setStil("verlauf");

            mockMvc.perform(post("/api/email/beautify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.body").value("Hi Max, passt dir morgen?"));
        }

        @Test
        @DisplayName("Unbekannter Stil gibt 400 mit Meldung")
        void unbekannterStilGibt400() throws Exception {
            EmailBeautifyRequest request = new EmailBeautifyRequest();
            request.setBody("Test");
            request.setStil("'; DROP TABLE emails; --");

            mockMvc.perform(post("/api/email/beautify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Unbekannter Schreibstil."));
        }

        @Test
        @DisplayName("Verlauf ohne Verlauf gibt 400 statt 502")
        void verlaufOhneKontextGibt400() throws Exception {
            given(emailAiService.beautify("Test", null, EmailSchreibstil.VERLAUF))
                    .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Kein Mail-Verlauf vorhanden – bitte einen anderen Stil wählen."));

            EmailBeautifyRequest request = new EmailBeautifyRequest();
            request.setBody("Test");
            request.setStil("VERLAUF");

            mockMvc.perform(post("/api/email/beautify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Kein Mail-Verlauf vorhanden – bitte einen anderen Stil wählen."));
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
    @DisplayName("GET /api/email/from-addresses")
    class FromAddresses {

        @Test
        @DisplayName("Liefert aktive Absender ohne User-Parameter")
        void liefertAktiveAbsender() throws Exception {
            given(emailAbsenderService.getPrioritizedFromAddresses(isNull()))
                    .willReturn(List.of("info@example.com", "buchhaltung@example.com"));

            mockMvc.perform(get("/api/email/from-addresses"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0]").value("info@example.com"))
                    .andExpect(jsonPath("$[1]").value("buchhaltung@example.com"));
        }

        @Test
        @DisplayName("Delegiert frontendUserId an Service")
        void delegiertFrontendUserId() throws Exception {
            given(emailAbsenderService.getPrioritizedFromAddresses(15L))
                    .willReturn(List.of("user@example.com", "info@example.com"));

            mockMvc.perform(get("/api/email/from-addresses?frontendUserId=15"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0]").value("user@example.com"))
                    .andExpect(jsonPath("$[1]").value("info@example.com"));
        }
    }
}
