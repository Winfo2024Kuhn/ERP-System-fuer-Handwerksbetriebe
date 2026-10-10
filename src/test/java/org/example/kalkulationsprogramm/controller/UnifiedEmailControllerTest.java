package org.example.kalkulationsprogramm.controller;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.EmailBlacklistEntry;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp;
import org.example.kalkulationsprogramm.dto.ContactDto;
import org.example.kalkulationsprogramm.dto.SteuerberaterKontaktDto;
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.EmailBlacklistRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.dto.EmailThreadDto;
import org.example.kalkulationsprogramm.dto.EmailThreadEntryDto;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;

@WebMvcTest({UnifiedEmailController.class, EmailDraftController.class})
@org.springframework.context.annotation.Import({org.example.kalkulationsprogramm.service.EmailDraftService.class,
        org.example.kalkulationsprogramm.service.AusgangsmailService.class})
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.test.context.TestPropertySource(properties = {
        "file.mail-attachment-dir=target/test-attachments"
})
class UnifiedEmailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @org.springframework.boot.test.mock.mockito.SpyBean
    private UnifiedEmailController unifiedEmailController;

    @MockBean private org.example.kalkulationsprogramm.service.mail.SentMailArchiver sentMailArchiver;
    @MockBean private EmailRepository emailRepository;
    @MockBean private org.example.kalkulationsprogramm.repository.EmailDraftRepository emailDraftRepository;
    @MockBean private org.example.kalkulationsprogramm.repository.EmailDraftAttachmentRepository emailDraftAttachmentRepository;
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
    @MockBean private org.example.kalkulationsprogramm.service.PostfachService postfachService;
    @MockBean private org.example.kalkulationsprogramm.service.PostfachVersandService postfachVersandService;

    @org.junit.jupiter.api.BeforeEach
    void threadKennzahlenWieEinzelmail() {
        // Anhang-Rechte prüft der Test "Attachment Download Tests" gezielt; sonst darf der Aufrufer alles öffnen.
        given(lieferantDokumentZugriffService.sichtbareTypen(any(), any())).willReturn(Optional.of(
                java.util.EnumSet.allOf(org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.class)));
        given(lieferantDokumentZugriffService.istAnhangSichtbar(any(), any())).willReturn(true);
        // Ohne Postfächer: Versand wie bisher über das Standard-Konto mit fester Absender-Adresse.
        given(postfachVersandService.versandUeber(any())).willReturn(
                new org.example.kalkulationsprogramm.service.PostfachVersandService.Versand(
                        new org.example.email.EmailService("mail.example.com", 465, "user", "pass"),
                        "absender@example.com", null));
        given(emailThreadService.kennzahlenFuer(any())).willAnswer(invocation -> {
            Email email = invocation.getArgument(0);
            return new EmailThreadService.ThreadKennzahlen(email.getId(), 1, email.getSentAt());
        });
    }

    private Email createTestEmail(Long id, String subject, String from) {
        Email email = new Email();
        email.setId(id);
        email.setSubject(subject);
        email.setFromAddress(from);
        email.setRecipient("test@example.com");
        email.setDirection(EmailDirection.IN);
        email.setSentAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        email.setZuordnungTyp(EmailZuordnungTyp.KEINE);
        email.setAttachments(Collections.emptyList());
        return email;
    }

    @Test
    void successfulDraftSendDeletesDraftOnServerAfterSmtp() throws Exception {
        prepareDraftSend();
        org.example.kalkulationsprogramm.domain.EmailDraft draft = new org.example.kalkulationsprogramm.domain.EmailDraft();
        draft.setId(42L);
        given(emailDraftRepository.findById(42L)).willReturn(Optional.of(draft));
        org.mockito.Mockito.doAnswer(invocation -> {
            given(emailDraftRepository.findById(42L)).willReturn(Optional.empty()); return null;
        }).when(emailDraftRepository).delete(draft);
        org.mockito.Mockito.doReturn("sent-message").when(unifiedEmailController).sendeSmtpMail(any(), any(), any(), any(), any(), any(), any());
        mockMvc.perform(multipart("/api/emails/send").file(draftSendPart()))
                .andExpect(status().isOk());
        // Observable contract: the sent draft must no longer be returned by the server.
        mockMvc.perform(get("/api/emails/drafts/42")).andExpect(status().isNotFound());
    }

    @Test
    void smtpFailureKeepsDraftAvailable() throws Exception {
        prepareDraftSend();
        storedDraft(null);
        org.mockito.Mockito.doThrow(new java.io.IOException("SMTP unavailable"))
                .when(unifiedEmailController).sendeSmtpMail(any(), any(), any(), any(), any(), any(), any());
        mockMvc.perform(multipart("/api/emails/send").file(draftSendPart()))
                .andExpect(status().isInternalServerError());
        mockMvc.perform(get("/api/emails/drafts/42")).andExpect(status().isOk());
        verify(emailDraftRepository, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void successfulReplyDeletesOnlyItsLinkedDraft() throws Exception {
        prepareDraftSend();
        storedDraft(7L);
        given(emailRepository.findById(7L)).willReturn(Optional.of(createTestEmail(7L, "Original", "test@example.com")));
        org.mockito.Mockito.doReturn("reply-message").when(unifiedEmailController).sendeSmtpMail(any(), any(), any(), any(), any(), any(), any(), any());
        mockMvc.perform(multipart("/api/emails/7/reply").file(draftSendPart())).andExpect(status().isOk());
        mockMvc.perform(get("/api/emails/drafts/42")).andExpect(status().isNotFound());
    }

    @Test
    void laterArchivingFailureCannotKeepAnAlreadySentDraft() throws Exception {
        prepareDraftSend();
        storedDraft(null);
        org.mockito.Mockito.doReturn("sent-message").when(unifiedEmailController).sendeSmtpMail(any(), any(), any(), any(), any(), any(), any());
        given(emailRepository.saveAndFlush(any())).willThrow(new IllegalStateException("Archive failed"));
        mockMvc.perform(multipart("/api/emails/send").file(draftSendPart())).andExpect(status().isInternalServerError());
        mockMvc.perform(get("/api/emails/drafts/42")).andExpect(status().isNotFound());
    }

    @Test
    void absentDraftIsRejectedBeforeSending() throws Exception {
        mockMvc.perform(multipart("/api/emails/send").file(draftSendPart())).andExpect(status().isNotFound());
        verify(unifiedEmailController, org.mockito.Mockito.never()).sendeSmtpMail(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void replyDraftCannotBeSentAgainstAnotherThread() throws Exception {
        storedDraft(8L);
        given(emailRepository.findById(7L)).willReturn(Optional.of(createTestEmail(7L, "Original", "test@example.com")));
        mockMvc.perform(multipart("/api/emails/7/reply").file(draftSendPart())).andExpect(status().isBadRequest());
        verify(unifiedEmailController, org.mockito.Mockito.never()).sendeSmtpMail(any(), any(), any(), any(), any(), any(), any(), any());
        verify(emailDraftRepository, org.mockito.Mockito.never()).delete(any());
    }

    private void storedDraft(Long replyEmailId) {
        org.example.kalkulationsprogramm.domain.EmailDraft draft = new org.example.kalkulationsprogramm.domain.EmailDraft();
        draft.setId(42L);
        draft.setReplyEmailId(replyEmailId);
        given(emailDraftRepository.findById(42L)).willReturn(Optional.of(draft));
        org.mockito.Mockito.doAnswer(invocation -> {
            given(emailDraftRepository.findById(42L)).willReturn(Optional.empty()); return null;
        }).when(emailDraftRepository).delete(draft);
    }

    private MockMultipartFile draftSendPart() {
        return new MockMultipartFile("dto", "", "application/json", "{\"draftId\":42,\"sender\":\"absender@example.com\",\"recipients\":[\"test@example.com\"],\"subject\":\"Plan\",\"body\":\"Hallo\"}".getBytes(StandardCharsets.UTF_8));
    }

    private void prepareDraftSend() {
        given(systemSettingsService.getStandardMailKonto()).willReturn(new org.example.kalkulationsprogramm.service.SystemSettingsService.MailKonto(
                "mail.example.com", 587, "user", "pass", "absender@example.com", "Firma"));
        given(systemSettingsService.getSmtpHost()).willReturn("mail.example.com");
        given(systemSettingsService.getSmtpPort()).willReturn(587);
        given(systemSettingsService.getSmtpUsername()).willReturn("user");
        given(systemSettingsService.getSmtpPassword()).willReturn("pass");
        given(emailAbsenderService.findActiveEmailAddresses()).willReturn(List.of("absender@example.com"));
    }

    // ═══════════════════════════════════════════════════════════════
    // SEARCH
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/emails/search")
    class Search {

        @Test
        @DisplayName("Suche findet E-Mails nach Betreff")
        void searchFindsEmails() throws Exception {
            Email email = createTestEmail(1L, "Angebot Geländer", "kunde@example.com");
            given(emailRepository.searchGlobal("Geländer")).willReturn(List.of(email));

            mockMvc.perform(get("/api/emails/search").param("q", "Geländer"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].subject").value("Angebot Geländer"))
                    .andExpect(jsonPath("$[0].fromAddress").value("kunde@example.com"));
        }

        @Test
        @DisplayName("Suche mit zu kurzem Query gibt leere Liste zurück")
        void searchTooShortReturnEmpty() throws Exception {
            mockMvc.perform(get("/api/emails/search").param("q", "A"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("Suche ohne q-Parameter gibt 400")
        void searchMissingParam() throws Exception {
            mockMvc.perform(get("/api/emails/search"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/emails/tax-advisors")
    class TaxAdvisorFolder {

        @Test
        @DisplayName("zeigt ein- und ausgehende E-Mails derselben Steuerberater-Domain")
        void findsIncomingAndOutgoingEmailsByDomain() throws Exception {
            SteuerberaterKontaktDto contact = new SteuerberaterKontaktDto();
            contact.setEmail("kanzlei@steuerprofi.de");
            given(steuerberaterKontaktService.findAll()).willReturn(List.of(contact));

            Email incoming = createTestEmail(10L, "BWA", "service@steuerprofi.de");
            Email outgoing = createTestEmail(11L, "Re: BWA", "firma@example.com");
            outgoing.setDirection(EmailDirection.OUT);
            outgoing.setRecipient("Sachbearbeitung <team@steuerprofi.de>");
            Email falsePositive = createTestEmail(12L, "Fremd", "info@steuerprofi.de.example.org");

            given(emailRepository.findTaxAdvisorCandidates("@steuerprofi.de"))
                    .willReturn(List.of(incoming, outgoing, falsePositive));

            mockMvc.perform(get("/api/emails/tax-advisors"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.containsInAnyOrder(10, 11)))
                    .andExpect(jsonPath("$[?(@.id == 12)]").isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // MARK SPAM
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/emails/{id}/mark-spam")
    class MarkSpam {

        @Test
        @DisplayName("E-Mail als Spam markieren")
        void markSpamSuccess() throws Exception {
            Email email = createTestEmail(1L, "Gewinnspiel", "spam@example.com");
            given(emailRepository.findById(1L)).willReturn(Optional.of(email));

            mockMvc.perform(post("/api/emails/1/mark-spam"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Spam")));

            verify(emailRepository).save(email);
            verify(spamBayesService).train(email, true);
        }

        @Test
        @DisplayName("Nicht existierende E-Mail gibt 404")
        void markSpamNotFound() throws Exception {
            given(emailRepository.findById(999L)).willReturn(Optional.empty());

            mockMvc.perform(post("/api/emails/999/mark-spam"))
                    .andExpect(status().isNotFound());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // MARK NOT SPAM
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/emails/{id}/mark-not-spam")
    class MarkNotSpam {

        @Test
        @DisplayName("E-Mail als Nicht-Spam markieren")
        void markNotSpamSuccess() throws Exception {
            Email email = createTestEmail(1L, "Bestellung", "kunde@example.com");
            email.setSpam(true);
            given(emailRepository.findById(1L)).willReturn(Optional.of(email));

            mockMvc.perform(post("/api/emails/1/mark-not-spam"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("kein Spam")));

            verify(emailRepository).save(email);
            verify(spamBayesService).train(email, false);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // BLOCK SENDER
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/emails/{id}/block-sender")
    class BlockSender {

        @Test
        @DisplayName("Absender sperren und alle bestehenden E-Mails löschen (kein Spam-Marking)")
        void blockSenderSuccess() throws Exception {
            Email email = createTestEmail(1L, "Test", "boese@example.com");
            given(emailRepository.findById(1L)).willReturn(Optional.of(email));
            given(emailBlacklistRepository.existsByEmailAddress("boese@example.com")).willReturn(false);
            given(emailRepository.findByFromAddressIgnoreCase("boese@example.com"))
                    .willReturn(List.of(email));

            mockMvc.perform(post("/api/emails/1/block-sender"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("blocked")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("deleted")));

            verify(emailBlacklistRepository).save(any(EmailBlacklistEntry.class));
            verify(emailImportService).deleteEmailFromServer(email);
            verify(emailRepository).deleteAll(anyList());
            // Wichtig: KEIN Spam-Marking — würde sonst das Bayes-Modell verfälschen.
            verify(emailRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("Absender sperren: E-Mail ohne Absender gibt 400")
        void blockSenderNoAddress() throws Exception {
            Email email = createTestEmail(1L, "Test", null);
            email.setFromAddress(null);
            given(emailRepository.findById(1L)).willReturn(Optional.of(email));

            mockMvc.perform(post("/api/emails/1/block-sender"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Absender sperren: E-Mail nicht gefunden")
        void blockSenderNotFound() throws Exception {
            given(emailRepository.findById(999L)).willReturn(Optional.empty());

            mockMvc.perform(post("/api/emails/999/block-sender"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Absender sperren: Idempotent — zweiter Aufruf legt keine Duplikate an")
        void blockSenderIdempotent() throws Exception {
            Email email = createTestEmail(1L, "Test", "boese@example.com");
            given(emailRepository.findById(1L)).willReturn(Optional.of(email));
            // Absender bereits geblockt
            given(emailBlacklistRepository.existsByEmailAddress("boese@example.com")).willReturn(true);
            given(emailRepository.findByFromAddressIgnoreCase("boese@example.com"))
                    .willReturn(Collections.emptyList());

            mockMvc.perform(post("/api/emails/1/block-sender"))
                    .andExpect(status().isOk());

            // Kein zweiter Blacklist-Eintrag (Duplicate-Key wäre die Folge)
            verify(emailBlacklistRepository, never()).save(any(EmailBlacklistEntry.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DELETE /api/emails/{id}")
    class Delete {

        @Test
        @DisplayName("E-Mail soft löschen (Papierkorb)")
        void softDelete() throws Exception {
            Email email = createTestEmail(1L, "Alte Mail", "test@example.com");
            given(emailRepository.findById(1L)).willReturn(Optional.of(email));

            mockMvc.perform(delete("/api/emails/1"))
                    .andExpect(status().isNoContent());

            verify(emailRepository).save(email);
        }

        @Test
        @DisplayName("E-Mail löschen: nicht gefunden")
        void deleteNotFound() throws Exception {
            given(emailRepository.findById(999L)).willReturn(Optional.empty());

            mockMvc.perform(delete("/api/emails/999"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Permanent löschen: Email vorhanden -> 204 + Replies-Detach VOR Delete (Reihenfolge wichtig!)")
        void deletePermanentlySuccess() throws Exception {
            Email email = createTestEmail(1L, "Zu löschen", "test@example.com");
            given(emailRepository.findByIdForUpdate(1L)).willReturn(Optional.of(email));

            mockMvc.perform(delete("/api/emails/1/permanent"))
                    .andExpect(status().isNoContent());

            // Reihenfolge: detachRepliesFromParent -> flush -> delete.
            // Wenn flush() entfernt würde, könnte Hibernate Replies wiederbeleben (FK).
            InOrder order = inOrder(emailRepository);
            order.verify(emailRepository).detachRepliesFromParent(1L);
            order.verify(emailRepository).flush();
            order.verify(emailRepository).delete(email);
        }

        @Test
        @DisplayName("Permanent löschen ist idempotent (Doppelklick-Race) -> 204 statt 500")
        void deletePermanentlyIdempotentOnRace() throws Exception {
            // Race-Szenario: Erste Anfrage hat Email schon gelöscht, zweite findet
            // sie nicht mehr. Vorher: StaleStateException -> HTTP 500. Jetzt: 204.
            given(emailRepository.findByIdForUpdate(1L)).willReturn(Optional.empty());

            mockMvc.perform(delete("/api/emails/1/permanent"))
                    .andExpect(status().isNoContent());

            // Bei nicht gefundener Email darf KEINE der Folge-Aktionen laufen
            // (kein Delete, kein Reply-Detach, kein Mailserver-Call).
            verify(emailRepository, never()).delete(any(Email.class));
            verify(emailRepository, never()).detachRepliesFromParent(any());
            verifyNoInteractions(emailImportService);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INBOX
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/emails/inbox")
    class Inbox {

        @Test
        @DisplayName("Posteingang gibt E-Mails zurück")
        void inboxReturnsEmails() throws Exception {
            Email email = createTestEmail(1L, "Hallo", "max@example.com");
            given(emailRepository.findUnassigned()).willReturn(Collections.emptyList());
            given(emailRepository.findInboxFiltered()).willReturn(List.of(email));

            mockMvc.perform(get("/api/emails/inbox"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].subject").value("Hallo"));
        }

        @Test
        @DisplayName("Liste liefert Thread-Wurzel, Verlaufsgröße und Vorschau ohne zitierten Verlauf")
        void inboxLiefertThreadWurzelUndBereinigteVorschau() throws Exception {
            Email email = createTestEmail(17L, "Re: Anfrage", "max@example.com");
            email.setCc("erika@example.com");
            email.setBody("Wie gewünscht die Bilder.\nMfG Max\nAm Do., 17. Sept. 2026 um 10:00 Uhr schrieb info@example.com:\n> Altes Angebot "
                    + "x".repeat(300));
            given(emailRepository.findUnassigned()).willReturn(Collections.emptyList());
            given(emailRepository.findInboxFiltered()).willReturn(List.of(email));
            given(emailThreadService.kennzahlenFuer(email)).willReturn(
                    new EmailThreadService.ThreadKennzahlen(16L, 3, LocalDateTime.of(2026, 9, 17, 10, 0)));

            mockMvc.perform(get("/api/emails/inbox"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].threadRootId").value(16))
                    .andExpect(jsonPath("$[0].replyCount").value(2))
                    .andExpect(jsonPath("$[0].cc").value("erika@example.com"))
                    .andExpect(jsonPath("$[0].body").value("Wie gewünscht die Bilder. MfG Max"));
        }

        @Test
        @DisplayName("Liste zeigt bei reinen HTML-Mails eine Vorschau aus dem HTML")
        void inboxVorschauAusHtml() throws Exception {
            Email email = createTestEmail(18L, "Nur HTML", "max@example.com");
            email.setHtmlBody("<p>Neuer Text</p><hr><div><b>Von:</b> a@example.com<br><b>Gesendet:</b> heute<br>"
                    + "<b>An:</b> b@example.com<br><b>Betreff:</b> x</div><p>Alter Text</p>");
            given(emailRepository.findUnassigned()).willReturn(Collections.emptyList());
            given(emailRepository.findInboxFiltered()).willReturn(List.of(email));

            mockMvc.perform(get("/api/emails/inbox"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].body").value("Neuer Text"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ZUORDNUNGS-INFO (Links im E-Mail-Center)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Zuordnungs-Info in Liste und Detail")
    class ZuordnungsInfo {

        private org.example.kalkulationsprogramm.domain.Projekt projekt(String auftragsnummer) {
            org.example.kalkulationsprogramm.domain.Projekt p = new org.example.kalkulationsprogramm.domain.Projekt();
            p.setId(41L);
            p.setBauvorhaben("Garagentor Mustermann");
            p.setAuftragsnummer(auftragsnummer);
            return p;
        }

        @Test
        @DisplayName("Projekt-Ordner liefert ID, Name und Auftragsnummer des Projekts")
        void projektOrdnerLiefertAuftragsnummer() throws Exception {
            Email email = createTestEmail(30L, "Re: Garagentor", "max@example.com");
            email.setZuordnungTyp(EmailZuordnungTyp.PROJEKT);
            email.setProjekt(projekt("2026-041"));
            given(emailRepository.findProjectEmails()).willReturn(List.of(email));

            mockMvc.perform(get("/api/emails/projects"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].zuordnungTyp").value("PROJEKT"))
                    .andExpect(jsonPath("$[0].projektId").value(41))
                    .andExpect(jsonPath("$[0].projektName").value("Garagentor Mustermann"))
                    .andExpect(jsonPath("$[0].projektAuftragsnummer").value("2026-041"));
        }

        @Test
        @DisplayName("Anfrage-Ordner liefert ID und Name der Anfrage, aber keine Auftragsnummer")
        void anfrageOrdnerOhneAuftragsnummer() throws Exception {
            org.example.kalkulationsprogramm.domain.Anfrage anfrage = new org.example.kalkulationsprogramm.domain.Anfrage();
            anfrage.setId(12L);
            anfrage.setBauvorhaben("Geländer Mustermann");
            Email email = createTestEmail(31L, "Aw: Geländer", "max@example.com");
            email.setZuordnungTyp(EmailZuordnungTyp.ANFRAGE);
            email.setAnfrage(anfrage);
            given(emailRepository.findAnfrageEmails()).willReturn(List.of(email));

            mockMvc.perform(get("/api/emails/offers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].anfrageId").value(12))
                    .andExpect(jsonPath("$[0].anfrageName").value("Geländer Mustermann"))
                    .andExpect(jsonPath("$[0].projektId").doesNotExist())
                    .andExpect(jsonPath("$[0].projektAuftragsnummer").doesNotExist());
        }

        @Test
        @DisplayName("Detail liefert Auftragsnummer und Lieferant")
        void detailLiefertAuftragsnummerUndLieferant() throws Exception {
            org.example.kalkulationsprogramm.domain.Lieferanten lieferant = new org.example.kalkulationsprogramm.domain.Lieferanten();
            lieferant.setId(7L);
            lieferant.setLieferantenname("Muster Metall GmbH");
            Email email = createTestEmail(32L, "Lieferung Garagentor", "max@example.com");
            email.setProjekt(projekt("2026-041"));
            email.setLieferant(lieferant);
            given(emailRepository.findById(32L)).willReturn(Optional.of(email));

            mockMvc.perform(get("/api/emails/32"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.projektAuftragsnummer").value("2026-041"))
                    .andExpect(jsonPath("$.lieferantId").value(7))
                    .andExpect(jsonPath("$.lieferantName").value("Muster Metall GmbH"));
        }

        @Test
        @DisplayName("Projekt ohne Auftragsnummer: Feld bleibt leer, Name kommt trotzdem")
        void projektOhneAuftragsnummer() throws Exception {
            Email email = createTestEmail(33L, "Rückfrage", "max@example.com");
            email.setProjekt(projekt(null));
            given(emailRepository.findById(33L)).willReturn(Optional.of(email));

            mockMvc.perform(get("/api/emails/33"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.projektName").value("Garagentor Mustermann"))
                    .andExpect(jsonPath("$.projektAuftragsnummer").doesNotExist());
        }

        @Test
        @DisplayName("Ohne Zuordnung bleiben alle Zuordnungsfelder leer")
        void ohneZuordnungKeineFelder() throws Exception {
            Email email = createTestEmail(34L, "Hallo", "max@example.com");
            given(emailRepository.findById(34L)).willReturn(Optional.of(email));

            mockMvc.perform(get("/api/emails/34"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.zuordnungTyp").value("KEINE"))
                    .andExpect(jsonPath("$.projektId").doesNotExist())
                    .andExpect(jsonPath("$.anfrageId").doesNotExist())
                    .andExpect(jsonPath("$.lieferantId").doesNotExist());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // MARK READ
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/emails/{id}/mark-read")
    class MarkRead {

        @Test
        @DisplayName("E-Mail als gelesen markieren")
        void markReadSuccess() throws Exception {
            Email email = createTestEmail(1L, "Ungelesen", "test@example.com");
            email.setRead(false);
            given(emailRepository.findById(1L)).willReturn(Optional.of(email));

            mockMvc.perform(post("/api/emails/1/mark-read"))
                    .andExpect(status().isOk());

            verify(emailRepository).save(email);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CONTACT SEARCH
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/emails/contacts")
    class ContactSearch {

        @Test
        @DisplayName("Kontaktsuche liefert Ergebnisse")
        void searchContactsReturnsResults() throws Exception {
            List<ContactDto> contacts = List.of(
                    ContactDto.builder()
                            .id("KUNDE_1").name("Max Mustermann")
                            .email("max@example.com").type("KUNDE").context("K-001")
                            .build(),
                    ContactDto.builder()
                            .id("LIEFERANT_2").name("Muster GmbH")
                            .email("info@muster-gmbh.example.com").type("LIEFERANT").context("Stahl")
                            .build());

            given(contactService.searchContacts("muster")).willReturn(contacts);

            mockMvc.perform(get("/api/emails/contacts").param("q", "muster"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].name").value("Max Mustermann"))
                    .andExpect(jsonPath("$[0].email").value("max@example.com"))
                    .andExpect(jsonPath("$[0].type").value("KUNDE"))
                    .andExpect(jsonPath("$[0].context").value("K-001"))
                    .andExpect(jsonPath("$[1].name").value("Muster GmbH"))
                    .andExpect(jsonPath("$[1].type").value("LIEFERANT"));
        }

        @Test
        @DisplayName("Leere Suche liefert leere Liste")
        void searchContactsEmptyQuery() throws Exception {
            given(contactService.searchContacts("x")).willReturn(Collections.emptyList());

            mockMvc.perform(get("/api/emails/contacts").param("q", "x"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("Fehlender q-Parameter liefert 400")
        void searchContactsMissingParam() throws Exception {
            mockMvc.perform(get("/api/emails/contacts"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Sonderzeichen im Suchbegriff werden korrekt weitergeleitet")
        void searchContactsSpecialChars() throws Exception {
            given(contactService.searchContacts("müller & söhne")).willReturn(Collections.emptyList());

            mockMvc.perform(get("/api/emails/contacts").param("q", "müller & söhne"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());

            verify(contactService).searchContacts("müller & söhne");
        }

        @Test
        @DisplayName("Kontaktsuche gibt alle Felder korrekt zurück")
        void searchContactsAllFields() throws Exception {
            ContactDto contact = ContactDto.builder()
                    .id("PROJEKT_42")
                    .name("Bauherr Test")
                    .email("bauherr@example.com")
                    .type("PROJEKT")
                    .context("Neubau Musterstraße 1")
                    .build();

            given(contactService.searchContacts("Bauherr")).willReturn(List.of(contact));

            mockMvc.perform(get("/api/emails/contacts").param("q", "Bauherr"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value("PROJEKT_42"))
                    .andExpect(jsonPath("$[0].name").value("Bauherr Test"))
                    .andExpect(jsonPath("$[0].email").value("bauherr@example.com"))
                    .andExpect(jsonPath("$[0].type").value("PROJEKT"))
                    .andExpect(jsonPath("$[0].context").value("Neubau Musterstraße 1"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // THREAD-ENDPOINT
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/emails/{emailId}/thread")
    class GetThread {

        /** Erstellt ein minimales EmailThreadEntryDto für Tests. */
        private EmailThreadEntryDto makeEntry(long id, String subject, String direction) {
            EmailThreadEntryDto e = new EmailThreadEntryDto();
            e.setId(id);
            e.setSubject(subject);
            e.setFromAddress("max.mustermann@example.com");
            e.setRecipient("handwerk@example.com");
            e.setSentAt("2026-03-10T09:14:00");
            e.setDirection(direction);
            e.setSnippet("Hallo, ich hätte Interesse an einem Angebot für die Sanierung…");
            e.setAttachments(Collections.emptyList());
            return e;
        }

        @Test
        @DisplayName("Happy-Path: Thread mit zwei Einträgen zurückgeben")
        void getThread_happyPath() throws Exception {
            EmailThreadDto dto = new EmailThreadDto();
            dto.setRootEmailId(1L);
            dto.setFocusedEmailId(2L);
            dto.setEmails(List.of(
                    makeEntry(1L, "Anfrage Sanierung Bad", "IN"),
                    makeEntry(2L, "Re: Anfrage Sanierung Bad", "OUT")
            ));

            given(emailThreadService.loadThreadFor(2L)).willReturn(dto);

            mockMvc.perform(get("/api/emails/2/thread"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rootEmailId").value(1))
                    .andExpect(jsonPath("$.focusedEmailId").value(2))
                    .andExpect(jsonPath("$.emails").isArray())
                    .andExpect(jsonPath("$.emails.length()").value(2))
                    .andExpect(jsonPath("$.emails[0].subject").value("Anfrage Sanierung Bad"))
                    .andExpect(jsonPath("$.emails[0].direction").value("IN"))
                    .andExpect(jsonPath("$.emails[1].subject").value("Re: Anfrage Sanierung Bad"))
                    .andExpect(jsonPath("$.emails[1].direction").value("OUT"));
        }

        @Test
        @DisplayName("Single-Email-Thread: Thread mit genau einem Eintrag")
        void getThread_singleEmail() throws Exception {
            EmailThreadDto dto = new EmailThreadDto();
            dto.setRootEmailId(5L);
            dto.setFocusedEmailId(5L);
            dto.setEmails(List.of(makeEntry(5L, "Einzelnachricht", "IN")));

            given(emailThreadService.loadThreadFor(5L)).willReturn(dto);

            mockMvc.perform(get("/api/emails/5/thread"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.emails.length()").value(1))
                    .andExpect(jsonPath("$.rootEmailId").value(5))
                    .andExpect(jsonPath("$.focusedEmailId").value(5));
        }

        @Test
        @DisplayName("Nicht existierende E-Mail gibt 404")
        void getThread_notFound() throws Exception {
            given(emailThreadService.loadThreadFor(999L))
                    .willThrow(new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.NOT_FOUND, "Email not found: 999"));

            mockMvc.perform(get("/api/emails/999/thread"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Thread enthält Anhang-Informationen")
        void getThread_withAttachments() throws Exception {
            EmailThreadEntryDto.AttachmentDto att = new EmailThreadEntryDto.AttachmentDto();
            att.setId(10L);
            att.setOriginalFilename("angebot.pdf");
            att.setMimeType("application/pdf");
            att.setSizeBytes(145_000L);
            att.setInline(false);

            EmailThreadEntryDto entry = makeEntry(1L, "Angebot Sanierung", "OUT");
            entry.setAttachments(List.of(att));

            EmailThreadDto dto = new EmailThreadDto();
            dto.setRootEmailId(1L);
            dto.setFocusedEmailId(1L);
            dto.setEmails(List.of(entry));

            given(emailThreadService.loadThreadFor(1L)).willReturn(dto);

            mockMvc.perform(get("/api/emails/1/thread"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.emails[0].attachments[0].originalFilename").value("angebot.pdf"))
                    .andExpect(jsonPath("$.emails[0].attachments[0].mimeType").value("application/pdf"))
                    .andExpect(jsonPath("$.emails[0].attachments[0].sizeBytes").value(145000));
        }

        @Test
        @DisplayName("Ungültige ID (negativ) gibt 404")
        void getThread_negativeId() throws Exception {
            given(emailThreadService.loadThreadFor(-1L))
                    .willThrow(new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.NOT_FOUND, "Email not found: -1"));

            mockMvc.perform(get("/api/emails/-1/thread"))
                    .andExpect(status().isNotFound());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // BACKFILL-PARENTS
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/emails/backfill-parents")
    class BackfillParents {

        @Test
        @DisplayName("Backfill verknüpft Emails und gibt Anzahl zurück")
        void backfillSuccess() throws Exception {
            given(emailImportService.backfillParentEmails()).willReturn(42);

            mockMvc.perform(post("/api/emails/backfill-parents"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.updatedCount").value(42))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("42")));
        }

        @Test
        @DisplayName("Backfill ohne Treffer gibt 0 zurück")
        void backfillNoUpdates() throws Exception {
            given(emailImportService.backfillParentEmails()).willReturn(0);

            mockMvc.perform(post("/api/emails/backfill-parents"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.updatedCount").value(0));
        }
    }

    @Nested
    @DisplayName("POST /api/emails/admin/backfill-xml-to-pdf")
    class BackfillXmlToPdf {

        @Test
        @DisplayName("Stellt XML-Dokumente auf PDF um und gibt Anzahl zurück")
        void backfillSuccess() throws Exception {
            given(emailAttachmentProcessingService.backfillXmlDokumenteAufPdf()).willReturn(3);

            mockMvc.perform(post("/api/emails/admin/backfill-xml-to-pdf"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ok"))
                    .andExpect(jsonPath("$.updated").value(3));
        }

        @Test
        @DisplayName("Backfill ohne Treffer gibt 0 zurück")
        void backfillNoUpdates() throws Exception {
            given(emailAttachmentProcessingService.backfillXmlDokumenteAufPdf()).willReturn(0);

            mockMvc.perform(post("/api/emails/admin/backfill-xml-to-pdf"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.updated").value(0));
        }

        @Test
        @DisplayName("Fehler im Service wird nicht verschluckt (kein stiller Erfolg)")
        void backfillServiceFehler() {
            given(emailAttachmentProcessingService.backfillXmlDokumenteAufPdf())
                    .willThrow(new RuntimeException("DB nicht erreichbar"));

            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    mockMvc.perform(post("/api/emails/admin/backfill-xml-to-pdf")))
                    .hasRootCauseInstanceOf(RuntimeException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // KUNDE-LOOKUP (Detail-DTO)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/emails/{id} – Kunden-Lookup im Detail-DTO")
    class KundeLookup {

        private org.example.kalkulationsprogramm.domain.Kunde kundeMustermann() {
            org.example.kalkulationsprogramm.domain.Kunde k =
                    new org.example.kalkulationsprogramm.domain.Kunde();
            k.setId(7L);
            k.setKundennummer("K-007");
            k.setName("Max Mustermann");
            return k;
        }

        @Test
        @DisplayName("Eingehende Mail: kundeName aus fromAddress gemappt")
        void incomingEmail_setsKundeName() throws Exception {
            Email email = createTestEmail(100L, "Anfrage Geländer",
                    "\"Max Mustermann\" <max@mustermann.de>");
            email.setDirection(EmailDirection.IN);
            given(emailRepository.findById(100L)).willReturn(Optional.of(email));
            given(kundeRepository.findByKundenEmailIgnoreCase("max@mustermann.de"))
                    .willReturn(List.of(kundeMustermann()));

            mockMvc.perform(get("/api/emails/100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.kundeId").value(7))
                    .andExpect(jsonPath("$.kundeName").value("Max Mustermann"));
        }

        @Test
        @DisplayName("Ausgehende Mail: kundeName aus recipient gemappt")
        void outgoingEmail_setsKundeNameFromRecipient() throws Exception {
            Email email = createTestEmail(101L, "Rechnung", "buero@firma.de");
            email.setDirection(EmailDirection.OUT);
            email.setRecipient("Max Mustermann <max@mustermann.de>");
            given(emailRepository.findById(101L)).willReturn(Optional.of(email));
            given(kundeRepository.findByKundenEmailIgnoreCase("max@mustermann.de"))
                    .willReturn(List.of(kundeMustermann()));

            mockMvc.perform(get("/api/emails/101"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.kundeId").value(7))
                    .andExpect(jsonPath("$.kundeName").value("Max Mustermann"));
        }

        @Test
        @DisplayName("Unbekannte Adresse: kundeName bleibt null")
        void noMatch_leavesKundeNameNull() throws Exception {
            Email email = createTestEmail(102L, "Spam", "fremd@nirgendwo.com");
            given(emailRepository.findById(102L)).willReturn(Optional.of(email));
            given(kundeRepository.findByKundenEmailIgnoreCase("fremd@nirgendwo.com"))
                    .willReturn(List.of());

            mockMvc.perform(get("/api/emails/102"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.kundeId").doesNotExist())
                    .andExpect(jsonPath("$.kundeName").doesNotExist());
        }

        @Test
        @DisplayName("Mehrere Treffer: deterministisch der mit kleinster Id gewinnt")
        void multipleMatches_picksLowestId() throws Exception {
            Email email = createTestEmail(103L, "Sammeladresse",
                    "info@gemeinde-musterstadt.de");
            given(emailRepository.findById(103L)).willReturn(Optional.of(email));
            org.example.kalkulationsprogramm.domain.Kunde k1 =
                    new org.example.kalkulationsprogramm.domain.Kunde();
            k1.setId(42L);
            k1.setKundennummer("K-042");
            k1.setName("Spät angelegt");
            org.example.kalkulationsprogramm.domain.Kunde k2 =
                    new org.example.kalkulationsprogramm.domain.Kunde();
            k2.setId(5L);
            k2.setKundennummer("K-005");
            k2.setName("Früher Kunde");
            // Bewusst in „falscher" Reihenfolge zurückgeben, um Sortierung zu prüfen.
            given(kundeRepository.findByKundenEmailIgnoreCase("info@gemeinde-musterstadt.de"))
                    .willReturn(List.of(k1, k2));

            mockMvc.perform(get("/api/emails/103"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.kundeId").value(5))
                    .andExpect(jsonPath("$.kundeName").value("Früher Kunde"));
        }
    }

    @Nested
    @DisplayName("Postfächer: Absender-Auswahl, fester Absender, Einzelversand")
    class PostfachVersand {

        private org.example.kalkulationsprogramm.domain.EmailAbsender postfach(long id, String adresse) {
            org.example.kalkulationsprogramm.domain.EmailAbsender p = new org.example.kalkulationsprogramm.domain.EmailAbsender();
            p.setId(id);
            p.setEmailAdresse(adresse);
            return p;
        }

        private MockMultipartFile dto(String json) {
            return new MockMultipartFile("dto", "", "application/json", json.getBytes(StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("absender-postfaecher: eigenes Postfach kommt aus der Anmeldung")
        void absenderPostfaecherNutztAnmeldung() throws Exception {
            org.example.kalkulationsprogramm.domain.FrontendUserProfile max = new org.example.kalkulationsprogramm.domain.FrontendUserProfile();
            max.setId(7L);
            given(frontendUserProfileService.findByUsername("max")).willReturn(Optional.of(max));
            given(postfachVersandService.eigenesPostfach(7L)).willReturn(Optional.of(postfach(3L, "max@example.com")));
            given(postfachService.absenderAuswahl(3L)).willReturn(List.of(
                    new org.example.kalkulationsprogramm.dto.Postfach.AbsenderPostfachDto(3L, "max@example.com", "Max Mustermann", true, false),
                    new org.example.kalkulationsprogramm.dto.Postfach.AbsenderPostfachDto(1L, "info@example.com", null, false, true)));

            mockMvc.perform(get("/api/emails/absender-postfaecher")
                            .principal(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("max", null)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(3))
                    .andExpect(jsonPath("$[0].eigenes").value(true))
                    .andExpect(jsonPath("$[1].hauptpostfach").value(true));
        }

        @Test
        @DisplayName("absender-postfaecher: ohne Anmeldung kein eigenes Postfach")
        void absenderPostfaecherOhneAnmeldung() throws Exception {
            given(postfachService.absenderAuswahl(isNull())).willReturn(List.of());

            mockMvc.perform(get("/api/emails/absender-postfaecher"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
            verify(postfachService).absenderAuswahl(isNull());
        }

        @Test
        @DisplayName("send: gewähltes Postfach geht an den Versand, Absender kommt aus dem Postfach")
        void sendNutztGewaehltesPostfach() throws Exception {
            var info = postfach(1L, "info@example.com");
            given(postfachVersandService.postfachFuerNeueMail(eq(1L), isNull(), eq(false))).willReturn(info);
            given(postfachVersandService.versandUeber(info)).willReturn(
                    new org.example.kalkulationsprogramm.service.PostfachVersandService.Versand(
                            new org.example.email.EmailService("h", 465, "u", "p"), "info@example.com", info));
            org.mockito.Mockito.doReturn("<m1@example.com>").when(unifiedEmailController)
                    .sendeSmtpMail(any(), any(), any(), any(), any(), any(), any());

            mockMvc.perform(multipart("/api/emails/send").file(dto(
                            "{\"postfachId\":1,\"sender\":\"falsch@example.org\",\"recipients\":[\"kunde@example.com\"],\"subject\":\"Angebot\",\"body\":\"Hallo\"}")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fromAddress").value("info@example.com"))
                    .andExpect(jsonPath("$.postfaecher[0].emailAdresse").value("info@example.com"));
            verify(unifiedEmailController).sendeSmtpMail(any(), eq("kunde@example.com"), isNull(),
                    eq("info@example.com"), eq("Angebot"), any(), any());
        }

        @Test
        @DisplayName("send: unbekanntes Postfach -> 400 mit Meldung")
        void sendUnbekanntesPostfach() throws Exception {
            given(postfachVersandService.postfachFuerNeueMail(eq(99L), any(), eq(false)))
                    .willThrow(new IllegalArgumentException("Dieses Postfach gibt es nicht (mehr) oder es ist ausgeschaltet. Bitte ein anderes wählen."));

            mockMvc.perform(multipart("/api/emails/send").file(dto(
                            "{\"postfachId\":99,\"recipients\":[\"kunde@example.com\"],\"subject\":\"s\",\"body\":\"b\"}")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Postfach")));
        }

        @Test
        @DisplayName("send: ohne jedes Postfach -> 400 mit Hinweis auf die Einstellungen")
        void sendOhnePostfach() throws Exception {
            given(postfachVersandService.versandUeber(any())).willReturn(
                    new org.example.kalkulationsprogramm.service.PostfachVersandService.Versand(
                            new org.example.email.EmailService("h", 465, "u", "p"), "", null));

            mockMvc.perform(multipart("/api/emails/send").file(dto(
                            "{\"recipients\":[\"kunde@example.com\"],\"subject\":\"s\",\"body\":\"b\"}")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Postfächer")));
        }

        @Test
        @DisplayName("Weiterleiten: Absender fest wie die Original-Mail, postfachId wird ignoriert")
        void weiterleitenNutztEingangsPostfach() throws Exception {
            var rechnungen = postfach(2L, "rechnungen@example.com");
            Email original = createTestEmail(5L, "Rechnung 4711", "lieferant@example.com");
            given(emailRepository.findById(5L)).willReturn(Optional.of(original));
            given(postfachVersandService.antwortPostfach(original)).willReturn(rechnungen);
            given(postfachVersandService.versandUeber(rechnungen)).willReturn(
                    new org.example.kalkulationsprogramm.service.PostfachVersandService.Versand(
                            new org.example.email.EmailService("h", 465, "u", "p"), "rechnungen@example.com", rechnungen));
            org.mockito.Mockito.doReturn("<fw@example.com>").when(unifiedEmailController)
                    .sendeSmtpMail(any(), any(), any(), any(), any(), any(), any());

            mockMvc.perform(multipart("/api/emails/send").file(dto(
                            "{\"weitergeleitetVonEmailId\":5,\"postfachId\":1,\"recipients\":[\"buero@example.com\"],\"subject\":\"WG: Rechnung 4711\",\"body\":\"b\"}")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fromAddress").value("rechnungen@example.com"));
            verify(postfachVersandService, never()).postfachFuerNeueMail(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        }

        @Test
        @DisplayName("Weiterleiten einer unbekannten Mail -> 404")
        void weiterleitenUnbekannteMail() throws Exception {
            given(emailRepository.findById(Long.MAX_VALUE)).willReturn(Optional.empty());

            mockMvc.perform(multipart("/api/emails/send").file(dto(
                            "{\"weitergeleitetVonEmailId\":" + Long.MAX_VALUE + ",\"recipients\":[\"buero@example.com\"],\"subject\":\"s\",\"body\":\"b\"}")))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Einzelversand: jeder Empfänger eine eigene Mail, Teilfehler werden gemeldet")
        void einzelversandMitTeilfehler() throws Exception {
            given(postfachVersandService.pruefeEinzelversand(anyList(), any()))
                    .willReturn(List.of("a@example.com", "b@example.com", "kaputt@example.org"));
            given(postfachVersandService.versendeEinzeln(anyList(), any())).willAnswer(inv -> {
                List<String> empfaenger = inv.getArgument(0);
                org.example.kalkulationsprogramm.service.PostfachVersandService.Einzelsendung<Optional<Email>> sendung = inv.getArgument(1);
                List<Optional<Email>> ok = new java.util.ArrayList<>();
                for (String adresse : empfaenger.subList(0, 2)) {
                    ok.add(sendung.sendeAn(adresse));
                }
                return new org.example.kalkulationsprogramm.service.PostfachVersandService.EinzelversandErgebnis<>(ok,
                        List.of(new org.example.kalkulationsprogramm.service.PostfachVersandService.Fehlschlag(
                                "kaputt@example.org", "Empfänger vom Mail-Server abgelehnt")));
            });
            org.mockito.Mockito.doReturn("<e1@example.com>", "<e2@example.com>").when(unifiedEmailController)
                    .sendeSmtpMail(any(), any(), any(), any(), any(), any(), any());

            mockMvc.perform(multipart("/api/emails/send").file(dto(
                            "{\"einzelversand\":true,\"recipients\":[\"a@example.com\",\"b@example.com\",\"kaputt@example.org\"],\"subject\":\"Betriebsurlaub\",\"body\":\"b\"}")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.verschickt").value(2))
                    .andExpect(jsonPath("$.fehlgeschlagen[0].adresse").value("kaputt@example.org"))
                    .andExpect(jsonPath("$.emails.length()").value(2))
                    .andExpect(jsonPath("$.emails[0].recipient").value("a@example.com"))
                    .andExpect(jsonPath("$.nichtGespeichert").isEmpty());
            // Jede Mail geht an genau einen Empfänger, ohne CC.
            verify(unifiedEmailController).sendeSmtpMail(any(), eq("a@example.com"), isNull(), any(), any(), any(), any());
            verify(unifiedEmailController).sendeSmtpMail(any(), eq("b@example.com"), isNull(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Einzelversand: verschickt, aber Speichern scheitert -> nicht als fehlgeschlagen melden")
        void einzelversandSpeicherfehlerIstKeinVersandfehler() throws Exception {
            given(postfachVersandService.pruefeEinzelversand(anyList(), any())).willReturn(List.of("a@example.com"));
            given(postfachVersandService.versendeEinzeln(anyList(), any())).willAnswer(inv -> {
                org.example.kalkulationsprogramm.service.PostfachVersandService.Einzelsendung<Optional<Email>> sendung = inv.getArgument(1);
                return new org.example.kalkulationsprogramm.service.PostfachVersandService.EinzelversandErgebnis<>(
                        List.of(sendung.sendeAn("a@example.com")), List.of());
            });
            org.mockito.Mockito.doReturn("<e@example.com>").when(unifiedEmailController)
                    .sendeSmtpMail(any(), any(), any(), any(), any(), any(), any());
            given(emailRepository.saveAndFlush(any())).willThrow(new org.springframework.dao.DataIntegrityViolationException("doppelt"));

            mockMvc.perform(multipart("/api/emails/send").file(dto(
                            "{\"einzelversand\":true,\"recipients\":[\"a@example.com\"],\"subject\":\"s\",\"body\":\"b\"}")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.verschickt").value(1))
                    .andExpect(jsonPath("$.fehlgeschlagen").isEmpty())
                    .andExpect(jsonPath("$.nichtGespeichert[0]").value("a@example.com"))
                    .andExpect(jsonPath("$.emails").isEmpty());
        }

        @Test
        @DisplayName("Einzelversand: alles schiefgegangen -> 502 mit Liste")
        void einzelversandAllesFehlgeschlagen() throws Exception {
            given(postfachVersandService.pruefeEinzelversand(anyList(), any())).willReturn(List.of("a@example.com"));
            given(postfachVersandService.versendeEinzeln(anyList(), any())).willReturn(
                    new org.example.kalkulationsprogramm.service.PostfachVersandService.EinzelversandErgebnis<>(List.of(),
                            List.of(new org.example.kalkulationsprogramm.service.PostfachVersandService.Fehlschlag(
                                    "a@example.com", "Anmeldung am Postfach fehlgeschlagen"))));

            mockMvc.perform(multipart("/api/emails/send").file(dto(
                            "{\"einzelversand\":true,\"recipients\":[\"a@example.com\"],\"subject\":\"s\",\"body\":\"b\"}")))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.fehlgeschlagen[0].grund").value("Anmeldung am Postfach fehlgeschlagen"));
        }

        @Test
        @DisplayName("Einzelversand mit CC -> 400")
        void einzelversandMitCc() throws Exception {
            given(postfachVersandService.pruefeEinzelversand(anyList(), any()))
                    .willThrow(new IllegalArgumentException("Beim einzelnen Verschicken bitte keine Kopie-Empfänger eintragen."));

            mockMvc.perform(multipart("/api/emails/send").file(dto(
                            "{\"einzelversand\":true,\"recipients\":[\"a@example.com\"],\"cc\":[\"c@example.com\"],\"subject\":\"s\",\"body\":\"b\"}")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Beim einzelnen Verschicken bitte keine Kopie-Empfänger eintragen."));
            verify(unifiedEmailController, never()).sendeSmtpMail(any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Antwort: Absender fest aus dem Eingangs-Postfach, Postfach landet an der gespeicherten Mail")
        void antwortNutztEingangsPostfach() throws Exception {
            var info = postfach(1L, "info@example.com");
            Email original = createTestEmail(8L, "Anfrage Treppe", "kunde@example.com");
            given(emailRepository.findById(8L)).willReturn(Optional.of(original));
            given(postfachVersandService.antwortPostfach(original)).willReturn(info);
            given(postfachVersandService.versandUeber(info)).willReturn(
                    new org.example.kalkulationsprogramm.service.PostfachVersandService.Versand(
                            new org.example.email.EmailService("h", 465, "u", "p"), "info@example.com", info));
            given(emailThreadService.antwortBezugFuer(original)).willReturn(null);
            org.mockito.Mockito.doReturn("<r@example.com>").when(unifiedEmailController)
                    .sendeSmtpMail(any(), any(), any(), any(), any(), any(), any(), any());

            mockMvc.perform(multipart("/api/emails/8/reply").file(dto(
                            "{\"postfachId\":2,\"recipients\":[\"kunde@example.com\"],\"subject\":\"AW: Anfrage Treppe\",\"body\":\"b\"}")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fromAddress").value("info@example.com"))
                    .andExpect(jsonPath("$.postfaecher[0].id").value(1));
        }
    }

    @Nested
    @DisplayName("Versand: Zuordnung, Anhänge und Grenzen")
    class VersandZweige {

        private MockMultipartFile dto(String json) {
            return new MockMultipartFile("dto", "", "application/json", json.getBytes(StandardCharsets.UTF_8));
        }

        private MockMultipartFile anhang(String name, int groesse) {
            return new MockMultipartFile("attachments", name, "application/pdf", new byte[groesse]);
        }

        private void smtpOk() throws Exception {
            org.mockito.Mockito.doReturn("<v@example.com>").when(unifiedEmailController)
                    .sendeSmtpMail(any(), any(), any(), any(), any(), any(), any());
            org.mockito.Mockito.doReturn("<r@example.com>").when(unifiedEmailController)
                    .sendeSmtpMail(any(), any(), any(), any(), any(), any(), any(), any());
        }

        private Email gespeicherteMail() {
            org.mockito.ArgumentCaptor<Email> captor = org.mockito.ArgumentCaptor.forClass(Email.class);
            verify(emailRepository).saveAndFlush(captor.capture());
            return captor.getValue();
        }

        @Test
        void sendMitProjektUndHochgeladenemAnhang() throws Exception {
            smtpOk();
            org.example.kalkulationsprogramm.domain.Projekt projekt = new org.example.kalkulationsprogramm.domain.Projekt();
            projekt.setId(3L);
            given(projektRepository.findById(3L)).willReturn(Optional.of(projekt));

            mockMvc.perform(multipart("/api/emails/send").file(dto(
                            "{\"projektId\":3,\"recipients\":[\"kunde@example.com\"],\"cc\":[\"chef@example.com\"],\"subject\":\"Plan\",\"body\":\"b\"}"))
                            .file(anhang("../../plan.pdf", 10)))
                    .andExpect(status().isOk());

            Email mail = gespeicherteMail();
            org.junit.jupiter.api.Assertions.assertSame(projekt, mail.getProjekt());
            org.junit.jupiter.api.Assertions.assertEquals("chef@example.com", mail.getCc());
            org.junit.jupiter.api.Assertions.assertEquals(1, mail.getAttachments().size());
            org.junit.jupiter.api.Assertions.assertFalse(mail.getAttachments().get(0).getStoredFilename().contains(".."));
        }

        @Test
        void sendMitAnfrageUndAnfrageDokument() throws Exception {
            smtpOk();
            org.example.kalkulationsprogramm.domain.Anfrage anfrage = new org.example.kalkulationsprogramm.domain.Anfrage();
            given(anfrageRepository.findById(4L)).willReturn(Optional.of(anfrage));
            org.example.kalkulationsprogramm.domain.AnfrageDokument dok = new org.example.kalkulationsprogramm.domain.AnfrageDokument();
            dok.setId(9L);
            dok.setOriginalDateiname("angebot.pdf");
            dok.setGespeicherterDateiname("angebot_9.pdf");
            dok.setDateityp("application/pdf");
            given(anfrageDokumentRepository.findById(9L)).willReturn(Optional.of(dok));
            given(dateiSpeicherService.ladeDokumentAlsResource("angebot_9.pdf"))
                    .willReturn(new org.springframework.core.io.ByteArrayResource(new byte[] { 1, 2 }));

            mockMvc.perform(multipart("/api/emails/send")
                            .file(dto("{\"anfrageId\":4,\"recipients\":[\"kunde@example.com\"],\"subject\":\"Angebot\",\"body\":\"b\"}"))
                            .file(new MockMultipartFile("dokumentId", "", "text/plain", "9".getBytes(StandardCharsets.UTF_8))))
                    .andExpect(status().isOk());

            org.junit.jupiter.api.Assertions.assertSame(anfrage, gespeicherteMail().getAnfrage());
            verify(anfrageDokumentRepository).save(dok);
            org.junit.jupiter.api.Assertions.assertNotNull(dok.getEmailVersandDatum());
        }

        @Test
        void sendMitLieferantUndUnbekanntemDokument() throws Exception {
            smtpOk();
            org.example.kalkulationsprogramm.domain.Lieferanten lieferant = new org.example.kalkulationsprogramm.domain.Lieferanten();
            given(lieferantenRepository.findById(6L)).willReturn(Optional.of(lieferant));

            mockMvc.perform(multipart("/api/emails/send")
                            .file(dto("{\"lieferantId\":6,\"recipients\":[\"lieferant@example.com\"],\"subject\":\"Bestellung\",\"body\":\"b\"}"))
                            .file(new MockMultipartFile("dokumentId", "", "text/plain", "abc".getBytes(StandardCharsets.UTF_8))))
                    .andExpect(status().isOk());

            org.junit.jupiter.api.Assertions.assertSame(lieferant, gespeicherteMail().getLieferant());
        }

        @Test
        void sendAnfrageDokumentOhneDateiWirdUebersprungen() throws Exception {
            smtpOk();
            given(anfrageDokumentRepository.findById(9L)).willReturn(Optional.empty());

            mockMvc.perform(multipart("/api/emails/send")
                            .file(dto("{\"anfrageId\":4,\"recipients\":[\"kunde@example.com\"],\"subject\":\"s\",\"body\":\"b\"}"))
                            .file(new MockMultipartFile("dokumentId", "", "text/plain", "9".getBytes(StandardCharsets.UTF_8))))
                    .andExpect(status().isOk());
        }

        @Test
        void sendZuGrosserAnhang400() throws Exception {
            mockMvc.perform(multipart("/api/emails/send")
                            .file(dto("{\"recipients\":[\"kunde@example.com\"],\"subject\":\"s\",\"body\":\"b\"}"))
                            .file(anhang("riesig.pdf", (int) UnifiedEmailController.MAX_SINGLE_ATTACHMENT_BYTES + 1)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("15 MB")));
        }

        @Test
        void sendAnhaengeInSummeZuGross400() throws Exception {
            int dreizehnMb = 13 * 1024 * 1024;
            mockMvc.perform(multipart("/api/emails/send")
                            .file(dto("{\"recipients\":[\"kunde@example.com\"],\"subject\":\"s\",\"body\":\"b\"}"))
                            .file(anhang("a.pdf", dreizehnMb)).file(anhang("b.pdf", dreizehnMb)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("25 MB")));
        }

        @Test
        void sendOhneEmpfaenger400() throws Exception {
            mockMvc.perform(multipart("/api/emails/send").file(dto("{\"subject\":\"s\",\"body\":\"b\"}")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void sendMitUnbekanntemAngemeldetemBenutzerNimmtMitgeschickteId() throws Exception {
            smtpOk();
            given(frontendUserProfileService.findByUsername("unbekannt")).willReturn(Optional.empty());

            mockMvc.perform(multipart("/api/emails/send")
                            .file(dto("{\"frontendUserId\":7,\"recipients\":[\"kunde@example.com\"],\"subject\":\"s\",\"body\":\"b\"}"))
                            .principal(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("unbekannt", null)))
                    .andExpect(status().isOk());

            verify(postfachVersandService).postfachFuerNeueMail(isNull(), eq(7L), eq(false));
        }

        @Test
        void sendGeschaeftsdokumentGibtKennzeichenWeiter() throws Exception {
            smtpOk();

            mockMvc.perform(multipart("/api/emails/send")
                            .file(dto("{\"geschaeftsdokument\":true,\"recipients\":[\"kunde@example.com\"],\"subject\":\"Rechnung\",\"body\":\"b\"}")))
                    .andExpect(status().isOk());

            verify(postfachVersandService).postfachFuerNeueMail(isNull(), isNull(), eq(true));
        }

        @Test
        void antwortAufUnbekannteMail404() throws Exception {
            given(emailRepository.findById(Long.MAX_VALUE)).willReturn(Optional.empty());

            mockMvc.perform(multipart("/api/emails/" + Long.MAX_VALUE + "/reply")
                            .file(dto("{\"recipients\":[\"kunde@example.com\"],\"subject\":\"s\",\"body\":\"b\"}")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void antwortOhnePostfach400() throws Exception {
            Email original = createTestEmail(8L, "Anfrage", "kunde@example.com");
            given(emailRepository.findById(8L)).willReturn(Optional.of(original));
            given(postfachVersandService.versandUeber(any())).willReturn(
                    new org.example.kalkulationsprogramm.service.PostfachVersandService.Versand(
                            new org.example.email.EmailService("h", 465, "u", "p"), null, null));

            mockMvc.perform(multipart("/api/emails/8/reply")
                            .file(dto("{\"recipients\":[\"kunde@example.com\"],\"subject\":\"s\",\"body\":\"b\"}")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Postfächer")));
        }

        @Test
        void antwortErbtZuordnungDerOriginalMailUndSpeichertAnhang() throws Exception {
            smtpOk();
            Email original = createTestEmail(8L, "Anfrage", "kunde@example.com");
            org.example.kalkulationsprogramm.domain.Lieferanten lieferant = new org.example.kalkulationsprogramm.domain.Lieferanten();
            original.assignToLieferant(lieferant);
            given(emailRepository.findById(8L)).willReturn(Optional.of(original));

            mockMvc.perform(multipart("/api/emails/8/reply")
                            .file(dto("{\"recipients\":[\"kunde@example.com\"],\"subject\":\"AW: Anfrage\",\"body\":\"b\"}"))
                            .file(anhang("skizze.pdf", 5)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fromAddress").value("absender@example.com"));
        }

        @Test
        void antwortMitProjektAusDtoUndAnfrageDerOriginalMail() throws Exception {
            smtpOk();
            org.example.kalkulationsprogramm.domain.Projekt projekt = new org.example.kalkulationsprogramm.domain.Projekt();
            given(projektRepository.findById(3L)).willReturn(Optional.of(projekt));
            Email original = createTestEmail(8L, "Anfrage", "kunde@example.com");
            original.assignToAnfrage(new org.example.kalkulationsprogramm.domain.Anfrage());
            given(emailRepository.findById(8L)).willReturn(Optional.of(original));

            mockMvc.perform(multipart("/api/emails/8/reply")
                            .file(dto("{\"projektId\":3,\"recipients\":[\"kunde@example.com\"],\"subject\":\"AW\",\"body\":\"b\"}")))
                    .andExpect(status().isOk());
            mockMvc.perform(multipart("/api/emails/8/reply")
                            .file(dto("{\"recipients\":[\"kunde@example.com\"],\"subject\":\"AW\",\"body\":\"b\"}")))
                    .andExpect(status().isOk());
        }

        @Test
        void antwortZuGrosserAnhang400() throws Exception {
            Email original = createTestEmail(8L, "Anfrage", "kunde@example.com");
            given(emailRepository.findById(8L)).willReturn(Optional.of(original));

            mockMvc.perform(multipart("/api/emails/8/reply")
                            .file(dto("{\"recipients\":[\"kunde@example.com\"],\"subject\":\"s\",\"body\":\"b\"}"))
                            .file(anhang("riesig.pdf", (int) UnifiedEmailController.MAX_SINGLE_ATTACHMENT_BYTES + 1)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void leereAnhaengeWerdenUebersprungen() throws Exception {
            smtpOk();
            Email original = createTestEmail(8L, "Anfrage", "kunde@example.com");
            original.assignToProjekt(new org.example.kalkulationsprogramm.domain.Projekt());
            given(emailRepository.findById(8L)).willReturn(Optional.of(original));
            MockMultipartFile leer = new MockMultipartFile("attachments", "leer.pdf", "application/pdf", new byte[0]);

            mockMvc.perform(multipart("/api/emails/send")
                            .file(dto("{\"recipients\":[\"kunde@example.com\"],\"cc\":[],\"subject\":\"s\"}"))
                            .file(leer))
                    .andExpect(status().isOk());
            mockMvc.perform(multipart("/api/emails/8/reply")
                            .file(dto("{\"recipients\":[\"kunde@example.com\"],\"subject\":\"AW\"}"))
                            .file(leer))
                    .andExpect(status().isOk());
        }

        @Test
        void zuGrossesDokument400() throws Exception {
            org.example.kalkulationsprogramm.domain.ProjektDokument dok = new org.example.kalkulationsprogramm.domain.ProjektDokument();
            dok.setId(5L);
            dok.setOriginalDateiname("plan.pdf");
            dok.setGespeicherterDateiname("plan_5.pdf");
            given(projektDokumentRepository.findById(5L)).willReturn(Optional.of(dok));
            org.springframework.core.io.Resource riesig = org.mockito.Mockito.mock(org.springframework.core.io.Resource.class);
            given(riesig.exists()).willReturn(true);
            given(riesig.contentLength()).willReturn(UnifiedEmailController.MAX_SINGLE_ATTACHMENT_BYTES + 1);
            given(dateiSpeicherService.ladeDokumentAlsResource("plan_5.pdf")).willReturn(riesig);

            mockMvc.perform(multipart("/api/emails/send")
                            .file(dto("{\"recipients\":[\"kunde@example.com\"],\"subject\":\"s\",\"body\":\"b\"}"))
                            .file(new MockMultipartFile("dokumentId", "", "text/plain", "5".getBytes(StandardCharsets.UTF_8))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("15 MB")));
        }

        @Test
        void fehlendeDokumentDateiUndRechnungsdokument() throws Exception {
            smtpOk();
            org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument rechnung =
                    new org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument();
            rechnung.setId(6L);
            rechnung.setOriginalDateiname("rechnung.pdf");
            rechnung.setGespeicherterDateiname("rechnung_6.pdf");
            rechnung.setDateityp(" ");
            given(projektDokumentRepository.findById(6L)).willReturn(Optional.of(rechnung));
            org.springframework.core.io.Resource weg = org.mockito.Mockito.mock(org.springframework.core.io.Resource.class);
            given(weg.exists()).willReturn(false);
            given(dateiSpeicherService.ladeDokumentAlsResource("rechnung_6.pdf")).willReturn(weg);

            mockMvc.perform(multipart("/api/emails/send")
                            .file(dto("{\"recipients\":[\"kunde@example.com\"],\"subject\":\"Rechnung\",\"body\":\"b\"}"))
                            .file(new MockMultipartFile("dokumentId", "", "text/plain", "6".getBytes(StandardCharsets.UTF_8))))
                    .andExpect(status().isOk());

            // Rechnung erkannt -> Postfach für Geschäftsdokumente.
            verify(postfachVersandService).postfachFuerNeueMail(isNull(), isNull(), eq(true));
        }

        @Test
        void projektDokumentOhneGespeicherteDateiWirdIgnoriert() throws Exception {
            smtpOk();
            org.example.kalkulationsprogramm.domain.ProjektDokument dok = new org.example.kalkulationsprogramm.domain.ProjektDokument();
            dok.setId(7L);
            given(projektDokumentRepository.findById(7L)).willReturn(Optional.of(dok));

            mockMvc.perform(multipart("/api/emails/send")
                            .file(dto("{\"recipients\":[\"kunde@example.com\"],\"subject\":\"s\",\"body\":\"b\"}"))
                            .file(new MockMultipartFile("dokumentId", "", "text/plain", " ".getBytes(StandardCharsets.UTF_8))))
                    .andExpect(status().isOk());
            mockMvc.perform(multipart("/api/emails/send")
                            .file(dto("{\"recipients\":[\"kunde@example.com\"],\"subject\":\"s\",\"body\":\"b\"}"))
                            .file(new MockMultipartFile("dokumentId", "", "text/plain", "7".getBytes(StandardCharsets.UTF_8))))
                    .andExpect(status().isOk());
        }

        @Test
        void antwortMitAnfrageOderLieferantAusDemRequest() throws Exception {
            smtpOk();
            Email original = createTestEmail(8L, "Anfrage", "kunde@example.com");
            given(emailRepository.findById(8L)).willReturn(Optional.of(original));
            given(anfrageRepository.findById(4L)).willReturn(Optional.of(new org.example.kalkulationsprogramm.domain.Anfrage()));
            given(lieferantenRepository.findById(6L)).willReturn(Optional.of(new org.example.kalkulationsprogramm.domain.Lieferanten()));

            mockMvc.perform(multipart("/api/emails/8/reply")
                            .file(dto("{\"anfrageId\":4,\"recipients\":[\"kunde@example.com\"],\"subject\":\"AW\",\"body\":\"b\"}")))
                    .andExpect(status().isOk());
            mockMvc.perform(multipart("/api/emails/8/reply")
                            .file(dto("{\"lieferantId\":6,\"recipients\":[\"kunde@example.com\"],\"subject\":\"AW\",\"body\":\"b\"}")))
                    .andExpect(status().isOk());
        }

        @Test
        void listeZeigtPostfachSchild() throws Exception {
            Email mail = createTestEmail(1L, "Anfrage", "kunde@example.com");
            org.example.kalkulationsprogramm.domain.EmailAbsender info = new org.example.kalkulationsprogramm.domain.EmailAbsender();
            info.setId(1L);
            info.setEmailAdresse("info@example.com");
            mail.ordnePostfachZu(info, "INBOX", 1L);
            given(emailRepository.searchGlobal("Anfrage")).willReturn(List.of(mail));
            given(postfachVersandService.antwortPostfach(any())).willReturn(info);

            // Liste: Schild ja, Antwort-Postfach nein (sonst eine Abfrage pro Zeile).
            mockMvc.perform(get("/api/emails/search").param("q", "Anfrage"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].postfaecher[0].emailAdresse").value("info@example.com"))
                    .andExpect(jsonPath("$[0].antwortPostfach").doesNotExist());
            verify(postfachVersandService, never()).antwortPostfach(any());

            // Detail: mit festem Antwort-Postfach.
            given(emailRepository.findById(1L)).willReturn(Optional.of(mail));
            mockMvc.perform(get("/api/emails/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.antwortPostfach.id").value(1));
        }

        @Test
        void listeOhneZuordnungslisteLeer() throws Exception {
            Email mail = createTestEmail(1L, "Anfrage", "kunde@example.com");
            mail.setPostfachZuordnungen(null);
            given(emailRepository.searchGlobal("Anfrage")).willReturn(List.of(mail));

            mockMvc.perform(get("/api/emails/search").param("q", "Anfrage"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].postfaecher").isEmpty())
                    .andExpect(jsonPath("$[0].antwortPostfach").doesNotExist());
        }
    }

    @Nested
    @DisplayName("Attachment Download Tests")
    class AttachmentDownloadTests {
        @Test
        @DisplayName("downloadAttachment: Anhang eines gesperrten Dokumenttyps (Rechnung per Mail) -> 404")
        void downloadAttachment_gesperrterDokumenttypGibt404() throws Exception {
            Email email = createTestEmail(210L, "Test", "absender@example.com");
            EmailAttachment att = new EmailAttachment();
            att.setId(510L);
            att.setEmail(email);
            att.setOriginalFilename("rechnung.pdf");
            att.setStoredFilename("test-gesperrt.pdf");
            email.setAttachments(List.of(att));
            given(emailRepository.findById(210L)).willReturn(Optional.of(email));
            given(lieferantDokumentZugriffService.istAnhangSichtbar(eq(510L), any())).willReturn(false);

            mockMvc.perform(get("/api/emails/210/attachments/510"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("downloadAttachment: ohne Anmeldung -> 401, es wird nichts geladen")
        void downloadAttachment_ohneAnmeldungGibt401() throws Exception {
            given(lieferantDokumentZugriffService.sichtbareTypen(any(), any())).willReturn(Optional.empty());

            mockMvc.perform(get("/api/emails/210/attachments/510"))
                    .andExpect(status().isUnauthorized());

            org.mockito.Mockito.verifyNoInteractions(emailRepository);
        }

        @Test
        @DisplayName("download-all: gesperrter Anhang fehlt im ZIP, freier Anhang bleibt")
        void downloadAll_ueberspringtGesperrteAnhaenge() throws Exception {
            java.nio.file.Path tempDir = java.nio.file.Path.of("target/test-attachments");
            java.nio.file.Files.createDirectories(tempDir);
            java.nio.file.Files.writeString(tempDir.resolve("zip-frei.pdf"), "%PDF-1.4 frei");
            java.nio.file.Files.writeString(tempDir.resolve("zip-gesperrt.pdf"), "%PDF-1.4 gesperrt");
            Email email = createTestEmail(220L, "Test", "absender@example.com");
            EmailAttachment frei = new EmailAttachment();
            frei.setId(520L);
            frei.setEmail(email);
            frei.setOriginalFilename("frei.pdf");
            frei.setStoredFilename("zip-frei.pdf");
            EmailAttachment gesperrt = new EmailAttachment();
            gesperrt.setId(521L);
            gesperrt.setEmail(email);
            gesperrt.setOriginalFilename("rechnung.pdf");
            gesperrt.setStoredFilename("zip-gesperrt.pdf");
            email.setAttachments(List.of(frei, gesperrt));
            given(emailRepository.findById(220L)).willReturn(Optional.of(email));
            given(lieferantDokumentZugriffService.istAnhangSichtbar(eq(521L), any())).willReturn(false);

            byte[] zip = mockMvc.perform(get("/api/emails/220/attachments/download-all"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();

            java.util.List<String> eintraege = new java.util.ArrayList<>();
            try (var zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
                for (var e = zis.getNextEntry(); e != null; e = zis.getNextEntry()) {
                    eintraege.add(e.getName());
                }
            }
            org.junit.jupiter.api.Assertions.assertEquals(List.of("frei.pdf"), eintraege);
        }

        @Test
        @DisplayName("download-all: nur gesperrte Anhänge -> 204; ohne Anmeldung -> 401")
        void downloadAll_nurGesperrteOderOhneAnmeldung() throws Exception {
            Email email = createTestEmail(221L, "Test", "absender@example.com");
            EmailAttachment gesperrt = new EmailAttachment();
            gesperrt.setId(522L);
            gesperrt.setEmail(email);
            gesperrt.setOriginalFilename("rechnung.pdf");
            gesperrt.setStoredFilename("zip-gesperrt.pdf");
            email.setAttachments(List.of(gesperrt));
            given(emailRepository.findById(221L)).willReturn(Optional.of(email));
            given(lieferantDokumentZugriffService.istAnhangSichtbar(eq(522L), any())).willReturn(false);

            mockMvc.perform(get("/api/emails/221/attachments/download-all"))
                    .andExpect(status().isNoContent());

            given(lieferantDokumentZugriffService.sichtbareTypen(any(), any())).willReturn(Optional.empty());
            mockMvc.perform(get("/api/emails/221/attachments/download-all"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("downloadAttachment: freier Anhang wird für Nicht-Admin ausgeliefert")
        void downloadAttachment_freierAnhangWirdAusgeliefert() throws Exception {
            java.nio.file.Path tempDir = java.nio.file.Path.of("target/test-attachments");
            java.nio.file.Files.createDirectories(tempDir);
            java.nio.file.Files.writeString(tempDir.resolve("test-frei.pdf"), "%PDF-1.4 dummy");
            Email email = createTestEmail(230L, "Test", "absender@example.com");
            EmailAttachment att = new EmailAttachment();
            att.setId(530L);
            att.setEmail(email);
            att.setOriginalFilename("foto.pdf");
            att.setStoredFilename("test-frei.pdf");
            att.setMimeType("application/pdf");
            email.setAttachments(List.of(att));
            given(emailRepository.findById(230L)).willReturn(Optional.of(email));
            given(lieferantDokumentZugriffService.sichtbareTypen(any(), any())).willReturn(Optional.of(
                    java.util.EnumSet.of(org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.LIEFERSCHEIN)));
            given(lieferantDokumentZugriffService.istAnhangSichtbar(eq(530L), any())).willReturn(true);

            mockMvc.perform(get("/api/emails/230/attachments/530")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("downloadAttachment bereinigt CRLF aus MIME-Type")
        void downloadAttachment_bereinigtCrlfAusMimeType() throws Exception {
            java.nio.file.Path tempDir = java.nio.file.Path.of("target/test-attachments");
            java.nio.file.Files.createDirectories(tempDir);
            java.nio.file.Path dummyFile = tempDir.resolve("test-crlf.pdf");
            java.nio.file.Files.writeString(dummyFile, "%PDF-1.4 dummy");

            Email email = createTestEmail(200L, "Test", "absender@example.com");
            EmailAttachment att = new EmailAttachment();
            att.setId(501L);
            att.setEmail(email);
            att.setOriginalFilename("beleg.pdf");
            att.setStoredFilename("test-crlf.pdf");
            att.setMimeType("application/pdf;\r\n name=\"beleg.pdf\"");
            email.setAttachments(List.of(att));

            given(emailRepository.findById(200L)).willReturn(Optional.of(email));

            mockMvc.perform(get("/api/emails/200/attachments/501"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/pdf"));
        }

        @Test
        @DisplayName("downloadAttachment kodiert Nicht-ASCII Unicode im Content-Disposition Header (RFC 5987)")
        void downloadAttachment_kodiertNichtAsciiDateinamen() throws Exception {
            java.nio.file.Path tempDir = java.nio.file.Path.of("target/test-attachments");
            java.nio.file.Files.createDirectories(tempDir);
            java.nio.file.Path dummyFile = tempDir.resolve("test-unicode.pdf");
            java.nio.file.Files.writeString(dummyFile, "%PDF-1.4 dummy");

            Email email = createTestEmail(201L, "Test", "absender@example.com");
            EmailAttachment att = new EmailAttachment();
            att.setId(502L);
            att.setEmail(email);
            att.setOriginalFilename("Lebenslauf JAVİD MEHRDAD_Überweisung.pdf");
            att.setStoredFilename("test-unicode.pdf");
            att.setMimeType("application/pdf");
            email.setAttachments(List.of(att));

            given(emailRepository.findById(201L)).willReturn(Optional.of(email));

            var result = mockMvc.perform(get("/api/emails/201/attachments/502"))
                    .andExpect(status().isOk())
                    .andReturn();

            String disposition = result.getResponse().getHeader(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION);
            org.junit.jupiter.api.Assertions.assertNotNull(disposition);
            org.junit.jupiter.api.Assertions.assertTrue(disposition.contains("filename*="));
        }
    }

    @Nested
    @DisplayName("Email Send Attachment & Upload Limit Tests")
    class EmailSendAttachmentTests {

        @Test
        @DisplayName("sendEmail lehnt Anhang ab, der 15 MB Einzelgrenze überschreitet (HTTP 400)")
        void sendEmail_lehntUebergrossenEinzelanhangAb() throws Exception {
            MockMultipartFile dtoPart = new MockMultipartFile("dto", "", "application/json",
                    """
                    {
                        "recipients": ["empfaenger@example.com"],
                        "subject": "Großer Anhang Test",
                        "body": "Hallo"
                    }
                    """.getBytes(StandardCharsets.UTF_8));

            MockMultipartFile oversizedFile = new MockMultipartFile("attachments", "riesig.zip", "application/zip", new byte[10]) {
                @Override
                public long getSize() {
                    return 16 * 1024 * 1024L; // 16 MB
                }
            };

            mockMvc.perform(multipart("/api/emails/send")
                            .file(dtoPart)
                            .file(oversizedFile))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("15 MB")));
        }

        @Test
        @DisplayName("sendEmail lehnt Anhänge ab, deren Summe 25 MB übersteigt (HTTP 400)")
        void sendEmail_lehntUebergrosseGesamtAnhaengeAb() throws Exception {
            MockMultipartFile dtoPart = new MockMultipartFile("dto", "", "application/json",
                    """
                    {
                        "recipients": ["empfaenger@example.com"],
                        "subject": "Summen-Test",
                        "body": "Hallo"
                    }
                    """.getBytes(StandardCharsets.UTF_8));

            MockMultipartFile file1 = new MockMultipartFile("attachments", "teil1.zip", "application/zip", new byte[10]) {
                @Override
                public long getSize() {
                    return 14 * 1024 * 1024L;
                }
            };
            MockMultipartFile file2 = new MockMultipartFile("attachments", "teil2.zip", "application/zip", new byte[10]) {
                @Override
                public long getSize() {
                    return 12 * 1024 * 1024L;
                }
            };

            mockMvc.perform(multipart("/api/emails/send")
                            .file(dtoPart)
                            .file(file1)
                            .file(file2))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("25 MB")));
        }

        @Test
        @DisplayName("replyToEmail lehnt Anhang ab, der 15 MB Einzelgrenze überschreitet (HTTP 400)")
        void replyToEmail_lehntUebergrossenEinzelanhangAb() throws Exception {
            Email parent = createTestEmail(300L, "Parent", "kunde@example.com");
            given(emailRepository.findById(300L)).willReturn(Optional.of(parent));

            MockMultipartFile dtoPart = new MockMultipartFile("dto", "", "application/json",
                    """
                    {
                        "recipients": ["kunde@example.com"],
                        "subject": "Re: Antwort",
                        "body": "Hallo"
                    }
                    """.getBytes(StandardCharsets.UTF_8));

            MockMultipartFile oversizedFile = new MockMultipartFile("attachments", "riesig.zip", "application/zip", new byte[10]) {
                @Override
                public long getSize() {
                    return 16 * 1024 * 1024L; // 16 MB
                }
            };

            mockMvc.perform(multipart("/api/emails/300/reply")
                            .file(dtoPart)
                            .file(oversizedFile))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("15 MB")));
        }

        @Test
        @DisplayName("replyToEmail lehnt Anhänge ab, deren Summe 25 MB übersteigt (HTTP 400)")
        void replyToEmail_lehntUebergrosseGesamtAnhaengeAb() throws Exception {
            Email parent = createTestEmail(301L, "Parent", "kunde@example.com");
            given(emailRepository.findById(301L)).willReturn(Optional.of(parent));

            MockMultipartFile dtoPart = new MockMultipartFile("dto", "", "application/json",
                    """
                    {
                        "recipients": ["kunde@example.com"],
                        "subject": "Re: Antwort",
                        "body": "Hallo"
                    }
                    """.getBytes(StandardCharsets.UTF_8));

            MockMultipartFile file1 = new MockMultipartFile("attachments", "teil1.zip", "application/zip", new byte[10]) {
                @Override
                public long getSize() {
                    return 14 * 1024 * 1024L;
                }
            };
            MockMultipartFile file2 = new MockMultipartFile("attachments", "teil2.zip", "application/zip", new byte[10]) {
                @Override
                public long getSize() {
                    return 12 * 1024 * 1024L;
                }
            };

            mockMvc.perform(multipart("/api/emails/301/reply")
                            .file(dtoPart)
                            .file(file1)
                            .file(file2))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("25 MB")));
        }

        @Test
        @DisplayName("sendEmail übernimmt echten dateityp des ProjektDokuments (PNG) statt pauschal PDF")
        void sendEmail_uebernimmtEchtenDateitypVonProjektDokument() throws Exception {
            MockMultipartFile dtoPart = new MockMultipartFile("dto", "", "application/json",
                    """
                    {
                        "sender": "absender@example.com",
                        "recipients": ["kunde@example.com"],
                        "subject": "Plan als Bild",
                        "body": "Hier ist der Plan"
                    }
                    """.getBytes(StandardCharsets.UTF_8));

            MockMultipartFile dokIdPart = new MockMultipartFile("dokumentId", "", "text/plain", "777".getBytes(StandardCharsets.UTF_8));

            org.example.kalkulationsprogramm.domain.ProjektDokument dok = new org.example.kalkulationsprogramm.domain.ProjektDokument();
            dok.setId(777L);
            dok.setOriginalDateiname("zeichnung.png");
            dok.setGespeicherterDateiname("zeichnung_123.png");
            dok.setDateityp("image/png");

            given(projektDokumentRepository.findById(777L)).willReturn(Optional.of(dok));

            org.springframework.core.io.ByteArrayResource res = new org.springframework.core.io.ByteArrayResource(new byte[]{1, 2, 3}) {
                @Override
                public String getFilename() {
                    return "zeichnung_123.png";
                }
            };
            given(dateiSpeicherService.ladeDokumentAlsResource("zeichnung_123.png")).willReturn(res);

            org.example.kalkulationsprogramm.service.SystemSettingsService.MailKonto konto =
                    new org.example.kalkulationsprogramm.service.SystemSettingsService.MailKonto(
                            "mail.example.com", 587, "user", "pass", "absender@example.com", "Firma");
            given(systemSettingsService.getStandardMailKonto()).willReturn(konto);
            given(emailAbsenderService.findActiveEmailAddresses()).willReturn(List.of("absender@example.com"));
            org.example.kalkulationsprogramm.domain.EmailAbsender absender = new org.example.kalkulationsprogramm.domain.EmailAbsender();
            absender.setEmailAdresse("absender@example.com");

            org.mockito.Mockito.doReturn("msg-id-777").when(unifiedEmailController).sendeSmtpMail(
                    any(), any(), any(), any(), any(), any(), any());

            mockMvc.perform(multipart("/api/emails/send")
                            .file(dtoPart)
                            .file(dokIdPart))
                    .andExpect(status().isOk());

            // 1. Prüfe, dass das gespeicherte EmailAttachment den Typ "image/png" hat (nicht pauschal "application/pdf")
            org.mockito.ArgumentCaptor<Email> emailCaptor = org.mockito.ArgumentCaptor.forClass(Email.class);
            verify(emailRepository).save(emailCaptor.capture());
            Email saved = emailCaptor.getValue();
            org.junit.jupiter.api.Assertions.assertNotNull(saved.getAttachments());
            org.junit.jupiter.api.Assertions.assertEquals(1, saved.getAttachments().size());
            org.junit.jupiter.api.Assertions.assertEquals("image/png", saved.getAttachments().get(0).getMimeType());

            // 2. Prüfe, dass der SMTP-Versand ebenfalls "image/png" als Attachment-Typ erhalten hat
            @SuppressWarnings("unchecked")
            org.mockito.ArgumentCaptor<List<org.example.email.EmailService.Attachment>> attachCaptor =
                    org.mockito.ArgumentCaptor.forClass(List.class);
            verify(unifiedEmailController).sendeSmtpMail(
                    any(), any(), any(), any(), any(), any(), attachCaptor.capture());
            List<org.example.email.EmailService.Attachment> sentAttachments = attachCaptor.getValue();
            org.junit.jupiter.api.Assertions.assertEquals(1, sentAttachments.size());
            org.junit.jupiter.api.Assertions.assertEquals("image/png", sentAttachments.get(0).mimeType());
        }

        @Test
        @DisplayName("sendEmail fällt bei Dokument ohne dateityp auf application/octet-stream zurück")
        void sendEmail_faelltBeiFehlendemDateitypAufOctetStreamZurueck() throws Exception {
            MockMultipartFile dtoPart = new MockMultipartFile("dto", "", "application/json",
                    """
                    {
                        "sender": "absender@example.com",
                        "recipients": ["kunde@example.com"],
                        "subject": "CAD-Datei",
                        "body": "Hier ist die Datei"
                    }
                    """.getBytes(StandardCharsets.UTF_8));

            MockMultipartFile dokIdPart = new MockMultipartFile("dokumentId", "", "text/plain", "888".getBytes(StandardCharsets.UTF_8));

            org.example.kalkulationsprogramm.domain.ProjektDokument dok = new org.example.kalkulationsprogramm.domain.ProjektDokument();
            dok.setId(888L);
            dok.setOriginalDateiname("modell.xyz");
            dok.setGespeicherterDateiname("modell_888.xyz");
            dok.setDateityp(null); // kein dateityp hinterlegt

            given(projektDokumentRepository.findById(888L)).willReturn(Optional.of(dok));

            org.springframework.core.io.ByteArrayResource res = new org.springframework.core.io.ByteArrayResource(new byte[]{4, 5, 6}) {
                @Override
                public String getFilename() {
                    return "modell_888.xyz";
                }
            };
            given(dateiSpeicherService.ladeDokumentAlsResource("modell_888.xyz")).willReturn(res);

            org.example.kalkulationsprogramm.service.SystemSettingsService.MailKonto konto =
                    new org.example.kalkulationsprogramm.service.SystemSettingsService.MailKonto(
                            "mail.example.com", 587, "user", "pass", "absender@example.com", "Firma");
            given(systemSettingsService.getStandardMailKonto()).willReturn(konto);
            given(emailAbsenderService.findActiveEmailAddresses()).willReturn(List.of("absender@example.com"));
            org.example.kalkulationsprogramm.domain.EmailAbsender absender = new org.example.kalkulationsprogramm.domain.EmailAbsender();
            absender.setEmailAdresse("absender@example.com");

            org.mockito.Mockito.doReturn("msg-id-888").when(unifiedEmailController).sendeSmtpMail(
                    any(), any(), any(), any(), any(), any(), any());

            mockMvc.perform(multipart("/api/emails/send")
                            .file(dtoPart)
                            .file(dokIdPart))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Email> emailCaptor = org.mockito.ArgumentCaptor.forClass(Email.class);
            verify(emailRepository).save(emailCaptor.capture());
            Email saved = emailCaptor.getValue();
            org.junit.jupiter.api.Assertions.assertNotNull(saved.getAttachments());
            org.junit.jupiter.api.Assertions.assertEquals("application/octet-stream", saved.getAttachments().get(0).getMimeType());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ANTWORTEN MIT CC / REPLY-TO / ANTWORT-BEZUG
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/emails/{id}/reply – Empfänger, CC und Verlaufsbezug")
    class ReplyMitCc {

        private final org.example.email.EmailService.AntwortBezug bezug =
                new org.example.email.EmailService.AntwortBezug("<original@example.com>", List.of("<root@example.com>"));

        private Email original() {
            Email original = createTestEmail(7L, "Angebot", "max@example.com");
            original.setMessageId("<original@example.com>");
            return original;
        }

        private MockMultipartFile dto(String recipients, String cc) {
            return new MockMultipartFile("dto", "", "application/json", ("{\"sender\":\"absender@example.com\","
                    + "\"recipients\":" + recipients + ",\"cc\":" + cc + ",\"subject\":\"AW: Angebot\",\"body\":\"<p>Danke</p>\"}")
                    .getBytes(StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("versendet und speichert CC, alle An-Empfänger und setzt In-Reply-To/References")
        void replyVersendetCcUndAntwortBezug() throws Exception {
            prepareDraftSend();
            Email original = original();
            given(emailRepository.findById(7L)).willReturn(Optional.of(original));
            given(emailThreadService.antwortBezugFuer(original)).willReturn(bezug);
            org.mockito.Mockito.doReturn("<antwort@example.com>").when(unifiedEmailController)
                    .sendeSmtpMail(any(), any(), any(), any(), any(), any(), any(), any());

            mockMvc.perform(multipart("/api/emails/7/reply")
                            .file(dto("[\"\\\"Max Mustermann\\\" <max@example.com>, erika@example.com\"]",
                                    "[\"architekt@example.org\", \" \"]")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cc").value("architekt@example.org"));

            verify(unifiedEmailController).sendeSmtpMail(any(),
                    org.mockito.ArgumentMatchers.eq("\"Max Mustermann\" <max@example.com>, erika@example.com"),
                    org.mockito.ArgumentMatchers.eq("architekt@example.org"),
                    org.mockito.ArgumentMatchers.eq("absender@example.com"),
                    org.mockito.ArgumentMatchers.eq("AW: Angebot"), any(), any(),
                    org.mockito.ArgumentMatchers.eq(bezug));
            org.mockito.ArgumentCaptor<Email> saved = org.mockito.ArgumentCaptor.forClass(Email.class);
            verify(emailRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
            Email antwort = saved.getAllValues().stream().filter(e -> e.getParentEmail() == original).findFirst().orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals("architekt@example.org", antwort.getCc());
            org.junit.jupiter.api.Assertions.assertEquals("<antwort@example.com>", antwort.getMessageId());
            verify(emailLieferantVerknuepfungService).verknuepfeAusEmpfaenger(antwort,
                    "\"Max Mustermann\" <max@example.com>, erika@example.com", "architekt@example.org");
        }

        @Test
        @DisplayName("ohne Empfänger geht die Antwort an Reply-To statt an den Absender")
        void replyOhneEmpfaengerNutztReplyTo() throws Exception {
            prepareDraftSend();
            Email original = original();
            original.setReplyToAddress("auftraege@example.com");
            given(emailRepository.findById(7L)).willReturn(Optional.of(original));
            org.mockito.Mockito.doReturn("<antwort@example.com>").when(unifiedEmailController)
                    .sendeSmtpMail(any(), any(), any(), any(), any(), any(), any(), any());

            mockMvc.perform(multipart("/api/emails/7/reply").file(dto("[]", "[]")))
                    .andExpect(status().isOk());

            verify(unifiedEmailController).sendeSmtpMail(any(),
                    org.mockito.ArgumentMatchers.eq("auftraege@example.com"), isNull(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Detailansicht liefert Reply-To und CC")
        void detailLiefertReplyToUndCc() throws Exception {
            Email original = original();
            original.setReplyToAddress("auftraege@example.com");
            original.setCc("erika@example.com");
            given(emailRepository.findById(7L)).willReturn(Optional.of(original));

            mockMvc.perform(get("/api/emails/7"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.replyToAddress").value("auftraege@example.com"))
                    .andExpect(jsonPath("$.cc").value("erika@example.com"))
                    .andExpect(jsonPath("$.threadRootId").value(7));
        }

        @Test
        @DisplayName("freier Versand speichert CC an der gesendeten Mail")
        void sendSpeichertCc() throws Exception {
            prepareDraftSend();
            org.mockito.Mockito.doReturn("<neu@example.com>").when(unifiedEmailController)
                    .sendeSmtpMail(any(), any(), any(), any(), any(), any(), any());

            mockMvc.perform(multipart("/api/emails/send").file(dto("[\"kunde@example.com\"]", "[\"erika@example.com\"]")))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Email> saved = org.mockito.ArgumentCaptor.forClass(Email.class);
            verify(emailRepository).saveAndFlush(saved.capture());
            org.junit.jupiter.api.Assertions.assertEquals("erika@example.com", saved.getValue().getCc());
        }
    }
}
