package org.example.kalkulationsprogramm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.service.EmailAttachmentProcessingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = LieferantWartungController.class)
@AutoConfigureMockMvc(addFilters = false)
class LieferantWartungControllerTest {

    private static final String PFAD = "/api/admin/lieferanten/{id}/reprocess-attachments";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LieferantenRepository lieferantenRepository;

    @MockBean
    private EmailRepository emailRepository;

    @MockBean
    private EmailAttachmentProcessingService emailAttachmentProcessingService;

    @Test
    @DisplayName("Setzt nur PDF-Anhänge zurück und verarbeitet die Mails neu")
    void verarbeitetPdfAnhaengeNeu() throws Exception {
        EmailAttachment pdf = anhang("Rechnung_4711.PDF");
        EmailAttachment xml = anhang("rechnung.xml");
        EmailAttachment ohneName = anhang(null);
        Email mail = mailMit(1L, pdf, xml, ohneName);
        when(lieferantenRepository.findById(5L)).thenReturn(Optional.of(new Lieferanten()));
        when(emailRepository.findByLieferantIdOrderBySentAtDesc(5L)).thenReturn(List.of(mail));
        when(emailAttachmentProcessingService.processLieferantAttachments(mail)).thenReturn(1);

        mockMvc.perform(post(PFAD, 5L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lieferantId").value(5))
                .andExpect(jsonPath("$.totalAttachments").value(1))
                .andExpect(jsonPath("$.processed").value(1));

        assertThat(pdf.getAiProcessed()).isFalse();
        assertThat(pdf.getAiProcessedAt()).isNull();
        assertThat(xml.getAiProcessed()).isTrue();
        verify(emailAttachmentProcessingService).processLieferantAttachments(mail);
    }

    @Test
    @DisplayName("Ohne PDF-Anhänge wird nichts an die KI geschickt")
    void ohnePdfKeineVerarbeitung() throws Exception {
        Email mail = mailMit(1L, anhang("lieferschein.xml"));
        when(lieferantenRepository.findById(5L)).thenReturn(Optional.of(new Lieferanten()));
        when(emailRepository.findByLieferantIdOrderBySentAtDesc(5L)).thenReturn(List.of(mail));

        mockMvc.perform(post(PFAD, 5L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAttachments").value(0))
                .andExpect(jsonPath("$.processed").value(0));

        verifyNoInteractions(emailAttachmentProcessingService);
    }

    @Test
    @DisplayName("Ein Fehler bei einer Mail bricht den Lauf nicht ab")
    void fehlerBeiEinerMailLaeuftWeiter() throws Exception {
        Email kaputt = mailMit(1L, anhang("a.pdf"));
        Email gut = mailMit(2L, anhang("b.pdf"));
        when(lieferantenRepository.findById(5L)).thenReturn(Optional.of(new Lieferanten()));
        when(emailRepository.findByLieferantIdOrderBySentAtDesc(5L)).thenReturn(List.of(kaputt, gut));
        when(emailAttachmentProcessingService.processLieferantAttachments(kaputt))
                .thenThrow(new IllegalStateException("KI nicht erreichbar"));
        when(emailAttachmentProcessingService.processLieferantAttachments(gut)).thenReturn(1);

        mockMvc.perform(post(PFAD, 5L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAttachments").value(2))
                .andExpect(jsonPath("$.processed").value(1));
    }

    @ParameterizedTest
    @ValueSource(longs = { 0L, -1L, Long.MAX_VALUE })
    @DisplayName("Unbekannter Lieferant gibt 404 und startet nichts")
    void unbekannterLieferant(long id) throws Exception {
        when(lieferantenRepository.findById(anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(post(PFAD, id)).andExpect(status().isNotFound());

        verifyNoInteractions(emailRepository, emailAttachmentProcessingService);
    }

    @Test
    @DisplayName("Keine Zahl als ID gibt 400")
    void ungueltigeId() throws Exception {
        mockMvc.perform(post(PFAD, "'; DROP TABLE email; --"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(lieferantenRepository, emailAttachmentProcessingService);
    }

    private static EmailAttachment anhang(String dateiname) {
        EmailAttachment a = new EmailAttachment();
        a.setOriginalFilename(dateiname);
        a.setAiProcessed(true);
        a.setAiProcessedAt(LocalDateTime.of(2026, 1, 1, 8, 0));
        return a;
    }

    private static Email mailMit(Long id, EmailAttachment... anhaenge) {
        Email mail = new Email();
        mail.setId(id);
        mail.setAttachments(new ArrayList<>(List.of(anhaenge)));
        return mail;
    }
}
