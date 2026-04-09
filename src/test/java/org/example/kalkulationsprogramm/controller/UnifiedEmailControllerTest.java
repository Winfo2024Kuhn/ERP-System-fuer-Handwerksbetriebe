package org.example.kalkulationsprogramm.controller;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailBlacklistEntry;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp;
import org.example.kalkulationsprogramm.dto.ContactDto;
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.AngebotRepository;
import org.example.kalkulationsprogramm.repository.EmailBlacklistRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.service.ContactService;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.EmailAutoAssignmentService;
import org.example.kalkulationsprogramm.service.EmailImportService;
import org.example.kalkulationsprogramm.service.InquiryDetectionService;
import org.example.kalkulationsprogramm.service.SpamBayesService;
import org.example.kalkulationsprogramm.service.SpamFilterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UnifiedEmailController.class)
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.test.context.TestPropertySource(properties = {
        "file.mail-attachment-dir=target/test-attachments"
})
class UnifiedEmailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private EmailRepository emailRepository;
    @MockBean private ProjektRepository projektRepository;
    @MockBean private AnfrageRepository anfrageRepository;
    @MockBean private AngebotRepository angebotRepository;
    @MockBean private LieferantenRepository lieferantenRepository;
    @MockBean private EmailAutoAssignmentService emailAutoAssignmentService;
    @MockBean private EmailImportService emailImportService;
    @MockBean private SpamFilterService spamFilterService;
    @MockBean private InquiryDetectionService inquiryDetectionService;
    @MockBean private EmailBlacklistRepository emailBlacklistRepository;
    @MockBean private ProjektDokumentRepository projektDokumentRepository;
    @MockBean private AnfrageDokumentRepository anfrageDokumentRepository;
    @MockBean private DateiSpeicherService dateiSpeicherService;
    @MockBean private ContactService contactService;
    @MockBean private SpamBayesService spamBayesService;

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
        @DisplayName("Absender sperren und alle E-Mails als Spam markieren")
        void blockSenderSuccess() throws Exception {
            Email email = createTestEmail(1L, "Test", "boese@example.com");
            given(emailRepository.findById(1L)).willReturn(Optional.of(email));
            given(emailBlacklistRepository.existsByEmailAddress("boese@example.com")).willReturn(false);
            given(emailRepository.findByFromAddressIgnoreCase("boese@example.com"))
                    .willReturn(List.of(email));

            mockMvc.perform(post("/api/emails/1/block-sender"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("blocked")));

            verify(emailBlacklistRepository).save(any(EmailBlacklistEntry.class));
            verify(emailRepository).saveAll(anyList());
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
}
