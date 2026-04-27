package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantDetailDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantEmailDto;
import org.example.kalkulationsprogramm.mapper.LieferantMapper;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantBildRepository;
import org.example.kalkulationsprogramm.service.LieferantArtikelpreisService;
import org.example.kalkulationsprogramm.service.LieferantDokumentService;
import org.example.kalkulationsprogramm.service.LieferantEmailResolver;
import org.example.kalkulationsprogramm.repository.LieferantNotizRepository;

import org.example.kalkulationsprogramm.service.LieferantenDetailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LieferantenController.class)
@AutoConfigureMockMvc(addFilters = false)
class LieferantenControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private LieferantenRepository lieferantenRepository;
  @MockBean
  private LieferantMapper lieferantMapper;
  @MockBean
  private LieferantEmailResolver lieferantEmailResolver;
  @MockBean
  private LieferantenDetailService lieferantenDetailService;
  @MockBean
  private org.example.kalkulationsprogramm.repository.EmailRepository emailRepository;
  @MockBean
  private org.example.kalkulationsprogramm.service.FrontendUserProfileService frontendUserProfileService;
  @MockBean
  private org.example.kalkulationsprogramm.service.EmailSignatureService emailSignatureService;
  @MockBean
  private LieferantDokumentService lieferantDokumentService;
  @MockBean
  private MitarbeiterRepository mitarbeiterRepository;
  @MockBean
  private LieferantArtikelpreisService lieferantArtikelpreisService;
  @MockBean
  private org.example.kalkulationsprogramm.service.EmailAttachmentProcessingService emailAttachmentProcessingService;
  @MockBean
  private org.example.kalkulationsprogramm.repository.LieferantDokumentRepository lieferantDokumentRepository;
  @MockBean
  private LieferantGeschaeftsdokumentRepository lieferantGeschaeftsdokumentRepository;
  @MockBean
  private org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService geminiDokumentAnalyseService;
  @MockBean
  private org.springframework.context.ApplicationEventPublisher applicationEventPublisher;
  @MockBean
  private LieferantNotizRepository lieferantNotizRepository;
  @MockBean
  private LieferantBildRepository lieferantBildRepository;
  @MockBean
  private org.example.kalkulationsprogramm.repository.KostenstelleRepository kostenstelleRepository;
  @MockBean
  private org.example.kalkulationsprogramm.service.LieferantStandardKostenstelleAutoAssigner standardKostenstelleAutoAssigner;

  @Test
  void returnsAllEmails() throws Exception {
    Lieferanten l1 = new Lieferanten();
    l1.setId(1L);
    l1.getKundenEmails().add("a@example.com");
    Lieferanten l2 = new Lieferanten();
    l2.setId(2L);
    l2.getKundenEmails().add("b@example.com");
    l2.getKundenEmails().add("c@example.com");
    when(lieferantenRepository.findAll()).thenReturn(List.of(l1, l2));

    mockMvc.perform(get("/api/lieferanten/emails"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").value("a@example.com"))
        .andExpect(jsonPath("$[1]").value("b@example.com"))
        .andExpect(jsonPath("$[2]").value("c@example.com"));
  }

  @Test
  void updatesLieferant() throws Exception {
    Lieferanten entity = new Lieferanten();
    entity.setId(5L);
    entity.setLieferantenname("Alt");
    when(lieferantenRepository.findById(5L)).thenReturn(Optional.of(entity));
    when(lieferantenRepository.findByLieferantennameIgnoreCase("Neu")).thenReturn(Optional.empty());
    LieferantDetailDto detail = new LieferantDetailDto();
    detail.setLieferantenname("Neu");
    when(lieferantenDetailService.loadDetails(5L)).thenReturn(detail);

    String payload = """
        {
          "lieferantenname": "Neu",
          "istAktiv": true,
          "kundenEmails": []
        }
        """;

    mockMvc.perform(put("/api/lieferanten/5")
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lieferantenname").value("Neu"));
  }
}
