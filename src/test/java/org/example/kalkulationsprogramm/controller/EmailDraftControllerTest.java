package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.EmailDraft;
import org.example.kalkulationsprogramm.repository.EmailDraftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.example.kalkulationsprogramm.domain.EmailDraftAttachment;
import org.springframework.http.MediaType;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EmailDraftControllerTest {
    private final EmailDraftRepository repository = mock(EmailDraftRepository.class);
    private final org.example.kalkulationsprogramm.repository.EmailDraftAttachmentRepository attachments = mock(org.example.kalkulationsprogramm.repository.EmailDraftAttachmentRepository.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new EmailDraftController(new org.example.kalkulationsprogramm.service.EmailDraftService(repository, attachments))).build();
        when(repository.save(any())).thenAnswer(invocation -> {
            EmailDraft draft = invocation.getArgument(0);
            draft.setId(42L);
            return draft;
        });
    }

    @Test
    void savesAttachmentsTogetherWithDraft() throws Exception {
        mvc.perform(multipart("/api/emails/drafts")
                .file(new MockMultipartFile("dto", "", "application/json",
                        "{\"recipient\":\"test@example.com\",\"subject\":\"Plan\"}".getBytes()))
                .file(new MockMultipartFile("attachments", "plan.pdf", "application/pdf", "PDF-Inhalt".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments[0].filename").value("plan.pdf"))
                .andExpect(jsonPath("$.attachments[0].size").value(10));
    }

    @Test
    void opensAnExistingDraft() throws Exception {
        EmailDraft draft = new EmailDraft();
        draft.setId(42L);
        draft.setCc("test@example.com");
        when(repository.findById(42L)).thenReturn(Optional.of(draft));
        mvc.perform(get("/api/emails/drafts/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cc").value("test@example.com"));
    }
    @Test
    void downloadsExactlyTheStoredBytesAndScopesAttachmentToItsDraft() throws Exception {
        EmailDraft draft = draftWithAttachment();
        when(repository.findById(42L)).thenReturn(Optional.of(draft));
        when(attachments.findByIdAndDraftId(7L, 42L)).thenReturn(Optional.of(draft.getAttachments().getFirst()));
        mvc.perform(get("/api/emails/drafts/42/attachments/7"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[] {0, 1, 2, -1}))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get("/api/emails/drafts/42/attachments/8")).andExpect(status().isNotFound());
    }

    @Test
    void multipartRemovalReplacesAttachmentSetButLegacyJsonPreservesIt() throws Exception {
        EmailDraft draft = draftWithAttachment();
        when(repository.findById(42L)).thenReturn(Optional.of(draft));
        mvc.perform(put("/api/emails/drafts/42").contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"Geändert\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attachments.length()").value(1));
        mvc.perform(multipart("/api/emails/drafts/42")
                .file(new MockMultipartFile("dto", "", "application/json", "{}".getBytes()))
                .with(request -> { request.setMethod("PUT"); return request; }))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attachments.length()").value(0));
    }

    @Test
    void validatesAllFilesBeforeReplacingExistingContent() throws Exception {
        EmailDraft draft = draftWithAttachment();
        draft.setSubject("Vorher");
        when(repository.findById(42L)).thenReturn(Optional.of(draft));
        mvc.perform(multipart("/api/emails/drafts/42")
                .file(new MockMultipartFile("dto", "", "application/json", "{\"subject\":\"Nachher\"}".getBytes()))
                .file(new MockMultipartFile("attachments", "bad.exe", "application/octet-stream", new byte[] {1}))
                .with(request -> { request.setMethod("PUT"); return request; }))
                .andExpect(status().isBadRequest());
        assertThat(draft.getSubject()).isEqualTo("Vorher");
        assertThat(draft.getAttachments()).hasSize(1);
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"datei.exe", "DATEI.BAT", "script.js", "script.ps1", "bild.svg"})
    void blocksExecutableAttachments(String filename) throws Exception {
        mvc.perform(multipart("/api/emails/drafts")
                .file(new MockMultipartFile("dto", "", "application/json", "{}".getBytes()))
                .file(new MockMultipartFile("attachments", filename, "application/octet-stream", new byte[] {1})))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cleansPathsAndDangerousHtmlWithoutInterpretingSqlText() throws Exception {
        mvc.perform(multipart("/api/emails/drafts")
                .file(new MockMultipartFile("dto", "", "application/json",
                        "{\"subject\":\"'; DROP TABLE email_draft; --\",\"body\":\"<script>alert(1)</script><p>Hallo</p>\"}".getBytes()))
                .file(new MockMultipartFile("attachments", "../../etc/plan.pdf", "application/pdf", new byte[] {1})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments[0].filename").value("plan.pdf"))
                .andExpect(jsonPath("$.subject").value("'; DROP TABLE email_draft; --"))
                .andExpect(jsonPath("$.body").value("<p>Hallo</p>"));
        ArgumentCaptor<EmailDraft> saved = ArgumentCaptor.forClass(EmailDraft.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getAttachments().getFirst().getData()).containsExactly((byte) 1);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    void rejectsInvalidIds(long id) throws Exception {
        mvc.perform(get("/api/emails/drafts/" + id)).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/emails/drafts/" + id)).andExpect(status().isBadRequest());
    }

    @Test
    void absentDraftIsNeverRecreatedByLateUpdate() throws Exception {
        mvc.perform(put("/api/emails/drafts/" + Long.MAX_VALUE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsOversizedTextAndFiles() throws Exception {
        mvc.perform(post("/api/emails/drafts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"" + "x".repeat(10_001) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/emails/drafts")
                .file(new MockMultipartFile("dto", "", "application/json", "{}".getBytes()))
                .file(new MockMultipartFile("attachments", "plan.pdf", "application/pdf", new byte[14_007_000])))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void deletesDraftWithItsOwnedAttachments() throws Exception {
        EmailDraft draft = draftWithAttachment();
        when(repository.findById(42L)).thenReturn(Optional.of(draft));
        mvc.perform(delete("/api/emails/drafts/42")).andExpect(status().isOk());
        verify(repository).delete(draft);
    }

    @Test
    void rejectsRenamedProgramsAndActiveContentTypes() throws Exception {
        mvc.perform(multipart("/api/emails/drafts")
                .file(new MockMultipartFile("dto", "", "application/json", "{}".getBytes()))
                .file(new MockMultipartFile("attachments", "notiz.txt", "text/plain", "MZ-program".getBytes())))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/emails/drafts")
                .file(new MockMultipartFile("dto", "", "application/json", "{}".getBytes()))
                .file(new MockMultipartFile("attachments", "notiz.txt", "text/html", "<script>alert(1)</script>".getBytes())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsCadFilesWithGenericBrowserContentType() throws Exception {
        mvc.perform(multipart("/api/emails/drafts")
                .file(new MockMultipartFile("dto", "", "application/json", "{}".getBytes()))
                .file(new MockMultipartFile("attachments", "zeichnung.dwg", "application/octet-stream", "AC1032".getBytes())))
                .andExpect(status().isOk());
    }

    @Test
    void preservesLocalSignatureImagesAndMetadataWhileRemovingActiveContent() throws Exception {
        String body = "<div class='email-signature' data-signature-id='3'><img src='/api/email/signatures/3/images/8' onerror='alert(1)'><img src='/api/emails/12/attachments/4'></div>";
        mvc.perform(post("/api/emails/drafts").contentType(MediaType.APPLICATION_JSON)
                .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("body", body))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.containsString("src=\"/api/email/signatures/3/images/8\"")))
                .andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.containsString("src=\"/api/emails/12/attachments/4\"")))
                .andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.containsString("class=\"email-signature\"")))
                .andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.containsString("data-signature-id=\"3\"")))
                .andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("onerror"))));
    }

    private EmailDraft draftWithAttachment() {
        EmailDraft draft = new EmailDraft();
        draft.setId(42L);
        EmailDraftAttachment file = new EmailDraftAttachment();
        file.setId(7L);
        file.setDraft(draft);
        file.setFilename("plan.pdf");
        file.setContentType("application/pdf");
        file.setData(new byte[] {0, 1, 2, -1});
        file.setSize(4);
        draft.getAttachments().add(file);
        return draft;
    }

}
