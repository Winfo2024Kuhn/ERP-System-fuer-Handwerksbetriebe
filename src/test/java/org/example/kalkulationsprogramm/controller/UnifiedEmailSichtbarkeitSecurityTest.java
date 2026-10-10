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
 * E-Mail-Center mit echter SecurityConfig und echtem Sichtbarkeits-Service: Erika (Benutzerin)
 * sieht info@ (Hauptpostfach), aber nicht max@ (nur bestimmte). Admins sehen alles.
 */
@WebMvcTest(controllers = UnifiedEmailController.class)
@Import({ SecurityConfig.class, UnifiedEmailSichtbarkeitSecurityTest.EchteFilterBeans.class,
        PostfachSichtbarkeitService.class, EmailThreadService.class })
@TestPropertySource(properties = "file.mail-attachment-dir=target/test-attachments")
class UnifiedEmailSichtbarkeitSecurityTest {

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
    @MockBean private PostfachVersandService postfachVersandService;
    @MockBean private AusgangsmailService ausgangsmailService;
    @MockBean private SteuerberaterKontaktService steuerberaterKontaktService;
    @MockBean private LieferantDokumentZugriffService lieferantDokumentZugriffService;

    private static final long INFO_MAIL = 10L;
    private static final long FREMDE_MAIL = 20L;
    private static final long FREMDE_PROJEKT_MAIL = 30L;

    private EmailAbsender info;
    private EmailAbsender max;
    private Email infoMail;
    private Email fremdeMail;
    private Email fremdeProjektMail;

    @BeforeEach
    void daten() {
        // Verschachtelte Testklassen teilen den Kontext – Mocks vor jedem Test leeren.
        Mockito.reset(emailRepository, postfachRepository, zuordnungRepository, postfachService, postfachVersandService,
                ausgangsmailService, emailBlacklistRepository, emailImportService, frontendUserProfileService,
                projektRepository, emailDraftRepository, spamBayesService, emailAutoAssignmentService,
                lieferantDokumentZugriffService, anfrageRepository, lieferantenRepository);
        info = postfach(1L, "info@example.com", true, true);
        max = postfach(3L, "max@example.com", false, false);
        given(postfachRepository.findByAktivTrueOrderBySortierungAscIdAsc()).willReturn(List.of(info, max));

        FrontendUserProfile erika = new FrontendUserProfile();
        erika.setId(7L);
        erika.setDisplayName("Erika Mustermann");
        erika.setUsername("erika");
        given(frontendUserProfileService.findByUsername("erika")).willReturn(Optional.of(erika));

        infoMail = mail(INFO_MAIL, "Anfrage Treppe", info);
        fremdeMail = mail(FREMDE_MAIL, "Persönlich an Max", max);
        fremdeProjektMail = mail(FREMDE_PROJEKT_MAIL, "Aufmaß Geländer", max);
        Projekt projekt = new Projekt();
        projekt.setId(5L);
        projekt.setBauvorhaben("Geländer Musterstraße");
        fremdeProjektMail.setProjekt(projekt);
        fremdeProjektMail.setZuordnungTyp(EmailZuordnungTyp.PROJEKT);
        for (Email e : List.of(infoMail, fremdeMail, fremdeProjektMail)) {
            given(emailRepository.findById(e.getId())).willReturn(Optional.of(e));
        }
        // Erika sieht nur info@ → die Mails aus max@ sind verborgen.
        given(zuordnungRepository.findEmailIdsNurInAnderenPostfaechern(Set.of(1L)))
                .willReturn(Set.of(FREMDE_MAIL, FREMDE_PROJEKT_MAIL));

        given(lieferantDokumentZugriffService.sichtbareTypen(any(), any()))
                .willReturn(Optional.of(EnumSet.allOf(LieferantDokumentTyp.class)));
        given(lieferantDokumentZugriffService.istAnhangSichtbar(any(), any())).willReturn(true);
    }

    private static EmailAbsender postfach(Long id, String adresse, boolean haupt, boolean fuerAlle) {
        EmailAbsender p = new EmailAbsender();
        p.setId(id);
        p.setEmailAdresse(adresse);
        p.setHauptpostfach(haupt);
        p.setSichtbarFuerAlle(fuerAlle);
        return p;
    }

    private static Email mail(Long id, String betreff, EmailAbsender postfach) {
        Email e = new Email();
        e.setId(id);
        e.setMessageId("<" + id + "@example.org>");
        e.setSubject(betreff);
        e.setFromAddress("kunde@example.org");
        e.setRecipient(postfach.getEmailAdresse());
        e.setDirection(EmailDirection.IN);
        e.setZuordnungTyp(EmailZuordnungTyp.KEINE);
        e.setSentAt(LocalDateTime.of(2026, 10, 10, 9, 0).minusMinutes(id));
        e.setAttachments(new ArrayList<>());
        e.ordnePostfachZu(postfach, "INBOX", id);
        return e;
    }

