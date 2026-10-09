package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.dto.Email.RebuildEmailThreadsResultDto;
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.EmailBlacklistRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.service.ContactService;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.EmailAutoAssignmentService;
import org.example.kalkulationsprogramm.service.EmailImportService;
import org.example.kalkulationsprogramm.service.EmailThreadService;
import org.example.kalkulationsprogramm.service.InquiryDetectionService;
import org.example.kalkulationsprogramm.service.SpamBayesService;
import org.example.kalkulationsprogramm.service.SpamFilterService;
import org.example.kalkulationsprogramm.service.SteuerberaterKontaktService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Hält fest, dass die Verlaufs-Bereinigung ({@code /api/emails/admin/rebuild-threads}) nur
 * für Admins erreichbar ist und ohne ausdrückliche Angabe nur einen Probelauf macht.
 */
@WebMvcTest(controllers = UnifiedEmailController.class)
@Import({ SecurityConfig.class, EmailThreadAdminSecurityTest.EchteFilterBeans.class })
@org.springframework.test.context.TestPropertySource(properties = "file.mail-attachment-dir=target/test-attachments")
class EmailThreadAdminSecurityTest {

    /** Echter Cloudflare-Filter: MockMvc initialisiert jeden Filter der Kette (siehe ProjektWartungControllerSecurityTest). */
    @org.springframework.boot.test.context.TestConfiguration
    static class EchteFilterBeans {
        @org.springframework.context.annotation.Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @MockBean private FrontendUserDetailsService frontendUserDetailsService;
    @MockBean private org.example.kalkulationsprogramm.service.EmailDraftService emailDraftService;
    @MockBean private org.example.kalkulationsprogramm.service.mail.SentMailArchiver sentMailArchiver;
    @MockBean private EmailRepository emailRepository;
    @MockBean private ProjektRepository projektRepository;
    @MockBean private AnfrageRepository anfrageRepository;
    @MockBean private LieferantenRepository lieferantenRepository;
    @MockBean private org.example.kalkulationsprogramm.service.EmailLieferantVerknuepfungService emailLieferantVerknuepfungService;
    @MockBean private KundeRepository kundeRepository;
    @MockBean private EmailAutoAssignmentService emailAutoAssignmentService;
    @MockBean private EmailImportService emailImportService;
    @MockBean private org.example.kalkulationsprogramm.service.EmailAttachmentProcessingService emailAttachmentProcessingService;
    @MockBean private SpamFilterService spamFilterService;
    @MockBean private InquiryDetectionService inquiryDetectionService;
    @MockBean private EmailBlacklistRepository emailBlacklistRepository;
    @MockBean private ProjektDokumentRepository projektDokumentRepository;
    @MockBean private AnfrageDokumentRepository anfrageDokumentRepository;
    @MockBean private DateiSpeicherService dateiSpeicherService;
    @MockBean private ContactService contactService;
    @MockBean private SpamBayesService spamBayesService;
    @MockBean private EmailThreadService emailThreadService;
    @MockBean private org.example.kalkulationsprogramm.service.SystemSettingsService systemSettingsService;
    @MockBean private org.example.kalkulationsprogramm.service.EmailAbsenderService emailAbsenderService;
    @MockBean private org.example.kalkulationsprogramm.service.FrontendUserProfileService frontendUserProfileService;
    @MockBean private SteuerberaterKontaktService steuerberaterKontaktService;
    @MockBean private org.example.kalkulationsprogramm.service.LieferantDokumentZugriffService lieferantDokumentZugriffService;

    @Test
    @DisplayName("Ohne Anmeldung kein Zugriff")
    void anonymWirdAbgewiesen() throws Exception {
        mockMvc.perform(post("/api/emails/admin/rebuild-threads").with(csrf()))
                .andExpect(status().isUnauthorized());
        verify(emailImportService, never()).bereinigeThreadVerknuepfungen(anyBoolean());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Normaler Benutzer darf nicht bereinigen")
    void normalerBenutzerWirdAbgewiesen() throws Exception {
        mockMvc.perform(post("/api/emails/admin/rebuild-threads").param("probelauf", "false").with(csrf()))
                .andExpect(status().isForbidden());
        verify(emailImportService, never()).bereinigeThreadVerknuepfungen(anyBoolean());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin bekommt ohne Angabe einen Probelauf")
    void adminStandardIstProbelauf() throws Exception {
        given(emailImportService.bereinigeThreadVerknuepfungen(true)).willReturn(new RebuildEmailThreadsResultDto(3215, 12, 33));

        mockMvc.perform(post("/api/emails/admin/rebuild-threads").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(3215))
                .andExpect(jsonPath("$.relinked").value(12))
                .andExpect(jsonPath("$.cleared").value(33));
        verify(emailImportService, never()).bereinigeThreadVerknuepfungen(false);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin speichert nur mit probelauf=false")
    void adminSpeichertMitProbelaufFalse() throws Exception {
        given(emailImportService.bereinigeThreadVerknuepfungen(false)).willReturn(new RebuildEmailThreadsResultDto(10, 1, 1));

        mockMvc.perform(post("/api/emails/admin/rebuild-threads").param("probelauf", "false").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cleared").value(1));
        verify(emailImportService).bereinigeThreadVerknuepfungen(false);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Ungültiger probelauf-Wert wird abgelehnt")
    void ungueltigerProbelaufWert() throws Exception {
        mockMvc.perform(post("/api/emails/admin/rebuild-threads").param("probelauf", "'; DROP TABLE email; --").with(csrf()))
                .andExpect(status().isBadRequest());
        verify(emailImportService, never()).bereinigeThreadVerknuepfungen(anyBoolean());
    }
}
