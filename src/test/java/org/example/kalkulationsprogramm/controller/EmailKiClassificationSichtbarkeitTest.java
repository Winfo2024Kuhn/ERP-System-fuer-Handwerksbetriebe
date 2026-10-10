package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.EmailPostfachZuordnungRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.service.EmailClassificationGeminiClient;
import org.example.kalkulationsprogramm.service.EmailKiClassificationService;
import org.example.kalkulationsprogramm.service.FrontendUserProfileService;
import org.example.kalkulationsprogramm.service.PostfachSichtbarkeitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * KI-Klassifizierung mit echter SecurityConfig und echtem Sichtbarkeits-Service: Mails aus
 * Postfächern, die der Benutzer nicht sieht, gibt es für ihn nicht (404) – sonst könnte die KI
 * eine fremde Mail einem Projekt zuordnen und damit lesbar machen.
 */
@WebMvcTest(controllers = EmailKiClassificationController.class)
@Import({ SecurityConfig.class, EmailKiClassificationSichtbarkeitTest.EchteFilterBeans.class,
        PostfachSichtbarkeitService.class })
class EmailKiClassificationSichtbarkeitTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class EchteFilterBeans {
        @org.springframework.context.annotation.Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @MockBean private FrontendUserDetailsService frontendUserDetailsService;
    @MockBean private EmailClassificationGeminiClient geminiClient;
    @MockBean private EmailKiClassificationService classificationService;
    @MockBean private EmailRepository emailRepository;
    @MockBean private ProjektRepository projektRepository;
    @MockBean private AnfrageRepository anfrageRepository;
    @MockBean private EmailAbsenderRepository postfachRepository;
    @MockBean private EmailPostfachZuordnungRepository zuordnungRepository;
    @MockBean private FrontendUserProfileService frontendUserProfileService;

    @BeforeEach
    void daten() {
        Mockito.reset(emailRepository, classificationService, projektRepository, anfrageRepository, postfachRepository);
        EmailAbsender info = postfach(1L, "info@example.com", true, true);
        EmailAbsender max = postfach(3L, "max@example.com", false, false);
        given(postfachRepository.findByAktivTrueOrderBySortierungAscIdAsc()).willReturn(List.of(info, max));
        given(emailRepository.findById(10L)).willReturn(Optional.of(mail(10L, info)));
        given(emailRepository.findById(20L)).willReturn(Optional.of(mail(20L, max)));
        given(projektRepository.findByKundenEmail(anyString())).willReturn(List.of());
        given(anfrageRepository.findByKundenEmail(anyString())).willReturn(List.of());
    }

    private static EmailAbsender postfach(Long id, String adresse, boolean haupt, boolean fuerAlle) {
        EmailAbsender p = new EmailAbsender();
        p.setId(id);
        p.setEmailAdresse(adresse);
        p.setHauptpostfach(haupt);
        p.setSichtbarFuerAlle(fuerAlle);
        return p;
    }

    private static Email mail(Long id, EmailAbsender postfach) {
        Email e = new Email();
        e.setId(id);
        e.setDirection(EmailDirection.IN);
        e.setFromAddress("kunde@example.org");
        e.setSubject("Anfrage Treppe");
        e.ordnePostfachZu(postfach, "INBOX", id);
        return e;
    }

    @Test
    void ohneAnmeldung401() throws Exception {
        mockMvc.perform(post("/api/email-ki/classify/10").with(csrf())).andExpect(status().isUnauthorized());
        verifyNoInteractions(emailRepository);
    }

    @Test
    @WithMockUser(username = "erika", roles = "USER")
    void ohneCsrf403() throws Exception {
        mockMvc.perform(post("/api/email-ki/classify-and-assign/10")).andExpect(status().isForbidden());
        verifyNoInteractions(emailRepository);
    }

    @Test
    @WithMockUser(username = "erika", roles = "USER")
    void fremdeMail404() throws Exception {
        given(zuordnungRepository.findEmailIdsNurInAnderenPostfaechern(Set.of(1L))).willReturn(Set.of(20L));

        mockMvc.perform(post("/api/email-ki/classify/20").with(csrf())).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/email-ki/classify-and-assign/20").with(csrf())).andExpect(status().isNotFound());
        verify(classificationService, never()).classify(any(), any(), any());
        verify(emailRepository, never()).save(any());
    }