    private static MockMultipartFile dto(String json) {
        return new MockMultipartFile("dto", "", "application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("Anmeldung und CSRF")
    class Zugriff {

        @Test
        void ohneAnmeldung401() throws Exception {
            mockMvc.perform(get("/api/emails/inbox")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/emails/" + INFO_MAIL)).andExpect(status().isUnauthorized());
            verify(emailRepository, never()).findById(any());
        }

        @Test
        @WithMockUser(username = "erika", roles = "USER")
        void schreibenOhneCsrf403() throws Exception {
            mockMvc.perform(post("/api/emails/" + INFO_MAIL + "/mark-read")).andExpect(status().isForbidden());
            mockMvc.perform(post("/api/emails/mark-all-read").param("folder", "inbox")).andExpect(status().isForbidden());
            verify(emailRepository, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("Einzelne Mail")
    @WithMockUser(username = "erika", roles = "USER")
    class EinzelneMail {

        @Test
        void eigeneSichtbareMail200() throws Exception {
            mockMvc.perform(get("/api/emails/" + INFO_MAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.subject").value("Anfrage Treppe"));
        }

        @Test
        void fremdeMail404() throws Exception {
            mockMvc.perform(get("/api/emails/" + FREMDE_MAIL)).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/emails/" + FREMDE_MAIL + "/thread")).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/emails/" + FREMDE_MAIL + "/attachments/1")).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/emails/" + FREMDE_MAIL + "/attachments/download-all")).andExpect(status().isNotFound());
        }

        @Test
        void fremdeMailBearbeiten404() throws Exception {
            mockMvc.perform(post("/api/emails/" + FREMDE_MAIL + "/mark-read").with(csrf())).andExpect(status().isNotFound());
            mockMvc.perform(post("/api/emails/" + FREMDE_MAIL + "/toggle-star").with(csrf())).andExpect(status().isNotFound());
            mockMvc.perform(post("/api/emails/" + FREMDE_MAIL + "/unassign").with(csrf())).andExpect(status().isNotFound());
            mockMvc.perform(post("/api/emails/" + FREMDE_MAIL + "/assign/projekt/5").with(csrf())).andExpect(status().isNotFound());
            mockMvc.perform(post("/api/emails/" + FREMDE_MAIL + "/mark-spam").with(csrf())).andExpect(status().isNotFound());
            mockMvc.perform(post("/api/emails/" + FREMDE_MAIL + "/mark-not-spam").with(csrf())).andExpect(status().isNotFound());
            mockMvc.perform(post("/api/emails/" + FREMDE_MAIL + "/block-sender").with(csrf())).andExpect(status().isNotFound());
            mockMvc.perform(delete("/api/emails/" + FREMDE_MAIL).with(csrf())).andExpect(status().isNotFound());
            verify(emailRepository, never()).save(any());
            verify(emailBlacklistRepository, never()).save(any());
        }

        @Test
        void endgueltigLoeschenFremderMail404() throws Exception {
            given(emailRepository.findByIdForUpdate(FREMDE_MAIL)).willReturn(Optional.of(fremdeMail));

            mockMvc.perform(delete("/api/emails/" + FREMDE_MAIL + "/permanent").with(csrf()))
                    .andExpect(status().isNotFound());
            verify(emailRepository, never()).delete(any());
            verify(emailImportService, never()).deleteEmailFromServer(any());
        }

        @Test
        void projektMailDarfGelesenAberNichtBearbeitetWerden() throws Exception {
            mockMvc.perform(get("/api/emails/" + FREMDE_PROJEKT_MAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.projektId").value(5));
            mockMvc.perform(get("/api/emails/" + FREMDE_PROJEKT_MAIL + "/thread"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.emails", hasSize(1)));
            mockMvc.perform(post("/api/emails/" + FREMDE_PROJEKT_MAIL + "/mark-read").with(csrf()))
                    .andExpect(status().isNotFound());
            mockMvc.perform(multipart("/api/emails/" + FREMDE_PROJEKT_MAIL + "/reply").file(dto(
                    "{\"recipients\":[\"kunde@example.org\"],\"subject\":\"AW\",\"body\":\"b\"}")).with(csrf()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void verlaufZeigtNurSichtbareMails() throws Exception {
            // Max hat auf die info@-Mail aus seinem eigenen Postfach geantwortet.
            fremdeMail.setParentEmail(infoMail);
            infoMail.getReplies().add(fremdeMail);
            given(emailDraftRepository.findByReplyEmailIdIn(any())).willReturn(new ArrayList<>());

            mockMvc.perform(get("/api/emails/" + INFO_MAIL + "/thread"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.emails", hasSize(1)))
                    .andExpect(jsonPath("$.emails[0].id").value(INFO_MAIL));
        }

        @Test
        void antwortAufFremdeMail404() throws Exception {
            mockMvc.perform(multipart("/api/emails/" + FREMDE_MAIL + "/reply").file(dto(
                    "{\"recipients\":[\"kunde@example.org\"],\"subject\":\"AW\",\"body\":\"b\"}")).with(csrf()))
                    .andExpect(status().isNotFound());
            verify(postfachVersandService, never()).versandUeber(any());
        }

        @Test
        void ungueltigeIds404() throws Exception {
            given(emailRepository.findById(Long.MAX_VALUE)).willReturn(Optional.empty());
            mockMvc.perform(get("/api/emails/" + Long.MAX_VALUE)).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/emails/0")).andExpect(status().isNotFound());
            mockMvc.perform(post("/api/emails/-1/mark-read").with(csrf())).andExpect(status().isNotFound());
            mockMvc.perform(post("/api/emails/" + Long.MAX_VALUE + "/toggle-star").with(csrf())).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Listen, Zähler, Massenaktionen")
    @WithMockUser(username = "erika", roles = "USER")
    class Listen {

        @Test
        void posteingangOhneFremdeMails() throws Exception {
            given(emailRepository.findInboxFiltered()).willReturn(List.of(infoMail, fremdeMail));

            mockMvc.perform(get("/api/emails/inbox"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(INFO_MAIL));
        }

        @Test
        void sucheMitAngriffstextenBleibtGefiltert() throws Exception {
            given(emailRepository.searchGlobal(anyString())).willReturn(List.of(fremdeMail, infoMail));

            mockMvc.perform(get("/api/emails/search").param("q", "'; DROP TABLE email; --"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(INFO_MAIL));
            mockMvc.perform(get("/api/emails/search").param("q", "<script>alert(1)</script>"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
            verify(emailRepository).searchGlobal("'; DROP TABLE email; --");
        }

        @Test
        void projektReiterUngefiltert() throws Exception {
            Projekt projekt = fremdeProjektMail.getProjekt();
            given(projektRepository.findById(5L)).willReturn(Optional.of(projekt));
            given(emailRepository.findByProjektOrderBySentAtDesc(projekt)).willReturn(List.of(fremdeProjektMail));

            mockMvc.perform(get("/api/emails/projekt/5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(FREMDE_PROJEKT_MAIL));
        }

        @Test
        void zaehlerNurUeberSichtbare() throws Exception {
            given(emailRepository.findInboxFiltered()).willReturn(List.of(infoMail, fremdeMail));
            given(emailRepository.findProjectEmails()).willReturn(List.of(fremdeProjektMail));

            mockMvc.perform(get("/api/emails/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.inboxTotal").value(1))
                    .andExpect(jsonPath("$.inboxCount").value(1))
                    .andExpect(jsonPath("$.projectTotal").value(0))
                    .andExpect(jsonPath("$.projectCount").value(0));
            // Mit Filter wird aus den Listen gezählt, nicht über die ungefilterten Zähl-Abfragen.
            verify(emailRepository, never()).countProjectEmailsUnread();
        }

        @Test
        void alleGelesenNurSichtbare() throws Exception {
            given(emailRepository.findInboxFiltered()).willReturn(List.of(infoMail, fremdeMail));

            mockMvc.perform(post("/api/emails/mark-all-read").param("folder", "inbox").with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.updated").value(1));
            org.assertj.core.api.Assertions.assertThat(infoMail.isRead()).isTrue();
            org.assertj.core.api.Assertions.assertThat(fremdeMail.isRead()).isFalse();
        }

        @Test
        void massenaktionUebergehtFremdeMails() throws Exception {
            given(emailRepository.findAllById(List.of(INFO_MAIL, FREMDE_MAIL))).willReturn(List.of(infoMail, fremdeMail));

            mockMvc.perform(post("/api/emails/bulk/move-to-folder").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ids\":[" + INFO_MAIL + "," + FREMDE_MAIL + "],\"targetFolder\":\"trash\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.moved").value(1));
            org.assertj.core.api.Assertions.assertThat(fremdeMail.getDeletedAt()).isNull();
            org.assertj.core.api.Assertions.assertThat(infoMail.getDeletedAt()).isNotNull();
        }

        @Test
        void absenderAuswahlNurMitSichtbarenPostfaechern() throws Exception {
            given(postfachService.absenderAuswahl(any(), any())).willReturn(List.of());

            mockMvc.perform(get("/api/emails/absender-postfaecher")).andExpect(status().isOk());

            ArgumentCaptor<PostfachSichtbarkeit> sicht = ArgumentCaptor.forClass(PostfachSichtbarkeit.class);
            verify(postfachService).absenderAuswahl(isNull(), sicht.capture());
            org.assertj.core.api.Assertions.assertThat(sicht.getValue().sichtbarePostfachIds()).containsExactly(1L);
        }
    }

    @Nested
    @DisplayName("Senden")
    @WithMockUser(username = "erika", roles = "USER")
    class Senden {

        private void versandKlappt() throws Exception {
            org.example.email.EmailService dienst = Mockito.mock(org.example.email.EmailService.class);
            given(dienst.sendEmailWithMultipleAttachments(anyString(), any(), anyString(), any(), any(), any(),
                    anyList(), any())).willReturn("<neu@example.com>");
            given(postfachVersandService.postfachFuerNeueMail(any(), any(), anyBoolean())).willReturn(info);
            given(postfachVersandService.versandUeber(any())).willReturn(
                    new PostfachVersandService.Versand(dienst, "info@example.com", info));
            Email gespeichert = mail(99L, "Neu", info);
            gespeichert.setDirection(EmailDirection.OUT);
            given(ausgangsmailService.speichere(any())).willReturn(gespeichert);
        }

        @Test
        void ueberFremdesPostfach403() throws Exception {
            given(postfachService.aktivesPostfach(3L)).willReturn(Optional.of(max));

            mockMvc.perform(multipart("/api/emails/send").file(dto(
                            "{\"postfachId\":3,\"recipients\":[\"kunde@example.org\"],\"subject\":\"s\",\"body\":\"b\"}"))
                            .with(csrf()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Über dieses Postfach dürfen Sie nicht senden."));
            verify(postfachVersandService, never()).postfachFuerNeueMail(any(), any(), anyBoolean());
            verify(postfachVersandService, never()).versandUeber(any());
        }

        @Test
        void ueberSichtbaresPostfachGehtRaus() throws Exception {
            versandKlappt();
            given(postfachService.aktivesPostfach(1L)).willReturn(Optional.of(info));

            mockMvc.perform(multipart("/api/emails/send").file(dto(
                            "{\"postfachId\":1,\"recipients\":[\"kunde@example.org\"],\"subject\":\"s\",\"body\":\"b\"}"))
                            .with(csrf()))
                    .andExpect(status().isOk());
            verify(postfachVersandService).postfachFuerNeueMail(eq(1L), eq(7L), eq(false));
        }

        @Test
        void geschaeftsKennzeichenGehtImmerUebersRechnungsPostfach() throws Exception {
            versandKlappt();
            given(postfachService.aktivesPostfach(3L)).willReturn(Optional.of(max));

            // Gewähltes fremdes Postfach wird bei Geschäftsdokumenten ignoriert – kein 403.
            mockMvc.perform(multipart("/api/emails/send").file(dto(
                            "{\"postfachId\":3,\"geschaeftsdokument\":true,\"recipients\":[\"kunde@example.org\"],"
                                    + "\"subject\":\"Rechnung\",\"body\":\"b\"}"))
                            .with(csrf()))
                    .andExpect(status().isOk());
            verify(postfachVersandService).postfachFuerNeueMail(eq(3L), eq(7L), eq(true));
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = { "Rechnung", "Angebot" })
        void angehaengtesGeschaeftsdokumentGehtUebersRechnungsPostfach(String art) throws Exception {
            versandKlappt();
            org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument dok =
                    new org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument();
            dok.setId(6L);
            dok.setGeschaeftsdokumentart(art);
            given(projektDokumentRepository.findById(6L)).willReturn(Optional.of(dok));

            mockMvc.perform(multipart("/api/emails/send")
                            .file(dto("{\"recipients\":[\"kunde@example.org\"],\"subject\":\"Dokument\",\"body\":\"b\"}"))
                            .file(new MockMultipartFile("dokumentId", "", "text/plain", "6".getBytes(StandardCharsets.UTF_8)))
                            .with(csrf()))
                    .andExpect(status().isOk());
            verify(postfachVersandService).postfachFuerNeueMail(isNull(), eq(7L), eq(true));
        }

        @Test
        void unbekanntesOderNegativesPostfach400() throws Exception {
            given(postfachVersandService.postfachFuerNeueMail(any(), any(), anyBoolean()))
                    .willThrow(new IllegalArgumentException("Dieses Postfach gibt es nicht (mehr) oder es ist ausgeschaltet. Bitte ein anderes wählen."));

            for (String id : List.of(String.valueOf(Long.MAX_VALUE), "-1", "0")) {
                mockMvc.perform(multipart("/api/emails/send").file(dto(
                                "{\"postfachId\":" + id + ",\"recipients\":[\"kunde@example.org\"],\"subject\":\"s\",\"body\":\"b\"}"))
                                .with(csrf()))
                        .andExpect(status().isBadRequest());
            }
        }

        @Test
        void auslaufendesPostfach400() throws Exception {
            EmailAbsender tonline = postfach(6L, "betrieb@t-online.de", false, true);
            tonline.setLaeuftAus(true);
            // Für alle sichtbar – also kein 403, sondern die Regel „läuft aus“.
            given(postfachRepository.findByAktivTrueOrderBySortierungAscIdAsc()).willReturn(List.of(info, max, tonline));
            given(postfachService.aktivesPostfach(6L)).willReturn(Optional.of(tonline));
            given(postfachVersandService.postfachFuerNeueMail(eq(6L), any(), anyBoolean()))
                    .willThrow(new IllegalArgumentException("Dieses Postfach läuft aus. Bitte ein anderes Postfach wählen."));

            mockMvc.perform(multipart("/api/emails/send").file(dto(
                            "{\"postfachId\":6,\"recipients\":[\"kunde@example.org\"],\"subject\":\"s\",\"body\":\"b\"}"))
                            .with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Dieses Postfach läuft aus. Bitte ein anderes Postfach wählen."));
            verify(postfachVersandService, never()).versandUeber(any());
        }

        @Test
        void weiterleitungFremderMail404() throws Exception {
            mockMvc.perform(multipart("/api/emails/send").file(dto(
                            "{\"weitergeleitetVonEmailId\":" + FREMDE_MAIL
                                    + ",\"recipients\":[\"kunde@example.org\"],\"subject\":\"WG\",\"body\":\"b\"}"))
                            .with(csrf()))
                    .andExpect(status().isNotFound());
            verify(postfachVersandService, never()).antwortPostfachFuer(any(), any(), any());
        }

        @Test
        void weiterleitungZugeordneterMailUeberDasPostfachDasDerBenutzerSieht() throws Exception {
            versandKlappt();
            // Die Mail liegt nur in max@ – Erika sieht das nicht: die Weiterleitung nimmt ihr Postfach
            // bzw. das Hauptpostfach (Logik in PostfachVersandService.antwortPostfachFuer).
            given(postfachVersandService.antwortPostfachFuer(eq(fremdeProjektMail), any(), any())).willReturn(info);

            mockMvc.perform(multipart("/api/emails/send").file(dto(
                            "{\"weitergeleitetVonEmailId\":" + FREMDE_PROJEKT_MAIL
                                    + ",\"recipients\":[\"kunde@example.org\"],\"subject\":\"WG\",\"body\":\"b\"}"))
                            .with(csrf()))
                    .andExpect(status().isOk());
            ArgumentCaptor<PostfachSichtbarkeit> sicht = ArgumentCaptor.forClass(PostfachSichtbarkeit.class);
            verify(postfachVersandService).antwortPostfachFuer(eq(fremdeProjektMail), sicht.capture(), any());
            org.assertj.core.api.Assertions.assertThat(sicht.getValue().sichtbarePostfachIds()).containsExactly(1L);
            verify(postfachVersandService).versandUeber(info);
            verify(postfachVersandService, never()).versandUeber(max);
        }

        @Test
        void antwortNimmtDasPostfachFuerDenBenutzer() throws Exception {
            versandKlappt();
            given(postfachVersandService.antwortPostfachFuer(eq(infoMail), any(), any())).willReturn(info);

            mockMvc.perform(multipart("/api/emails/" + INFO_MAIL + "/reply").file(dto(
                            "{\"recipients\":[\"kunde@example.org\"],\"subject\":\"AW\",\"body\":\"b\"}")).with(csrf()))
                    .andExpect(status().isOk());
            verify(postfachVersandService).versandUeber(info);
        }

        @Test
        void detailZeigtDasPostfachUeberDasGesendetWuerde() throws Exception {
            given(postfachVersandService.antwortPostfachFuer(eq(fremdeProjektMail), any(), any())).willReturn(info);

            mockMvc.perform(get("/api/emails/" + FREMDE_PROJEKT_MAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.antwortPostfach.emailAdresse").value("info@example.com"));
        }

        @Test
        void mitgeschickteBenutzerIdZaehltNicht() throws Exception {
            versandKlappt();

            mockMvc.perform(multipart("/api/emails/send").file(dto(
                            "{\"frontendUserId\":99,\"recipients\":[\"kunde@example.org\"],\"subject\":\"s\",\"body\":\"b\"}"))
                            .with(csrf()))
                    .andExpect(status().isOk());
            verify(postfachVersandService).postfachFuerNeueMail(isNull(), eq(7L), eq(false));
        }
    }

    @Nested
    @DisplayName("Admin")
    @WithMockUser(username = "chefin", roles = "ADMIN")
    class Admin {

        @Test
        void adminSiehtAlles() throws Exception {
            given(emailRepository.findInboxFiltered()).willReturn(List.of(infoMail, fremdeMail));

            mockMvc.perform(get("/api/emails/" + FREMDE_MAIL)).andExpect(status().isOk());
            mockMvc.perform(get("/api/emails/inbox"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
            verify(zuordnungRepository, never()).findEmailIdsNurInAnderenPostfaechern(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Alle Zweige der geänderten Einzel- und Listen-Endpunkte
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Listen-Endpunkte filtern alle")
    @WithMockUser(username = "erika", roles = "USER")
    class AlleListen {

        @Test
        void jederOrdnerOhneFremdeMails() throws Exception {
            List<Email> gemischt = List.of(infoMail, fremdeMail);
            given(emailRepository.findUnassigned()).willReturn(gemischt);
            given(emailRepository.findPotentialInquiries()).willReturn(gemischt);
            given(emailRepository.findByZuordnungTypOrderBySentAtDesc(any())).willReturn(gemischt);
            given(emailRepository.findProjectEmails()).willReturn(gemischt);
            given(emailRepository.findAnfrageEmails()).willReturn(gemischt);
            given(emailRepository.findLieferantEmails()).willReturn(gemischt);
            given(emailRepository.findByDirectionOrderBySentAtDesc(EmailDirection.OUT)).willReturn(gemischt);
            given(emailRepository.findByDeletedAtIsNotNullOrderByDeletedAtDesc()).willReturn(gemischt);
            given(emailRepository.findSpam()).willReturn(gemischt);
            given(emailRepository.findNewsletter()).willReturn(gemischt);
            given(emailRepository.findStarred()).willReturn(gemischt);

            for (String ordner : List.of("unassigned", "inquiries", "new/projekt", "new/anfrage", "new/lieferant",
                    "projects", "offers", "suppliers", "sent", "trash", "spam", "newsletter", "starred")) {
                mockMvc.perform(get("/api/emails/" + ordner))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$", hasSize(1)))
                        .andExpect(jsonPath("$[0].id").value(INFO_MAIL));
            }
        }

        @Test
        void kurzeSucheLiefertNichts() throws Exception {
            mockMvc.perform(get("/api/emails/search").param("q", " a ")).andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
            verify(emailRepository, never()).searchGlobal(anyString());
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = { "inbox", "spam", "newsletter", "starred", "unassigned",
                "inquiries", "projects", "offers", "suppliers", "trash" })
        void alleGelesenJeOrdner(String ordner) throws Exception {
            Email schonGelesen = mail(11L, "Schon gelesen", info);
            schonGelesen.setRead(true);
            infoMail.setFirstViewedAt(LocalDateTime.of(2026, 10, 9, 8, 0));
            List<Email> gemischt = List.of(infoMail, fremdeMail, schonGelesen);
            given(emailRepository.findInboxFiltered()).willReturn(gemischt);
            given(emailRepository.findSpam()).willReturn(gemischt);
            given(emailRepository.findNewsletter()).willReturn(gemischt);
            given(emailRepository.findStarred()).willReturn(gemischt);
            given(emailRepository.findUnassigned()).willReturn(gemischt);
            given(emailRepository.findPotentialInquiries()).willReturn(gemischt);
            given(emailRepository.findProjectEmails()).willReturn(gemischt);
            given(emailRepository.findAnfrageEmails()).willReturn(gemischt);
            given(emailRepository.findLieferantEmails()).willReturn(gemischt);
            given(emailRepository.findByDeletedAtIsNotNullOrderByDeletedAtDesc()).willReturn(gemischt);

            mockMvc.perform(post("/api/emails/mark-all-read").param("folder", ordner).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.updated").value(1));
            org.assertj.core.api.Assertions.assertThat(fremdeMail.isRead()).isFalse();
            org.assertj.core.api.Assertions.assertThat(infoMail.getFirstViewedAt()).isEqualTo(LocalDateTime.of(2026, 10, 9, 8, 0));
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = { "sent", "tax-advisors", "gibt-es-nicht" })
        void alleGelesenOhneTreffer(String ordner) throws Exception {
            mockMvc.perform(post("/api/emails/mark-all-read").param("folder", ordner).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.updated").value(0));
            verify(emailRepository, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("Verschieben (Massenaktion)")
    @WithMockUser(username = "erika", roles = "USER")
    class Verschieben {

        private org.springframework.test.web.servlet.ResultActions verschiebe(String json) throws Exception {
            return mockMvc.perform(post("/api/emails/bulk/move-to-folder").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(json));
        }

        @Test
        void pflichtfelderUndUngueltigesZiel400() throws Exception {
            verschiebe("{\"targetFolder\":\"trash\"}").andExpect(status().isBadRequest());
            verschiebe("{\"ids\":[10]}").andExpect(status().isBadRequest());
            verschiebe("{\"ids\":[10],\"targetFolder\":\"<script>alert(1)</script>\"}").andExpect(status().isBadRequest());
            verschiebe("{\"ids\":[10],\"targetFolder\":\"'; DROP TABLE email; --\"}").andExpect(status().isBadRequest());
            verify(emailRepository, never()).findAllById(any());
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.CsvSource({ "inbox,true", "inbox,false", "spam,false", "newsletter,false", "trash,false" })
        void jedesZielNurFuerSichtbare(String ziel, boolean modellBereit) throws Exception {
            given(spamBayesService.isModelReady()).willReturn(modellBereit);
            given(emailRepository.findAllById(List.of(INFO_MAIL, FREMDE_MAIL))).willReturn(List.of(infoMail, fremdeMail));

            verschiebe("{\"ids\":[" + INFO_MAIL + "," + FREMDE_MAIL + "],\"targetFolder\":\"" + ziel + "\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.moved").value(1));
            verify(emailRepository).saveAll(List.of(infoMail));
        }

        @Test
        void nurFremdeMailsVerschiebtNichts() throws Exception {
            given(emailRepository.findAllById(List.of(FREMDE_MAIL))).willReturn(List.of(fremdeMail));

            verschiebe("{\"ids\":[" + FREMDE_MAIL + "],\"targetFolder\":\"spam\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.moved").value(0));
            verify(emailRepository, never()).saveAll(any());
            verify(spamBayesService, never()).train(any(), anyBoolean());
        }
    }

    @Nested
    @DisplayName("Einzelaktionen an sichtbaren Mails")
    @WithMockUser(username = "erika", roles = "USER")
    class Einzelaktionen {

        @Test
        void unbekannteMail404() throws Exception {
            given(emailRepository.findById(404L)).willReturn(Optional.empty());

            mockMvc.perform(get("/api/emails/404/mark-viewed")).andExpect(status().isNotFound());
            mockMvc.perform(post("/api/emails/404/toggle-star").with(csrf())).andExpect(status().isNotFound());
            mockMvc.perform(post("/api/emails/404/unassign").with(csrf())).andExpect(status().isNotFound());
            mockMvc.perform(post("/api/emails/404/mark-read").with(csrf())).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/emails/404/possible-assignments")).andExpect(status().isNotFound());
            mockMvc.perform(delete("/api/emails/404/permanent").with(csrf())).andExpect(status().isNoContent());
        }

        @Test
        void angesehenMarkierenNurBeimErstenMal() throws Exception {
            mockMvc.perform(get("/api/emails/" + INFO_MAIL + "/mark-viewed")).andExpect(status().isOk());
            LocalDateTime erstesMal = infoMail.getFirstViewedAt();
            org.assertj.core.api.Assertions.assertThat(erstesMal).isNotNull();

            mockMvc.perform(get("/api/emails/" + INFO_MAIL + "/mark-viewed")).andExpect(status().isOk());
            org.assertj.core.api.Assertions.assertThat(infoMail.getFirstViewedAt()).isEqualTo(erstesMal);
            verify(emailRepository, Mockito.times(1)).save(infoMail);
        }

        @Test
        void angesehenGiltAuchFuerZugeordneteFremdeMail() throws Exception {
            mockMvc.perform(get("/api/emails/" + FREMDE_PROJEKT_MAIL + "/mark-viewed")).andExpect(status().isOk());
            mockMvc.perform(get("/api/emails/" + FREMDE_MAIL + "/mark-viewed")).andExpect(status().isNotFound());
        }

        @Test
        void gelesenUndStern() throws Exception {
            mockMvc.perform(post("/api/emails/" + INFO_MAIL + "/mark-read").with(csrf())).andExpect(status().isOk());
            mockMvc.perform(post("/api/emails/" + INFO_MAIL + "/mark-read").with(csrf())).andExpect(status().isOk());
            verify(emailRepository, Mockito.times(1)).save(infoMail);

            mockMvc.perform(post("/api/emails/" + INFO_MAIL + "/toggle-star").with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isStarred").value(true));
            mockMvc.perform(post("/api/emails/" + INFO_MAIL + "/toggle-star").with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isStarred").value(false));
        }

        @Test
        void zuordnen() throws Exception {
            org.example.kalkulationsprogramm.domain.Anfrage anfrage = new org.example.kalkulationsprogramm.domain.Anfrage();
            anfrage.setId(6L);
            org.example.kalkulationsprogramm.domain.Lieferanten lieferant = new org.example.kalkulationsprogramm.domain.Lieferanten();
            lieferant.setId(8L);
            given(projektRepository.findById(5L)).willReturn(Optional.of(fremdeProjektMail.getProjekt()));
            given(anfrageRepository.findById(6L)).willReturn(Optional.of(anfrage));
            given(lieferantenRepository.findById(8L)).willReturn(Optional.of(lieferant));

            mockMvc.perform(post("/api/emails/" + INFO_MAIL + "/assign/projekt/5").with(csrf()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.projektId").value(5));
            mockMvc.perform(post("/api/emails/" + INFO_MAIL + "/assign/anfrage/6").with(csrf()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.anfrageId").value(6));
            mockMvc.perform(post("/api/emails/" + INFO_MAIL + "/assign/lieferant/8").with(csrf()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.lieferantId").value(8));
            mockMvc.perform(post("/api/emails/" + INFO_MAIL + "/unassign").with(csrf()))
                    .andExpect(status().isOk());

            // Ziel unbekannt → 404, egal wie sichtbar die Mail ist.
            mockMvc.perform(post("/api/emails/" + INFO_MAIL + "/assign/projekt/" + Long.MAX_VALUE).with(csrf()))
                    .andExpect(status().isNotFound());
            mockMvc.perform(post("/api/emails/" + INFO_MAIL + "/assign/anfrage/-1").with(csrf()))
                    .andExpect(status().isNotFound());
            mockMvc.perform(post("/api/emails/" + INFO_MAIL + "/assign/lieferant/0").with(csrf()))
                    .andExpect(status().isNotFound());
            // Fremde Mail → 404, auch mit gültigem Ziel.
            mockMvc.perform(post("/api/emails/" + FREMDE_MAIL + "/assign/anfrage/6").with(csrf()))
                    .andExpect(status().isNotFound());
            mockMvc.perform(post("/api/emails/" + FREMDE_MAIL + "/assign/lieferant/8").with(csrf()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void moeglicheZuordnungen() throws Exception {
            given(emailAutoAssignmentService.findPossibleAssignments(infoMail)).willReturn(null);

            mockMvc.perform(get("/api/emails/" + INFO_MAIL + "/possible-assignments")).andExpect(status().isOk());
            mockMvc.perform(get("/api/emails/" + FREMDE_MAIL + "/possible-assignments")).andExpect(status().isNotFound());
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.CsvSource({ "90,false", "90,true", "10,false" })
        void endgueltigLoeschen(int spamScore, boolean spam) throws Exception {
            infoMail.setSpamScore(spamScore);
            infoMail.setSpam(spam);
            given(emailRepository.findByIdForUpdate(INFO_MAIL)).willReturn(Optional.of(infoMail));

            mockMvc.perform(delete("/api/emails/" + INFO_MAIL + "/permanent").with(csrf())).andExpect(status().isNoContent());

            org.assertj.core.api.Assertions.assertThat(infoMail.isSpam()).isEqualTo(spam || spamScore >= 85);
            verify(emailRepository).delete(infoMail);
        }

        @Test
        void endgueltigLoeschenAuchWennServerNichtErreichbar() throws Exception {
            given(emailRepository.findByIdForUpdate(INFO_MAIL)).willReturn(Optional.of(infoMail));
            Mockito.doThrow(new IllegalStateException("Server weg")).when(emailImportService).deleteEmailFromServer(infoMail);

            mockMvc.perform(delete("/api/emails/" + INFO_MAIL + "/permanent").with(csrf())).andExpect(status().isNoContent());
            verify(emailRepository).delete(infoMail);
        }
    }

    @Nested
    @DisplayName("Spam- und Newsletter-Urteile")
    @WithMockUser(username = "erika", roles = "USER")
    class Urteile {

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = { "SPAM", "HAM", "HAM_IMPLICIT", "KEINS" })
        void alleUrteileAnSichtbarerMail(String vorher) throws Exception {
            for (String aktion : List.of("mark-spam", "mark-not-spam", "mark-not-newsletter", "confirm-newsletter")) {
                infoMail.setUserSpamVerdict("KEINS".equals(vorher) ? null : vorher);
                mockMvc.perform(post("/api/emails/" + INFO_MAIL + "/" + aktion).with(csrf())).andExpect(status().isOk());
            }
        }

        @Test
        void newsletterBestaetigenOhneSpamWert() throws Exception {
            infoMail.setSpamScore(null);

            mockMvc.perform(post("/api/emails/" + INFO_MAIL + "/confirm-newsletter").with(csrf())).andExpect(status().isOk());
            org.assertj.core.api.Assertions.assertThat(infoMail.getSpamScore()).isZero();
            org.assertj.core.api.Assertions.assertThat(infoMail.isNewsletter()).isTrue();
        }

        @Test
        void fremdeMailBleibtUnangetastet() throws Exception {
            for (String aktion : List.of("mark-not-newsletter", "confirm-newsletter")) {
                mockMvc.perform(post("/api/emails/" + FREMDE_MAIL + "/" + aktion).with(csrf())).andExpect(status().isNotFound());
            }
            verify(spamBayesService, never()).train(any(), anyBoolean());
        }
    }

    @Nested
    @DisplayName("Anhänge")
    @WithMockUser(username = "erika", roles = "USER")
    class Anhaenge {

        private final java.nio.file.Path basis = java.nio.file.Path.of("target/test-attachments").toAbsolutePath().normalize();
        private final List<java.nio.file.Path> angelegt = new ArrayList<>();

        @org.junit.jupiter.api.AfterEach
        void aufraeumen() throws Exception {
            for (java.nio.file.Path p : angelegt) {
                java.nio.file.Files.deleteIfExists(p);
            }
        }

        private void datei(java.nio.file.Path pfad) throws Exception {
            java.nio.file.Files.createDirectories(pfad.getParent());
            java.nio.file.Files.write(pfad, "Testinhalt".getBytes(StandardCharsets.UTF_8));
            angelegt.add(pfad);
        }

        private org.example.kalkulationsprogramm.domain.EmailAttachment anhang(Long id, String gespeichert,
                String original, String mime) {
            org.example.kalkulationsprogramm.domain.EmailAttachment a = new org.example.kalkulationsprogramm.domain.EmailAttachment();
            a.setId(id);
            a.setStoredFilename(gespeichert);
            a.setOriginalFilename(original);
            a.setMimeType(mime);
            a.setEmail(infoMail);
            infoMail.getAttachments().add(a);
            return a;
        }

        private String eindeutig(String endung) {
            return "sichtbarkeit-" + java.util.UUID.randomUUID() + endung;
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.CsvSource(nullValues = "NULL", value = {
                "application/pdf, rechnung.pdf, application/pdf",
                "NULL, bild.png, image/png",
                "NULL, foto.jpg, image/jpeg",
                "NULL, foto.jpeg, image/jpeg",
                "NULL, animation.gif, image/gif",
                "NULL, daten.xml, application/xml",
                "NULL, notiz.txt, text/plain",
                "NULL, plan.pdf, application/pdf",
                "NULL, unbekannt.xyz, application/octet-stream",
                "NULL, NULL, application/octet-stream",
                "'application/octet-stream; name=x.png', x.png, image/png",
                "'   ', blank.png, application/octet-stream",
                "kaputt///typ, kaputt.png, application/octet-stream" })
        void einzelnerAnhangMitTypErkennung(String mime, String original, String erwartet) throws Exception {
            String gespeichert = eindeutig(".unbekannteendung");
            datei(basis.resolve(gespeichert));
            anhang(501L, gespeichert, original, mime);

            mockMvc.perform(get("/api/emails/" + INFO_MAIL + "/attachments/501"))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                            .contentType(erwartet));
        }

        @Test
        void anhangInUnterordnernUndFehlend() throws Exception {
            String zweiter = eindeutig(".pdf");
            datei(basis.resolve(String.valueOf(INFO_MAIL)).resolve(zweiter));
            anhang(502L, zweiter, "  ", "application/pdf");
            String dritter = eindeutig(".pdf");
            datei(basis.resolve("attachments").resolve(String.valueOf(INFO_MAIL)).resolve(dritter));
            anhang(503L, dritter, "c.pdf", "application/pdf");
            anhang(504L, eindeutig(".pdf"), "weg.pdf", "application/pdf");

            mockMvc.perform(get("/api/emails/" + INFO_MAIL + "/attachments/502")).andExpect(status().isOk());
            mockMvc.perform(get("/api/emails/" + INFO_MAIL + "/attachments/503")).andExpect(status().isOk());
            mockMvc.perform(get("/api/emails/" + INFO_MAIL + "/attachments/504")).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/emails/" + INFO_MAIL + "/attachments/999")).andExpect(status().isNotFound());
        }

        @Test
        void anhangRechteUndFremdeMail() throws Exception {
            given(lieferantDokumentZugriffService.istAnhangSichtbar(eq(505L), any())).willReturn(false);
            mockMvc.perform(get("/api/emails/" + INFO_MAIL + "/attachments/505")).andExpect(status().isNotFound());

            given(lieferantDokumentZugriffService.sichtbareTypen(any(), any())).willReturn(Optional.empty());
            mockMvc.perform(get("/api/emails/" + INFO_MAIL + "/attachments/1")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/emails/" + INFO_MAIL + "/attachments/download-all")).andExpect(status().isUnauthorized());
        }

        @Test
        void zipMitAllenFundorten() throws Exception {
            String erster = eindeutig(".pdf");
            datei(basis.resolve(erster));
            anhang(601L, erster, null, "application/pdf");
            String zweiter = eindeutig(".pdf");
            datei(basis.resolve(String.valueOf(INFO_MAIL)).resolve(zweiter));
            anhang(602L, zweiter, "zwei.pdf", "application/pdf");
            String dritter = eindeutig(".pdf");
            datei(basis.resolve("attachments").resolve(String.valueOf(INFO_MAIL)).resolve(dritter));
            anhang(603L, dritter, "drei.pdf", "application/pdf");
            anhang(604L, eindeutig(".pdf"), "fehlt.pdf", "application/pdf");
            anhang(605L, "../../etc/passwd", "passwd", "text/plain");
            org.example.kalkulationsprogramm.domain.EmailAttachment inline = anhang(606L, erster, "logo.png", "image/png");
            inline.setInlineAttachment(true);
            String gesperrt = eindeutig(".pdf");
            datei(basis.resolve(gesperrt));
            anhang(607L, gesperrt, "rechnung.pdf", "application/pdf");
            given(lieferantDokumentZugriffService.istAnhangSichtbar(eq(607L), any())).willReturn(false);

            byte[] zip = mockMvc.perform(get("/api/emails/" + INFO_MAIL + "/attachments/download-all"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();

            List<String> namen = new ArrayList<>();
            try (java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
                for (java.util.zip.ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                    namen.add(e.getName());
                }
            }
            org.assertj.core.api.Assertions.assertThat(namen).containsExactlyInAnyOrder(erster, "zwei.pdf", "drei.pdf");
        }

        @Test
        void zipOhneBetreffUndLeer() throws Exception {
            infoMail.setSubject(null);
            anhang(701L, eindeutig(".pdf"), "fehlt.pdf", "application/pdf");

            mockMvc.perform(get("/api/emails/" + INFO_MAIL + "/attachments/download-all"))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                            .string("Content-Disposition", org.hamcrest.Matchers.containsString("Anhaenge_" + INFO_MAIL)));
        }

        @Test
        void zipNurGesperrteOderKeineAnhaenge() throws Exception {
            mockMvc.perform(get("/api/emails/" + INFO_MAIL + "/attachments/download-all")).andExpect(status().isNoContent());
            infoMail.setAttachments(null);
            mockMvc.perform(get("/api/emails/" + INFO_MAIL + "/attachments/download-all")).andExpect(status().isNoContent());

            infoMail.setAttachments(new ArrayList<>());
            anhang(801L, eindeutig(".pdf"), "rechnung.pdf", "application/pdf");
            given(lieferantDokumentZugriffService.istAnhangSichtbar(eq(801L), any())).willReturn(false);
            mockMvc.perform(get("/api/emails/" + INFO_MAIL + "/attachments/download-all")).andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("Reiter und Detail-Ansicht")
    @WithMockUser(username = "erika", roles = "USER")
    class ReiterUndDetail {

        @Test
        void alleReiterUngefiltertUndUnbekannt404() throws Exception {
            org.example.kalkulationsprogramm.domain.Anfrage anfrage = new org.example.kalkulationsprogramm.domain.Anfrage();
            anfrage.setId(6L);
            org.example.kalkulationsprogramm.domain.Lieferanten lieferant = new org.example.kalkulationsprogramm.domain.Lieferanten();
            lieferant.setId(8L);
            given(anfrageRepository.findById(6L)).willReturn(Optional.of(anfrage));
            given(lieferantenRepository.findById(8L)).willReturn(Optional.of(lieferant));
            given(emailRepository.findByAnfrageOrderBySentAtDesc(anfrage)).willReturn(List.of(fremdeMail));
            given(emailRepository.findByLieferantOrderBySentAtDesc(lieferant)).willReturn(List.of(fremdeMail));

            mockMvc.perform(get("/api/emails/anfrage/6")).andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(FREMDE_MAIL));
            mockMvc.perform(get("/api/emails/lieferant/8")).andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(FREMDE_MAIL));
            for (String reiter : List.of("projekt", "anfrage", "lieferant")) {
                mockMvc.perform(get("/api/emails/" + reiter + "/" + Long.MAX_VALUE)).andExpect(status().isNotFound());
            }
        }

        @Test
        void detailMitEingebettetemBildUndOhneRichtung() throws Exception {
            org.example.kalkulationsprogramm.domain.EmailAttachment logo = new org.example.kalkulationsprogramm.domain.EmailAttachment();
            logo.setId(77L);
            logo.setOriginalFilename("logo.png");
            logo.setContentId("logo");
            logo.setInlineAttachment(false);
            org.example.kalkulationsprogramm.domain.EmailAttachment bild = new org.example.kalkulationsprogramm.domain.EmailAttachment();
            bild.setId(78L);
            bild.setOriginalFilename("bild.png");
            bild.setInlineAttachment(true);
            org.example.kalkulationsprogramm.domain.EmailAttachment pdf = new org.example.kalkulationsprogramm.domain.EmailAttachment();
            pdf.setId(79L);
            pdf.setOriginalFilename("plan.pdf");
            pdf.setContentId(" ");
            infoMail.setAttachments(new ArrayList<>(List.of(logo, bild, pdf)));
            infoMail.setHtmlBody("<p>Hallo <img src=\"cid:logo\"></p>");
            infoMail.setDirection(null);
            infoMail.setZuordnungTyp(null);
            given(postfachVersandService.antwortPostfachFuer(eq(infoMail), any(), any())).willReturn(info);

            mockMvc.perform(get("/api/emails/" + INFO_MAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.attachments", hasSize(3)))
                    .andExpect(jsonPath("$.htmlBody").value(org.hamcrest.Matchers.containsString("/api/emails/10/attachments/77")))
                    .andExpect(jsonPath("$.antwortPostfach.id").value(1));

            infoMail.setHtmlBody("   ");
            mockMvc.perform(get("/api/emails/" + INFO_MAIL)).andExpect(status().isOk());
            infoMail.setHtmlBody(null);
            mockMvc.perform(get("/api/emails/" + INFO_MAIL)).andExpect(status().isOk());
            infoMail.setAttachments(null);
            mockMvc.perform(get("/api/emails/" + INFO_MAIL)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasAttachments").value(false));
        }
    }

    @Nested
    @DisplayName("Rechnungs-Postfach sieht jeder")
    @WithMockUser(username = "erika", roles = "USER")
    class RechnungsPostfachSiehtJeder {

        private EmailAbsender rechnungen;
        private Email rechnungsMail;

        @BeforeEach
        void rechnungsPostfach() {
            // Für Rechnungen & Mahnungen, „nur bestimmte“ ohne Freigabe für Erika – sie sieht es trotzdem.
            rechnungen = postfach(2L, "rechnungen@example.com", false, false);
            rechnungen.setFuerGeschaeftsdokumente(true);
            given(postfachRepository.findByAktivTrueOrderBySortierungAscIdAsc()).willReturn(List.of(info, max, rechnungen));
            rechnungsMail = mail(40L, "Rückfrage zur Rechnung", rechnungen);
            given(emailRepository.findById(40L)).willReturn(Optional.of(rechnungsMail));
            given(zuordnungRepository.findEmailIdsNurInAnderenPostfaechern(Set.of(1L, 2L)))
                    .willReturn(Set.of(FREMDE_MAIL, FREMDE_PROJEKT_MAIL));
        }

        @Test
        void inListeUndDetail() throws Exception {
            given(emailRepository.findInboxFiltered()).willReturn(List.of(infoMail, fremdeMail, rechnungsMail));

            mockMvc.perform(get("/api/emails/inbox"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[1].id").value(40));
            mockMvc.perform(get("/api/emails/40")).andExpect(status().isOk());
            mockMvc.perform(post("/api/emails/40/mark-read").with(csrf())).andExpect(status().isOk());
        }

        @Test
        void inDerAbsenderAuswahl() throws Exception {
            given(postfachService.absenderAuswahl(any(), any())).willReturn(List.of());

            mockMvc.perform(get("/api/emails/absender-postfaecher")).andExpect(status().isOk());

            ArgumentCaptor<PostfachSichtbarkeit> sicht = ArgumentCaptor.forClass(PostfachSichtbarkeit.class);
            verify(postfachService).absenderAuswahl(isNull(), sicht.capture());
            org.assertj.core.api.Assertions.assertThat(sicht.getValue().sichtbarePostfachIds()).containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        void sendenUeberPostfachIdErlaubt() throws Exception {
            org.example.email.EmailService dienst = Mockito.mock(org.example.email.EmailService.class);
            given(dienst.sendEmailWithMultipleAttachments(anyString(), any(), anyString(), any(), any(), any(),
                    anyList(), any())).willReturn("<neu@example.com>");
            given(postfachService.aktivesPostfach(2L)).willReturn(Optional.of(rechnungen));
            given(postfachVersandService.postfachFuerNeueMail(eq(2L), eq(7L), eq(false))).willReturn(rechnungen);
            given(postfachVersandService.versandUeber(rechnungen)).willReturn(
                    new PostfachVersandService.Versand(dienst, "rechnungen@example.com", rechnungen));
            Email gespeichert = mail(99L, "Neu", rechnungen);
            gespeichert.setDirection(EmailDirection.OUT);
            given(ausgangsmailService.speichere(any())).willReturn(gespeichert);

            mockMvc.perform(multipart("/api/emails/send").file(dto(
                            "{\"postfachId\":2,\"recipients\":[\"kunde@example.org\"],\"subject\":\"s\",\"body\":\"b\"}"))
                            .with(csrf()))
                    .andExpect(status().isOk());
            verify(postfachVersandService).versandUeber(rechnungen);
        }
    }
}
