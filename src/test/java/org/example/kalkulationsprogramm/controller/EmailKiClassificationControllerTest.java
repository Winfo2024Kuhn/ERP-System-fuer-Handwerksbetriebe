package org.example.kalkulationsprogramm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.service.EmailClassificationGeminiClient;
import org.example.kalkulationsprogramm.service.EmailKiClassificationService;
import org.example.kalkulationsprogramm.service.EmailKiClassificationService.ClassificationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmailKiClassificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class EmailKiClassificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmailClassificationGeminiClient geminiClient;

    @MockBean
    private EmailKiClassificationService classificationService;

    @MockBean
    private EmailRepository emailRepository;

    @MockBean
    private ProjektRepository projektRepository;

    @MockBean
    private AnfrageRepository anfrageRepository;

    @Test
    void getStatus_returnsGeminiEnabledState() throws Exception {
        when(geminiClient.isEnabled()).thenReturn(true);

        mockMvc.perform(get("/api/email-ki/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("gemini"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void getStatus_geminiMissingKey_returnsDisabled() throws Exception {
        when(geminiClient.isEnabled()).thenReturn(false);

        mockMvc.perform(get("/api/email-ki/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void classifyEmail_successfulClassification_returnsResult() throws Exception {
        Email testEmail = createTestEmail(100L, "test@example.com", "Montage Geländer");
        Projekt testProjekt = createTestProjekt(42L, "Balkongeländer Musterstraße");

        when(emailRepository.findById(100L)).thenReturn(Optional.of(testEmail));
        when(projektRepository.findByKundenEmail("test@example.com")).thenReturn(List.of(testProjekt));
        when(anfrageRepository.findByKundenEmail("test@example.com")).thenReturn(List.of());
        when(classificationService.classify(any(), anyList(), anyList()))
                .thenReturn(new ClassificationResult(
                        EmailZuordnungTyp.PROJEKT, 42L, 0.85, "Betreff passt", "PROJEKT_42"));

        mockMvc.perform(post("/api/email-ki/classify/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailId").value(100))
                .andExpect(jsonPath("$.candidateCount").value(1))
                .andExpect(jsonPath("$.result.key").value("PROJEKT_42"))
                .andExpect(jsonPath("$.result.confidence").value(0.85))
                .andExpect(jsonPath("$.result.assigned").value(true));
    }

    @Test
    void classifyEmail_emailNotFound_returns404() throws Exception {
        when(emailRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/email-ki/classify/999"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void classifyEmail_noCandidates_returnsNoCandidates() throws Exception {
        Email testEmail = createTestEmail(100L, "unknown@example.com", "Test");

        when(emailRepository.findById(100L)).thenReturn(Optional.of(testEmail));
        when(projektRepository.findByKundenEmail("unknown@example.com")).thenReturn(List.of());
        when(anfrageRepository.findByKundenEmail("unknown@example.com")).thenReturn(List.of());

        mockMvc.perform(post("/api/email-ki/classify/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("NO_CANDIDATES"));
    }

    @Test
    void classifyEmail_noFromAddress_returnsBadRequest() throws Exception {
        Email testEmail = createTestEmail(100L, null, "Test");

        when(emailRepository.findById(100L)).thenReturn(Optional.of(testEmail));

        mockMvc.perform(post("/api/email-ki/classify/100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void debugPrompt_returnsPromptDetails() throws Exception {
        Email testEmail = createTestEmail(100L, "test@example.com", "Test Betreff");
        Projekt testProjekt = createTestProjekt(42L, "Testprojekt");

        when(emailRepository.findById(100L)).thenReturn(Optional.of(testEmail));
        when(projektRepository.findByKundenEmail("test@example.com")).thenReturn(List.of(testProjekt));
        when(anfrageRepository.findByKundenEmail("test@example.com")).thenReturn(List.of());
        when(classificationService.buildUserPrompt(any(), anyList(), anyList()))
                .thenReturn("Test-Prompt Inhalt");

        mockMvc.perform(get("/api/email-ki/debug-prompt/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailId").value(100))
                .andExpect(jsonPath("$.prompt").value("Test-Prompt Inhalt"))
                .andExpect(jsonPath("$.candidateCount").value(1));
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private Email createTestEmail(Long id, String from, String subject) {
        Email email = new Email();
        email.setId(id);
        email.setFromAddress(from);
        email.setSubject(subject);
        email.setBody("Test-Body Inhalt der Email");
        email.setSentAt(LocalDateTime.of(2026, 3, 15, 10, 0));
        email.setDirection(EmailDirection.IN);
        return email;
    }

    private Projekt createTestProjekt(Long id, String bauvorhaben) {
        Projekt p = new Projekt();
        p.setId(id);
        p.setBauvorhaben(bauvorhaben);
        p.setAnlegedatum(LocalDate.of(2026, 2, 1));
        return p;
    }
}