    @Test
    @WithMockUser(username = "erika", roles = "USER")
    void sichtbareMailWirdKlassifiziert() throws Exception {
        mockMvc.perform(post("/api/email-ki/classify/10").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("NO_CANDIDATES"));
    }

    @Test
    @WithMockUser(username = "chefin", roles = "ADMIN")
    void adminSiehtAuchFremdeMail() throws Exception {
        mockMvc.perform(post("/api/email-ki/classify/20").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("NO_CANDIDATES"));
    }

    @Test
    @WithMockUser(username = "chefin", roles = "ADMIN")
    void debugPromptGibtEsAuchFuerAdminsNichtMehr() throws Exception {
        mockMvc.perform(get("/api/email-ki/debug-prompt/10")).andExpect(status().isNotFound());
        verify(emailRepository, never()).findById(any());
    }

    // ---- Alle Zweige der geänderten Endpunkte (sichtbare Mail) ----

    private org.example.kalkulationsprogramm.domain.Projekt projekt(Long id) {
        org.example.kalkulationsprogramm.domain.Projekt p = new org.example.kalkulationsprogramm.domain.Projekt();
        p.setId(id);
        p.setBauvorhaben("Treppe Musterstraße");
        return p;
    }

    private org.example.kalkulationsprogramm.domain.Anfrage anfrage(Long id) {
        org.example.kalkulationsprogramm.domain.Anfrage a = new org.example.kalkulationsprogramm.domain.Anfrage();
        a.setId(id);
        return a;
    }

    @Test
    @WithMockUser(username = "erika", roles = "USER")
    void ohneAbsender400() throws Exception {
        for (String absender : java.util.Arrays.asList(null, "  ")) {
            Email ohne = mail(11L, postfach(1L, "info@example.com", true, true));
            ohne.setFromAddress(absender);
            given(emailRepository.findById(11L)).willReturn(Optional.of(ohne));

            mockMvc.perform(post("/api/email-ki/classify/11").with(csrf())).andExpect(status().isBadRequest());
            mockMvc.perform(post("/api/email-ki/classify-and-assign/11").with(csrf())).andExpect(status().isBadRequest());
        }
    }

    @Test
    @WithMockUser(username = "erika", roles = "USER")
    void klassifizierenMitKandidaten() throws Exception {
        given(projektRepository.findByKundenEmail(anyString())).willReturn(List.of(projekt(5L)));
        given(anfrageRepository.findByKundenEmail(anyString())).willReturn(List.of(anfrage(6L)));
        given(classificationService.classify(any(), any(), any())).willReturn(
                EmailKiClassificationService.ClassificationResult.none("passt nicht"));

        mockMvc.perform(post("/api/email-ki/classify/10").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateCount").value(2))
                .andExpect(jsonPath("$.result.entityId").value("null"));

        given(classificationService.classify(any(), any(), any())).willReturn(
                new EmailKiClassificationService.ClassificationResult(
                        org.example.kalkulationsprogramm.domain.EmailZuordnungTyp.PROJEKT, 5L, 0.9, null, "PROJEKT_5"));
        mockMvc.perform(post("/api/email-ki/classify/10").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.entityId").value(5));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(nullValues = "NULL", value = {
            "PROJEKT, 5, 0.9, true", "PROJEKT, 99, 0.9, true", "ANFRAGE, 6, 0.8, true", "ANFRAGE, 99, 0.8, true",
            "PROJEKT, 5, 0.3, false", "KEINE, NULL, 0.0, false", "LIEFERANT, 7, 0.9, false" })
    @WithMockUser(username = "erika", roles = "USER")
    void zuordnenJeErgebnis(String typ, Long id, double sicherheit, boolean angewendet) throws Exception {
        given(projektRepository.findByKundenEmail(anyString())).willReturn(List.of(projekt(5L)));
        given(anfrageRepository.findByKundenEmail(anyString())).willReturn(List.of(anfrage(6L)));
        given(classificationService.classify(any(), any(), any())).willReturn(new EmailKiClassificationService.ClassificationResult(
                org.example.kalkulationsprogramm.domain.EmailZuordnungTyp.valueOf(typ), id, sicherheit, null, typ));

        mockMvc.perform(post("/api/email-ki/classify-and-assign/10").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(angewendet));
    }
}
