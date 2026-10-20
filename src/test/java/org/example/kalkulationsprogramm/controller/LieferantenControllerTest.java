package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantRolle;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantDetailDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantStatistikDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantEmailDto;
import org.example.kalkulationsprogramm.mapper.LieferantMapper;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantBildRepository;
import org.example.kalkulationsprogramm.service.BildVorschauService;
import org.example.kalkulationsprogramm.service.LieferantArtikelpreisService;
import org.example.kalkulationsprogramm.service.LieferantDateiablage;
import org.example.kalkulationsprogramm.testsupport.LieferantDateiablageImTestordner;
import org.example.kalkulationsprogramm.service.LieferantDokumentDateiService;
import org.example.kalkulationsprogramm.service.LieferantDokumentService;
import org.example.kalkulationsprogramm.service.LieferantEmailResolver;
import org.example.kalkulationsprogramm.repository.LieferantNotizRepository;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.service.LieferantenDetailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Import;
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
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LieferantenController.class)
// Echter BildVorschauService statt Mock: Die Vorschau-Tests unten pruefen die
// tatsaechlich erzeugten Bilddaten – mit einem Mock wuerden sie nichts messen.
@Import(BildVorschauService.class)
@AutoConfigureMockMvc(addFilters = false)
class LieferantenControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private org.example.kalkulationsprogramm.service.mail.SentMailArchiver sentMailArchiver;
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
  private org.example.kalkulationsprogramm.service.LieferantDokumentZugriffService lieferantDokumentZugriffService;
  @MockBean
  private org.example.kalkulationsprogramm.service.BelegZuordnungService belegZuordnungService;
  @MockBean
  private MitarbeiterRepository mitarbeiterRepository;
  @MockBean
  private LieferantArtikelpreisService lieferantArtikelpreisService;
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
  @MockBean
  private org.example.kalkulationsprogramm.service.SystemSettingsService systemSettingsService;
  @MockBean
  private org.example.kalkulationsprogramm.service.LieferantDokumentSucheService dokumentSucheService;
  @MockBean
  private LieferantDokumentDateiService dokumentDateiService;
  @MockBean
  private LieferantDateiablage dateiablage;

  /** Echte Dateisuche hinter dem Mock; die Ordner liegen unter {@code uploadDir} wie im Standard-Setup. */
  private void dateisucheUnter(Path uploadDir) {
    var echt = new LieferantDokumentDateiService(uploadDir.toString(), uploadDir.toString(),
        uploadDir.resolve("attachments").toString(), uploadDir.resolve("attachments").resolve("lieferanten").toString());
    when(dokumentDateiService.findeDatei(any())).thenAnswer(aufruf -> echt.findeDatei(aufruf.getArgument(0)));
  }

  /** Rechte-Logik ist in LieferantDokumentRechteSecurityTest abgedeckt; hier darf jeder alles sehen. */
  @org.junit.jupiter.api.BeforeEach
  void alleDokumenttypenSichtbar() {
    when(lieferantDokumentZugriffService.sichtbareTypen(any(), any()))
        .thenReturn(Optional.of(java.util.EnumSet.allOf(org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.class)));
  }

  @Test
  @DisplayName("Anhänge neu verarbeiten gibt es unter /api/lieferanten nicht mehr – der Pfad ist ohne Login offen")
  void reprocessAttachmentsLiegtNichtMehrImOffenenPfad() throws Exception {
    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .post("/api/lieferanten/5/reprocess-attachments"))
        .andExpect(status().is4xxClientError());

    verify(lieferantenRepository, org.mockito.Mockito.never()).findById(any());
  }

  @Test
  @DisplayName("Filter nach Rolle (Issue #266): gültige Rolle 200, unbekannte 400 ohne Datenbankabfrage")
  void sucheLieferantenNachRolle() throws Exception {
    Page<Lieferanten> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 12), 0);
    when(lieferantenRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

    mockMvc.perform(get("/api/lieferanten").param("rolle", "beschichtung_verzinken"))
        .andExpect(status().isOk());
    for (String falsch : List.of("GIBTSNICHT", "'; DROP TABLE lieferanten; --", "<script>alert(1)</script>")) {
      mockMvc.perform(get("/api/lieferanten").param("rolle", falsch))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("Diese Rolle gibt es nicht."));
    }
    org.mockito.Mockito.verify(lieferantenRepository, org.mockito.Mockito.times(1))
        .findAll(any(Specification.class), any(Pageable.class));
  }

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
  @DisplayName("Details mit nurStammdaten=true laden nur Stammdaten und Zaehler")
  void getByIdNurStammdaten() throws Exception {
    LieferantDetailDto detail = new LieferantDetailDto();
    detail.setDokumenteAnzahl(7L);
    when(lieferantenDetailService.loadStammdaten(5L)).thenReturn(detail);

    mockMvc.perform(get("/api/lieferanten/5").param("nurStammdaten", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dokumenteAnzahl").value(7));
  }

  @Test
  @DisplayName("Details mit nurStammdaten=true: unbekannte ID liefert 404")
  void getByIdNurStammdatenUnbekannt() throws Exception {
    when(lieferantenDetailService.loadStammdaten(99L)).thenReturn(null);

    mockMvc.perform(get("/api/lieferanten/99").param("nurStammdaten", "true"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Statistik: liefert Kennzahlen bzw. 404")
  void getStatistik() throws Exception {
    LieferantStatistikDto statistik = new LieferantStatistikDto();
    statistik.setArtikelAnzahl(42);
    when(lieferantenDetailService.loadStatistik(5L)).thenReturn(statistik);
    when(lieferantenDetailService.loadStatistik(99L)).thenReturn(null);

    mockMvc.perform(get("/api/lieferanten/5/statistik"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.artikelAnzahl").value(42));
    mockMvc.perform(get("/api/lieferanten/99/statistik"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("E-Mail-Verlauf: liefert Liste bzw. 404")
  void getEmailVerlauf() throws Exception {
    when(lieferantenRepository.existsById(5L)).thenReturn(true);
    when(lieferantenRepository.existsById(99L)).thenReturn(false);
    when(lieferantenDetailService.loadEmailVerlauf(5L)).thenReturn(List.of());

    mockMvc.perform(get("/api/lieferanten/5/email-verlauf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
    mockMvc.perform(get("/api/lieferanten/99/email-verlauf"))
        .andExpect(status().isNotFound());
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

  @Test
  @DisplayName("Anlegen: eine reine Vorwahl (vom Formular vorgeschlagen) wird nicht als Telefonnummer gespeichert")
  void createLieferantSpeichertKeineReineVorwahl() throws Exception {
    when(lieferantenRepository.findByLieferantennameIgnoreCase("Stahlbau Mustermann")).thenReturn(Optional.empty());
    when(lieferantenRepository.save(any(Lieferanten.class))).thenAnswer(inv -> inv.getArgument(0));

    String payload = """
        {
          "lieferantenname": "Stahlbau Mustermann",
          "plz": "97070",
          "ort": "Würzburg",
          "telefon": "0931 ",
          "mobiltelefon": " 0170 1234567 ",
          "kundenEmails": []
        }
        """;

    mockMvc.perform(post("/api/lieferanten")
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload))
        .andExpect(status().isCreated());

    ArgumentCaptor<Lieferanten> captor = ArgumentCaptor.forClass(Lieferanten.class);
    verify(lieferantenRepository).save(captor.capture());
    org.junit.jupiter.api.Assertions.assertNull(captor.getValue().getTelefon());
    org.junit.jupiter.api.Assertions.assertEquals("0170 1234567", captor.getValue().getMobiltelefon());
  }

  @Test
  @DisplayName("Ändern: eine gespeicherte reine Vorwahl wird beim nächsten Speichern geleert")
  void updateLieferantLeertReineVorwahl() throws Exception {
    Lieferanten entity = new Lieferanten();
    entity.setId(5L);
    entity.setLieferantenname("Stahlbau Mustermann");
    entity.setTelefon("0931");
    when(lieferantenRepository.findById(5L)).thenReturn(Optional.of(entity));
    when(lieferantenDetailService.loadDetails(5L)).thenReturn(new LieferantDetailDto());

    String payload = """
        {
          "lieferantenname": "Stahlbau Mustermann",
          "telefon": "0931 ",
          "mobiltelefon": "+49 ",
          "kundenEmails": []
        }
        """;

    mockMvc.perform(put("/api/lieferanten/5")
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload))
        .andExpect(status().isOk());

    org.junit.jupiter.api.Assertions.assertNull(entity.getTelefon());
    org.junit.jupiter.api.Assertions.assertNull(entity.getMobiltelefon());
  }

  @Test
  @DisplayName("Update speichert die uebergebenen Rollen am Lieferanten")
  void updatesLieferantSpeichertRollen() throws Exception {
    Lieferanten entity = new Lieferanten();
    entity.setId(5L);
    entity.setLieferantenname("Stahlbau Mustermann");
    when(lieferantenRepository.findById(5L)).thenReturn(Optional.of(entity));
    when(lieferantenDetailService.loadDetails(5L)).thenReturn(new LieferantDetailDto());

    String payload = """
        {
          "lieferantenname": "Stahlbau Mustermann",
          "istAktiv": true,
          "kundenEmails": [],
          "rollen": ["STAHLHANDEL", "EDELSTAHL"]
        }
        """;

    mockMvc.perform(put("/api/lieferanten/5")
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload))
        .andExpect(status().isOk());

    ArgumentCaptor<Lieferanten> captor = ArgumentCaptor.forClass(Lieferanten.class);
    org.mockito.Mockito.verify(lieferantenRepository).save(captor.capture());
    org.junit.jupiter.api.Assertions.assertEquals(
        java.util.Set.of(org.example.kalkulationsprogramm.domain.LieferantRolle.STAHLHANDEL,
            org.example.kalkulationsprogramm.domain.LieferantRolle.EDELSTAHL),
        captor.getValue().getRollen());
  }

  @Test
  @DisplayName("Update ohne rollen-Feld leert die Rollen-Zuordnung statt sie unveraendert zu lassen")
  void updatesLieferantOhneRollenLeertZuordnung() throws Exception {
    Lieferanten entity = new Lieferanten();
    entity.setId(6L);
    entity.setLieferantenname("Alt");
    entity.setRollen(new java.util.HashSet<>(java.util.Set.of(org.example.kalkulationsprogramm.domain.LieferantRolle.IT)));
    when(lieferantenRepository.findById(6L)).thenReturn(Optional.of(entity));
    when(lieferantenDetailService.loadDetails(6L)).thenReturn(new LieferantDetailDto());

    String payload = """
        {
          "lieferantenname": "Alt",
          "istAktiv": true,
          "kundenEmails": []
        }
        """;

    mockMvc.perform(put("/api/lieferanten/6")
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload))
        .andExpect(status().isOk());

    ArgumentCaptor<Lieferanten> captor = ArgumentCaptor.forClass(Lieferanten.class);
    org.mockito.Mockito.verify(lieferantenRepository).save(captor.capture());
    org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getRollen().isEmpty());
  }

  /**
   * Baut ein Dokument samt zugehoerigem Lieferanten. Die Spalte lieferant_id ist
   * NOT NULL, und der Download-Endpunkt prueft, dass Dokument und angefragter
   * Lieferant zusammengehoeren — ein Dokument ohne Lieferanten gibt es nicht.
   */
  private LieferantDokument dokumentVonLieferant(Long dokumentId, Long lieferantId) {
    Lieferanten lieferant = new Lieferanten();
    lieferant.setId(lieferantId);
    LieferantDokument dokument = new LieferantDokument();
    dokument.setId(dokumentId);
    dokument.setLieferant(lieferant);
    dokument.setTyp(org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.RECHNUNG);
    return dokument;
  }

  /** Ein Dokument eines anderen Lieferanten darf ueber fremde IDs nicht abrufbar sein. */
  @Test
  void dokumentEinesAnderenLieferantenWirdNichtAusgeliefert() throws Exception {
    when(lieferantDokumentService.findById(9L)).thenReturn(dokumentVonLieferant(9L, 43L));

    mockMvc.perform(get("/api/lieferanten/42/dokumente/9/download"))
        .andExpect(status().isNotFound());
  }

  /**
   * Happy-Path fuer den Mobile-Beleg-Bugfix: ein zu LieferantDokument promoteter
   * Mobile-Beleg traegt gespeicherterDateiname="belege/<file>" und liegt physisch
   * unter <uploadDir>/belege/. Die Dateisuche (LieferantDokumentDateiService)
   * muss diese Datei finden — vorher 404 (Bug), jetzt 200.
   */
  @Test
  void mobileBelegWirdAusgeliefert(@TempDir Path workDir) throws Exception {
    Path uploadDir = workDir.resolve("uploads");
    Path belegeDir = uploadDir.resolve("belege");
    Files.createDirectories(belegeDir);
    Files.write(belegeDir.resolve("scan.pdf"), "%PDF-1.4 stub".getBytes());

    dateisucheUnter(uploadDir);

    LieferantDokument dokument = dokumentVonLieferant(7L, 42L);
    dokument.setOriginalDateiname("scan.pdf");
    dokument.setGespeicherterDateiname("belege/scan.pdf");
    when(lieferantDokumentService.findById(7L)).thenReturn(dokument);

    mockMvc.perform(get("/api/lieferanten/42/dokumente/7/download"))
        .andExpect(status().isOk());
  }

  /**
   * Defense-in-Depth: ein boesartig gesetzter gespeicherterDateiname mit
   * ../-Traversal darf die Datei NICHT ausserhalb von uploadDir ausliefern.
   * Ohne Containment-Pruefung wuerde "<uploadDir>/../secret.txt" gefunden → LFI.
   * Die Dateisuche verwirft jeden Kandidaten ausserhalb seines Suchordners.
   */
  @Test
  void pathTraversalWirdBlockiert(@TempDir Path workDir) throws Exception {
    Path uploadDir = workDir.resolve("uploads");
    Files.createDirectories(uploadDir);
    Path secret = workDir.resolve("secret.txt");
    Files.writeString(secret, "geheim");

    dateisucheUnter(uploadDir);

    LieferantDokument dokument = dokumentVonLieferant(8L, 42L);
    dokument.setGespeicherterDateiname("../secret.txt");
    when(lieferantDokumentService.findById(8L)).thenReturn(dokument);

    mockMvc.perform(get("/api/lieferanten/42/dokumente/8/download"))
        .andExpect(status().isNotFound());
  }

  // ============== DATEIABLAGE (Dokumente und Bilder) ==============
  // Echte Ablage hinter dem Mock, aber in einem Temp-Ordner statt im Arbeitsverzeichnis.
  // Der Ordner heisst bewusst nicht "uploads": Die Endpunkte muessen dem konfigurierten
  // upload.path folgen.

  private static final long TEST_LIEFERANT_ID = 42L;

  @TempDir
  Path ablageOrdner;

  @org.junit.jupiter.api.BeforeEach
  void ablageImTempOrdner() throws Exception {
    LieferantDateiablageImTestordner.leiteUm(dateiablage, ablageOrdner);
  }

  private Path lieferantOrdner(long lieferantId) {
    return ablageOrdner.resolve("lieferanten").resolve(String.valueOf(lieferantId));
  }

  private Path bilderOrdner(long lieferantId) {
    return lieferantOrdner(lieferantId).resolve("bilder");
  }

  private static Lieferanten lieferant(long id) {
    Lieferanten lieferant = new Lieferanten();
    lieferant.setId(id);
    lieferant.setLieferantenname("Musterlieferant GmbH");
    return lieferant;
  }

  /** Legt eine Bilddatei dort ab, wo die Bild-Endpunkte sie erwarten. */
  private byte[] legeLieferantenBildAn(long lieferantId, String dateiname, int breite, int hoehe)
      throws Exception {
    java.awt.image.BufferedImage bild =
        new java.awt.image.BufferedImage(breite, hoehe, java.awt.image.BufferedImage.TYPE_INT_RGB);
    java.awt.Graphics2D g = bild.createGraphics();
    g.setColor(java.awt.Color.GRAY);
    g.fillRect(0, 0, breite, hoehe);
    g.dispose();
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(bild, "jpg", out);
    byte[] daten = out.toByteArray();

    Path ziel = bilderOrdner(lieferantId).resolve(dateiname);
    Files.createDirectories(ziel.getParent());
    Files.write(ziel, daten);
    return daten;
  }

  /**
   * Meldet dem Repository ein Bild mit dem angegebenen gespeicherten Dateinamen.
   * Der Lookup greift fuer jeden angefragten Namen – so laesst sich auch ein
   * Datenbankwert testen, der sich nicht als Request-Pfad schreiben liesse.
   */
  private org.example.kalkulationsprogramm.domain.LieferantBild registriereBild(String gespeicherterDateiname) {
    var bild = new org.example.kalkulationsprogramm.domain.LieferantBild();
    bild.setId(5L);
    bild.setLieferant(lieferant(TEST_LIEFERANT_ID));
    bild.setGespeicherterDateiname(gespeicherterDateiname);
    when(lieferantBildRepository.findByGespeicherterDateiname(any()))
        .thenReturn(Optional.of(bild));
    return bild;
  }

  @Test
  @DisplayName("Vorschau liefert ein verkleinertes JPEG statt des Originalfotos")
  void vorschauVerkleinertDasBild() throws Exception {
    String dateiname = "vorschau-test-" + java.util.UUID.randomUUID() + ".jpg";
    byte[] original = legeLieferantenBildAn(TEST_LIEFERANT_ID, dateiname, 2000, 1500);
    registriereBild(dateiname);

    byte[] vorschau = mockMvc.perform(get("/api/lieferanten/bilder/file/" + dateiname + "/vorschau"))
        .andExpect(status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
            .content().contentType(MediaType.IMAGE_JPEG))
        // "private": Reklamationsfotos sind personenbezogen, kein geteilter Proxy-Cache
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
            .header().string(org.springframework.http.HttpHeaders.CACHE_CONTROL,
                "max-age=86400, private"))
        .andReturn().getResponse().getContentAsByteArray();

    java.awt.image.BufferedImage verkleinert =
        javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(vorschau));
    org.assertj.core.api.Assertions.assertThat(verkleinert.getWidth()).isEqualTo(300);
    org.assertj.core.api.Assertions.assertThat(verkleinert.getHeight()).isEqualTo(225);
    org.assertj.core.api.Assertions.assertThat(vorschau.length).isLessThan(original.length);
  }

  @Test
  @DisplayName("Vorschau meldet 404, wenn der Dateiname unbekannt ist")
  void vorschauMeldet404BeiUnbekannterDatei() throws Exception {
    when(lieferantBildRepository.findByGespeicherterDateiname(any())).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/lieferanten/bilder/file/gibtesnicht.jpg/vorschau"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Vorschau meldet 404, wenn das Bild bekannt, die Datei aber weg ist")
  void vorschauMeldet404BeiFehlenderDatei() throws Exception {
    registriereBild("geloescht.jpg");

    mockMvc.perform(get("/api/lieferanten/bilder/file/geloescht.jpg/vorschau"))
        .andExpect(status().isNotFound());
  }

  /**
   * Der Dateiname wird ausschliesslich ueber die Datenbank aufgeloest. Ein
   * Traversal-Versuch findet dort keinen Treffer und kann damit keine Datei
   * ausserhalb des Bilder-Ordners ausliefern.
   */
  @Test
  @DisplayName("Vorschau blockiert Path-Traversal im Dateinamen")
  void vorschauBlocktPathTraversal() throws Exception {
    when(lieferantBildRepository.findByGespeicherterDateiname(any())).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/lieferanten/bilder/file/..%2F..%2Fapplication.properties/vorschau"))
        .andExpect(status().isNotFound());
  }

  /**
   * Regression: Der gespeicherte Dateiname stammt zwar aus der Datenbank, wurde beim
   * Upload frueher aber nur von {@code StringUtils.cleanPath} bereinigt – und das behaelt
   * fuehrende {@code ../}-Elemente. Ein so benanntes Bild darf keine Datei ausserhalb
   * des Bilder-Ordners ausliefern.
   *
   * <p>Die Zieldatei liegt bewusst zwei Ebenen ueber dem Bilder-Ordner und existiert
   * wirklich: Ohne die Absicherung in {@code LieferantDateiablage.findeBild} wuerde der
   * Endpunkt sie mit 200 samt Inhalt ausliefern. Der Request-Pfad selbst ist harmlos –
   * geprueft wird ausschliesslich der Wert aus der Datenbank.</p>
   */
  @Test
  @DisplayName("Vorschau und Original liefern keine Datei ausserhalb des Bilder-Ordners aus")
  void bildBlocktTraversalImGespeichertenDateinamen() throws Exception {
    Path geheim = ablageOrdner.resolve("lieferanten").resolve("geheim-testdatei.txt");
    Files.createDirectories(geheim.getParent());
    Files.writeString(geheim, "streng vertraulich");

    // Vom Bilder-Ordner (lieferanten/<id>/bilder) zwei Ebenen hoch
    registriereBild("../../geheim-testdatei.txt");

    for (String url : List.of("/api/lieferanten/bilder/file/harmlos.jpg/vorschau",
        "/api/lieferanten/bilder/file/harmlos.jpg")) {
      byte[] antwort = mockMvc.perform(get(url))
          .andExpect(status().isNotFound())
          .andReturn().getResponse().getContentAsByteArray();

      org.assertj.core.api.Assertions.assertThat(new String(antwort))
          .doesNotContain("streng vertraulich");
    }
  }

  @Test
  @DisplayName("Original-Bild wird aus dem konfigurierten Ordner ausgeliefert")
  void bildWirdAusgeliefert() throws Exception {
    byte[] original = legeLieferantenBildAn(TEST_LIEFERANT_ID, "kratzer.jpg", 40, 30);
    registriereBild("kratzer.jpg");

    mockMvc.perform(get("/api/lieferanten/bilder/file/kratzer.jpg"))
        .andExpect(status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
            .content().contentType(MediaType.IMAGE_JPEG))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
            .content().bytes(original));
  }

  @Test
  @DisplayName("Bild hochladen: Datei landet im Bilder-Ordner unter upload.path, Name bereinigt")
  void bildHochladen() throws Exception {
    var mitarbeiter = new org.example.kalkulationsprogramm.domain.Mitarbeiter();
    mitarbeiter.setVorname("Max");
    mitarbeiter.setNachname("Mustermann");
    when(lieferantenRepository.findById(TEST_LIEFERANT_ID)).thenReturn(Optional.of(lieferant(TEST_LIEFERANT_ID)));
    when(mitarbeiterRepository.findByLoginTokenAndAktivTrue("tok")).thenReturn(Optional.of(mitarbeiter));
    when(lieferantBildRepository.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
    var datei = new MockMultipartFile("datei", "../../Kratzer.jpg", "image/jpeg", new byte[] { 1, 2, 3 });

    mockMvc.perform(multipart("/api/lieferanten/" + TEST_LIEFERANT_ID + "/bilder").file(datei).param("token", "tok"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.originalDateiname").value("Kratzer.jpg"))
        .andExpect(jsonPath("$.mitarbeiterNachname").value("Mustermann"));

    var gespeichert = ArgumentCaptor.forClass(org.example.kalkulationsprogramm.domain.LieferantBild.class);
    verify(lieferantBildRepository).save(gespeichert.capture());
    org.assertj.core.api.Assertions.assertThat(
        bilderOrdner(TEST_LIEFERANT_ID).resolve(gespeichert.getValue().getGespeicherterDateiname()))
        .hasBinaryContent(new byte[] { 1, 2, 3 });
  }

  @Test
  @DisplayName("Bild hochladen ohne gueltigen Token: 401, nichts gespeichert")
  void bildHochladenOhneToken() throws Exception {
    when(lieferantenRepository.findById(TEST_LIEFERANT_ID)).thenReturn(Optional.of(lieferant(TEST_LIEFERANT_ID)));
    var datei = new MockMultipartFile("datei", "Kratzer.jpg", "image/jpeg", new byte[] { 1 });

    mockMvc.perform(multipart("/api/lieferanten/" + TEST_LIEFERANT_ID + "/bilder").file(datei))
        .andExpect(status().isUnauthorized());

    org.assertj.core.api.Assertions.assertThat(bilderOrdner(TEST_LIEFERANT_ID)).doesNotExist();
  }

  @Test
  @DisplayName("Bild loeschen entfernt Datei und Datensatz")
  void bildLoeschen() throws Exception {
    legeLieferantenBildAn(TEST_LIEFERANT_ID, "kratzer.jpg", 10, 10);
    var bild = registriereBild("kratzer.jpg");
    when(lieferantenRepository.existsById(TEST_LIEFERANT_ID)).thenReturn(true);
    when(lieferantBildRepository.findById(5L)).thenReturn(Optional.of(bild));

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .delete("/api/lieferanten/" + TEST_LIEFERANT_ID + "/bilder/5"))
        .andExpect(status().isNoContent());

    org.assertj.core.api.Assertions.assertThat(bilderOrdner(TEST_LIEFERANT_ID).resolve("kratzer.jpg")).doesNotExist();
    verify(lieferantBildRepository).delete(bild);
  }

  @Test
  @DisplayName("Bild loeschen mit Path-Traversal im gespeicherten Namen laesst fremde Dateien stehen")
  void bildLoeschenMitTraversal() throws Exception {
    Path daneben = lieferantOrdner(TEST_LIEFERANT_ID).resolve("Rechnung_Muster.pdf");
    Files.createDirectories(daneben.getParent());
    Files.writeString(daneben, "%PDF-1.4");
    var bild = registriereBild("../Rechnung_Muster.pdf");
    when(lieferantenRepository.existsById(TEST_LIEFERANT_ID)).thenReturn(true);
    when(lieferantBildRepository.findById(5L)).thenReturn(Optional.of(bild));

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .delete("/api/lieferanten/" + TEST_LIEFERANT_ID + "/bilder/5"))
        .andExpect(status().isNoContent());

    org.assertj.core.api.Assertions.assertThat(daneben).exists();
  }

  @Test
  @DisplayName("Bild eines anderen Lieferanten loeschen: 404, Datei bleibt")
  void bildLoeschenFremderLieferant() throws Exception {
    legeLieferantenBildAn(TEST_LIEFERANT_ID, "kratzer.jpg", 10, 10);
    var bild = registriereBild("kratzer.jpg");
    when(lieferantenRepository.existsById(43L)).thenReturn(true);
    when(lieferantBildRepository.findById(5L)).thenReturn(Optional.of(bild));

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .delete("/api/lieferanten/43/bilder/5"))
        .andExpect(status().isNotFound());

    org.assertj.core.api.Assertions.assertThat(bilderOrdner(TEST_LIEFERANT_ID).resolve("kratzer.jpg")).exists();
    verify(lieferantBildRepository, org.mockito.Mockito.never()).delete(any());
  }

  @Test
  @DisplayName("Dokument-Import: Datei landet unter <upload.path>/lieferanten/<id>/, Name bereinigt")
  void dokumentImportSpeichertImKonfiguriertenOrdner() throws Exception {
    when(lieferantenRepository.findById(TEST_LIEFERANT_ID)).thenReturn(Optional.of(lieferant(TEST_LIEFERANT_ID)));
    when(lieferantDokumentRepository.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
    when(lieferantGeschaeftsdokumentRepository.saveAndFlush(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
    var datei = new MockMultipartFile("datei", "../../etc/Rechnung_Muster.pdf", "application/pdf",
        "%PDF-1.4 Muster".getBytes());
    var metadata = new MockMultipartFile("metadata", "", MediaType.APPLICATION_JSON_VALUE,
        "{\"dokumentTyp\":\"RECHNUNG\"}".getBytes());

    mockMvc.perform(multipart("/api/lieferanten/" + TEST_LIEFERANT_ID + "/dokumente/import").file(datei).file(metadata))
        .andExpect(status().isOk());

    var gespeichert = ArgumentCaptor.forClass(LieferantDokument.class);
    verify(lieferantDokumentRepository, org.mockito.Mockito.atLeastOnce()).save(gespeichert.capture());
    LieferantDokument dokument = gespeichert.getValue();
    org.assertj.core.api.Assertions.assertThat(dokument.getOriginalDateiname()).isEqualTo("Rechnung_Muster.pdf");
    org.assertj.core.api.Assertions.assertThat(
        lieferantOrdner(TEST_LIEFERANT_ID).resolve(dokument.getGespeicherterDateiname()))
        .hasContent("%PDF-1.4 Muster");
  }

  @Test
  @DisplayName("Dokument-Import fuer unbekannten Lieferanten: 404, nichts gespeichert")
  void dokumentImportUnbekannterLieferant() throws Exception {
    when(lieferantenRepository.findById(any())).thenReturn(Optional.empty());
    var datei = new MockMultipartFile("datei", "Rechnung.pdf", "application/pdf", new byte[] { 1 });
    var metadata = new MockMultipartFile("metadata", "", MediaType.APPLICATION_JSON_VALUE, "{}".getBytes());

    mockMvc.perform(multipart("/api/lieferanten/0/dokumente/import").file(datei).file(metadata))
        .andExpect(status().isNotFound());

    verifyNoInteractions(dateiablage);
  }

  @org.junit.jupiter.api.Nested
  @DisplayName("Positionssuche in den Dokumenten eines Lieferanten")
  class Positionssuche {

    private org.springframework.security.authentication.UsernamePasswordAuthenticationToken angemeldet() {
      var principal = new org.example.kalkulationsprogramm.config.FrontendUserPrincipal(
          70L, "max.mustermann@example.com", "Max Mustermann", "", true, java.util.Set.of());
      return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
          principal, null, principal.getAuthorities());
    }

    @Test
    @DisplayName("Ohne Anmeldung (keine Sitzung, kein/ungültiger Token): 401")
    void ohneAnmeldung401() throws Exception {
      when(lieferantDokumentZugriffService.sichtbareTypen(any(), any())).thenReturn(Optional.empty());

      mockMvc.perform(get("/api/lieferanten/5/dokumente/positionssuche").param("q", "Flachstahl"))
          .andExpect(status().isUnauthorized());
      mockMvc.perform(get("/api/lieferanten/5/dokumente/positionssuche").param("q", "Flachstahl")
              .param("token", "ungueltig"))
          .andExpect(status().isUnauthorized());
      verifyNoInteractions(dokumentSucheService);
    }

    @Test
    @DisplayName("Angemeldete Sitzung ohne Token: Treffer mit Trefferzeile, alle Typen")
    void liefertTreffer() throws Exception {
      when(lieferantenRepository.existsById(5L)).thenReturn(true);
      when(dokumentSucheService.suchePositionen(eq("Flachstahl"), eq(5L), any(), any(), any())).thenReturn(java.util.Map.of(11L,
          new org.example.kalkulationsprogramm.dto.PositionsTrefferDto(11L,
              "Flachstahl 50x5 · S235JR · Charge 123456", 2)));

      mockMvc.perform(get("/api/lieferanten/5/dokumente/positionssuche").param("q", "Flachstahl")
              .principal(angemeldet()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].dokumentId").value(11))
          .andExpect(jsonPath("$[0].trefferText").value("Flachstahl 50x5 · S235JR · Charge 123456"))
          .andExpect(jsonPath("$[0].weitereTreffer").value(2));
    }

    @Test
    @DisplayName("Mit Token: Suche mit den Rechten des Mitarbeiters")
    void mitTokenRechteDesMitarbeiters() throws Exception {
      when(lieferantenRepository.existsById(5L)).thenReturn(true);
      var typen = java.util.EnumSet.of(org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.LIEFERSCHEIN);
      when(lieferantDokumentZugriffService.sichtbareTypen(eq("tok"), any())).thenReturn(Optional.of(typen));
      when(dokumentSucheService.suchePositionen("123456", 5L, typen, null, null)).thenReturn(java.util.Map.of());

      mockMvc.perform(get("/api/lieferanten/5/dokumente/positionssuche").param("q", "123456").param("token", "tok"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isEmpty());
      verify(dokumentSucheService).suchePositionen("123456", 5L, typen, null, null);
    }

    @Test
    @DisplayName("Unbekannter Lieferant (auch 0, negativ, Long.MAX_VALUE) liefert 404")
    void unbekannterLieferant() throws Exception {
      for (String id : List.of("0", "-1", String.valueOf(Long.MAX_VALUE))) {
        mockMvc.perform(get("/api/lieferanten/" + id + "/dokumente/positionssuche").param("q", "Flachstahl")
                .principal(angemeldet()))
            .andExpect(status().isNotFound());
      }
      verifyNoInteractions(dokumentSucheService);
    }

    @Test
    @DisplayName("SQL-Injection, XSS und Überlänge werden als normaler Suchtext durchgereicht")
    void boeseEingabenSindNurText() throws Exception {
      when(lieferantenRepository.existsById(5L)).thenReturn(true);
      when(dokumentSucheService.suchePositionen(org.mockito.ArgumentMatchers.anyString(), eq(5L),
          any(), any(), any())).thenReturn(java.util.Map.of());

      for (String q : List.of("'; DROP TABLE x; --", "<script>alert(1)</script>", "a".repeat(10_001))) {
        mockMvc.perform(get("/api/lieferanten/5/dokumente/positionssuche").param("q", q).principal(angemeldet()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
      }
    }

    @Test
    @DisplayName("Ohne Suchbegriff: 200 mit leerer Liste")
    void ohneSuchbegriff() throws Exception {
      when(lieferantenRepository.existsById(5L)).thenReturn(true);
      when(dokumentSucheService.suchePositionen(org.mockito.ArgumentMatchers.isNull(), eq(5L),
          any(), any(), any())).thenReturn(java.util.Map.of());

      mockMvc.perform(get("/api/lieferanten/5/dokumente/positionssuche").principal(angemeldet()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isEmpty());
    }
  }
}
