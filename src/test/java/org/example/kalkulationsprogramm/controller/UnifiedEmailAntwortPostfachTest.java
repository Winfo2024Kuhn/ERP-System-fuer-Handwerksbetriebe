package org.example.kalkulationsprogramm.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.EmailBlacklistRepository;
import org.example.kalkulationsprogramm.repository.EmailDraftRepository;
import org.example.kalkulationsprogramm.repository.EmailPostfachZuordnungRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.service.AusgangsmailService;
import org.example.kalkulationsprogramm.service.ContactService;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.EmailAttachmentProcessingService;
import org.example.kalkulationsprogramm.service.EmailAutoAssignmentService;
import org.example.kalkulationsprogramm.service.EmailDraftService;
import org.example.kalkulationsprogramm.service.EmailImportService;
import org.example.kalkulationsprogramm.service.EmailLieferantVerknuepfungService;
import org.example.kalkulationsprogramm.service.EmailThreadService;
import org.example.kalkulationsprogramm.service.FrontendUserProfileService;
import org.example.kalkulationsprogramm.service.InquiryDetectionService;
import org.example.kalkulationsprogramm.service.LieferantDokumentZugriffService;
import org.example.kalkulationsprogramm.service.PostfachService;
import org.example.kalkulationsprogramm.service.PostfachSichtbarkeit;
import org.example.kalkulationsprogramm.service.PostfachSichtbarkeitService;
import org.example.kalkulationsprogramm.service.PostfachVersandService;
import org.example.kalkulationsprogramm.service.SpamBayesService;
import org.example.kalkulationsprogramm.service.SpamFilterService;
import org.example.kalkulationsprogramm.service.SteuerberaterKontaktService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Festes Antwort-Postfach in der Detailansicht mit echtem {@link PostfachVersandService}: Das
 * Postfach für Rechnungen & Mahnungen ist ein reines Ausgangspostfach – Antworten und
 * Weiterleitungen gehen über das eigene Postfach, sonst über info@. Gilt auch für Admins.
 */
@WebMvcTest(controllers = UnifiedEmailController.class)
@Import({ SecurityConfig.class, UnifiedEmailAntwortPostfachTest.EchteFilterBeans.class,
        PostfachSichtbarkeitService.class, EmailThreadService.class, PostfachVersandService.class })
@TestPropertySource(properties = "file.mail-attachment-dir=target/test-attachments")
class UnifiedEmailAntwortPostfachTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class EchteFilterBeans {
        @org.springframework.context.annotation.Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @MockBean private FrontendUserDetailsService frontendUserDetailsService;
    @MockBean private EmailDraftService emailDraftService;
    @MockBean private EmailDraftRepository emailDraftRepository;
    @MockBean private org.example.kalkulationsprogramm.service.mail.SentMailArchiver sentMailArchiver;
    @MockBean private EmailRepository emailRepository;
    @MockBean private EmailAbsenderRepository postfachRepository;
    @MockBean private EmailPostfachZuordnungRepository zuordnungRepository;
    @MockBean private ProjektRepository projektRepository;
    @MockBean private AnfrageRepository anfrageRepository;
    @MockBean private LieferantenRepository lieferantenRepository;
    @MockBean private EmailLieferantVerknuepfungService emailLieferantVerknuepfungService;
    @MockBean private KundeRepository kundeRepository;
    @MockBean private EmailAutoAssignmentService emailAutoAssignmentService;
    @MockBean private EmailImportService emailImportService;
    @MockBean private EmailAttachmentProcessingService emailAttachmentProcessingService;
    @MockBean private SpamFilterService spamFilterService;
    @MockBean private InquiryDetectionService inquiryDetectionService;
    @MockBean private EmailBlacklistRepository emailBlacklistRepository;
    @MockBean private ProjektDokumentRepository projektDokumentRepository;
    @MockBean private AnfrageDokumentRepository anfrageDokumentRepository;
    @MockBean private DateiSpeicherService dateiSpeicherService;
    @MockBean private ContactService contactService;
    @MockBean private SpamBayesService spamBayesService;
    @MockBean private FrontendUserProfileService frontendUserProfileService;
    @MockBean private PostfachService postfachService;
    @MockBean private org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository frontendUserProfileRepository;
    @MockBean private org.example.kalkulationsprogramm.service.mail.PostfachZugangService postfachZugangService;
    @MockBean private org.example.kalkulationsprogramm.service.SystemSettingsService systemSettingsService;
    @MockBean private AusgangsmailService ausgangsmailService;
    @MockBean private SteuerberaterKontaktService steuerberaterKontaktService;
    @MockBean private LieferantDokumentZugriffService lieferantDokumentZugriffService;

    private EmailAbsender info;
    private EmailAbsender rechnungen;
    private EmailAbsender eigenes;

    @BeforeEach
    void daten() {
        Mockito.reset(emailRepository, postfachRepository, zuordnungRepository, frontendUserProfileService,
                frontendUserProfileRepository);
        info = postfach(1L, "info@example.com", true);
        rechnungen = postfach(2L, "rechnungen@example.com", false);
        rechnungen.setFuerGeschaeftsdokumente(true);
        eigenes = postfach(9L, "erika@example.com", false);
        given(postfachRepository.findByAktivTrueOrderBySortierungAscIdAsc()).willReturn(List.of(info, rechnungen, eigenes));
        given(postfachRepository.findFirstByHauptpostfachTrueOrderByIdAsc()).willReturn(Optional.of(info));
        given(lieferantDokumentZugriffService.sichtbareTypen(any(), any()))
                .willReturn(Optional.of(EnumSet.allOf(LieferantDokumentTyp.class)));
    }

