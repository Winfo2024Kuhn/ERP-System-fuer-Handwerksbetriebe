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

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.service.LieferantenDetailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
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

  @Autowired
  private LieferantenController controller;

  @Test
  @DisplayName("Freitext-Suche nach Telefonnummer liefert 200 und ruft Repository auf")
  void sucheLieferantenMitTelefonnummerLiefert200() throws Exception {
    Page<Lieferanten> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 12), 0);
    when(lieferantenRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

    mockMvc.perform(get("/api/lieferanten").param("q", "0931"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lieferanten").isArray())
        .andExpect(jsonPath("$.gesamt").value(0));
  }

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

  /**
   * Happy-Path fuer den Mobile-Beleg-Bugfix: ein zu LieferantDokument promoteter
   * Mobile-Beleg traegt gespeicherterDateiname="belege/<file>" und liegt physisch
   * unter <uploadDir>/belege/. Die neue Stufe-0-Aufloesung in resolveDokumentPath
   * muss diese Datei finden — vorher 404 (Bug), jetzt 200.
   */
  @Test
  void mobileBelegWirdAusgeliefert(@TempDir Path workDir) throws Exception {
    Path uploadDir = workDir.resolve("uploads");
    Path belegeDir = uploadDir.resolve("belege");
    Files.createDirectories(belegeDir);
    Files.write(belegeDir.resolve("scan.pdf"), "%PDF-1.4 stub".getBytes());

    ReflectionTestUtils.setField(controller, "uploadDir", uploadDir.toString());

    LieferantDokument dokument = new LieferantDokument();
    dokument.setId(7L);
    dokument.setOriginalDateiname("scan.pdf");
    dokument.setGespeicherterDateiname("belege/scan.pdf");
    when(lieferantDokumentService.findById(7L)).thenReturn(dokument);

    mockMvc.perform(get("/api/lieferanten/42/dokumente/7/download"))
        .andExpect(status().isOk());
  }

  /**
   * Defense-in-Depth: ein boesartig gesetzter gespeicherterDateiname mit
   * ../-Traversal darf die Datei NICHT ausserhalb von uploadDir ausliefern.
   * Ohne die startsWith(uploadBase)-Pruefung in Stufe 0 wuerde Files.exists()
   * fuer "<uploadDir>/../secret.txt" Treffer melden → LFI. Mit Containment-
   * Check muss Stufe 0 die Datei verwerfen, und die uebrigen Stufen finden
   * den relativen Pfad im cwd nicht.
   */
  @Test
  void pathTraversalWirdBlockiert(@TempDir Path workDir) throws Exception {
    Path uploadDir = workDir.resolve("uploads");
    Files.createDirectories(uploadDir);
    Path secret = workDir.resolve("secret.txt");
    Files.writeString(secret, "geheim");

    ReflectionTestUtils.setField(controller, "uploadDir", uploadDir.toString());

    LieferantDokument dokument = new LieferantDokument();
    dokument.setId(8L);
    dokument.setGespeicherterDateiname("../secret.txt");
    when(lieferantDokumentService.findById(8L)).thenReturn(dokument);

    mockMvc.perform(get("/api/lieferanten/42/dokumente/8/download"))
        .andExpect(status().isNotFound());
  }
}