    private static EmailAbsender postfach(Long id, String adresse, boolean haupt) {
        EmailAbsender p = new EmailAbsender();
        p.setId(id);
        p.setEmailAdresse(adresse);
        p.setHauptpostfach(haupt);
        p.setSichtbarFuerAlle(false);
        return p;
    }

    private void benutzer(String name, Long id, EmailAbsender eigenesPostfach) {
        FrontendUserProfile u = new FrontendUserProfile();
        u.setId(id);
        u.setDisplayName("Max Mustermann");
        u.setUsername(name);
        u.setEmailAbsender(eigenesPostfach);
        given(frontendUserProfileService.findByUsername(name)).willReturn(Optional.of(u));
        given(frontendUserProfileRepository.findById(id)).willReturn(Optional.of(u));
        if (eigenesPostfach != null) {
            given(postfachRepository.findIdsFreigegebenFuerBenutzer(id)).willReturn(List.of());
        }
    }

    private Email mail(Long id, EmailDirection richtung, EmailAbsender... postfaecher) {
        Email e = new Email();
        e.setId(id);
        e.setMessageId("<" + id + "@example.org>");
        e.setSubject("Rückfrage zur Rechnung");
        e.setFromAddress(richtung == EmailDirection.IN ? "kunde@example.org" : "rechnungen@example.com");
        e.setRecipient(richtung == EmailDirection.IN ? "rechnungen@example.com" : "kunde@example.org");
        e.setDirection(richtung);
        e.setZuordnungTyp(EmailZuordnungTyp.KEINE);
        e.setSentAt(LocalDateTime.of(2026, 10, 10, 9, 0));
        e.setAttachments(new ArrayList<>());
        long uid = 1;
        for (EmailAbsender p : postfaecher) {
            e.ordnePostfachZu(p, "INBOX", uid);
            e.getPostfachZuordnungen().get(e.getPostfachZuordnungen().size() - 1).setId(uid++);
        }
        given(emailRepository.findById(id)).willReturn(Optional.of(e));
        return e;
    }

    @Test
    @WithMockUser(username = "erika", roles = "USER")
    void mailNurInRechnungenAntwortUeberEigenes() throws Exception {
        benutzer("erika", 7L, eigenes);
        mail(40L, EmailDirection.IN, rechnungen);

        mockMvc.perform(get("/api/emails/40"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.antwortPostfach.emailAdresse").value("erika@example.com"));
    }

    @Test
    @WithMockUser(username = "erika", roles = "USER")
    void ohneEigenesUeberDasHauptpostfach() throws Exception {
        benutzer("erika", 7L, null);
        mail(40L, EmailDirection.IN, rechnungen);

        mockMvc.perform(get("/api/emails/40"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.antwortPostfach.emailAdresse").value("info@example.com"));
    }

    @Test
    @WithMockUser(username = "erika", roles = "USER")
    void mailInRechnungenUndInfoUeberInfo() throws Exception {
        benutzer("erika", 7L, eigenes);
        mail(41L, EmailDirection.IN, rechnungen, info);

        mockMvc.perform(get("/api/emails/41"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.antwortPostfach.emailAdresse").value("info@example.com"));
    }

    @Test
    @WithMockUser(username = "erika", roles = "USER")
    void eigeneGesendeteRechnungsMailUeberEigenes() throws Exception {
        benutzer("erika", 7L, eigenes);
        mail(42L, EmailDirection.OUT, rechnungen);

        mockMvc.perform(get("/api/emails/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.antwortPostfach.emailAdresse").value("erika@example.com"));
    }

    @Test
    @WithMockUser(username = "chefin", roles = "ADMIN")
    void adminGenauso() throws Exception {
        EmailAbsender chef = postfach(8L, "chef@example.com", false);
        benutzer("chefin", 3L, chef);
        mail(40L, EmailDirection.IN, rechnungen);
        mail(42L, EmailDirection.OUT, rechnungen);

        mockMvc.perform(get("/api/emails/40"))
                .andExpect(jsonPath("$.antwortPostfach.emailAdresse").value("chef@example.com"));
        mockMvc.perform(get("/api/emails/42"))
                .andExpect(jsonPath("$.antwortPostfach.emailAdresse").value("chef@example.com"));
    }

    @Test
    @WithMockUser(username = "chefin", roles = "ADMIN")
    void rechnungsPostfachZugleichHauptpostfachWieBisher() throws Exception {
        info.setFuerGeschaeftsdokumente(true);
        benutzer("chefin", 3L, postfach(8L, "chef@example.com", false));
        mail(43L, EmailDirection.IN, info);

        mockMvc.perform(get("/api/emails/43"))
                .andExpect(jsonPath("$.antwortPostfach.emailAdresse").value("info@example.com"));
    }

    // ---- Auslaufendes Postfach (alte T-Online-Adresse) ----

    @Test
    @WithMockUser(username = "erika", roles = "USER")
    void mailNurInAuslaufendemPostfachAntwortUeberInfo() throws Exception {
        EmailAbsender tonline = postfach(6L, "betrieb@t-online.de", false);
        tonline.setLaeuftAus(true);
        tonline.setSichtbarFuerAlle(true);
        given(postfachRepository.findByAktivTrueOrderBySortierungAscIdAsc()).willReturn(List.of(info, rechnungen, eigenes, tonline));
        benutzer("erika", 7L, eigenes);
        mail(50L, EmailDirection.IN, tonline);
        mail(51L, EmailDirection.IN, tonline, info);

        mockMvc.perform(get("/api/emails/50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.antwortPostfach.emailAdresse").value("info@example.com"));
        mockMvc.perform(get("/api/emails/51"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.antwortPostfach.emailAdresse").value("info@example.com"));
    }
}
