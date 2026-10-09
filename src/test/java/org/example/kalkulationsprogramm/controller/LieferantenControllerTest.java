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
import org.springframework.test.util.ReflectionTestUtils;
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

  @Autowired
  private LieferantenController controller;

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

    LieferantDokument dokument = dokumentVonLieferant(8L, 42L);
    dokument.setGespeicherterDateiname("../secret.txt");
    when(lieferantDokumentService.findById(8L)).thenReturn(dokument);

    mockMvc.perform(get("/api/lieferanten/42/dokumente/8/download"))
        .andExpect(status().isNotFound());
  }

  // ============== VORSCHAUBILDER ==============
  // Hinweis: Der Endpunkt loest den Bilder-Ordner relativ zum Arbeitsverzeichnis auf.
  // Die Tests legen ihre Dateien deshalb unter einer bewusst unrealistischen
  // Lieferanten-ID ab, damit sie auf keinen Fall im Ordner eines echten Lieferanten
  // landen, und raeumen alles wieder weg.

  private static final long TEST_LIEFERANT_ID = 999_000_042L;

  /** Loescht die im Test angelegte Bilddatei samt der leeren Ordner darueber. */
  private void raeumeTestBildOrdnerAuf(String dateiname) throws Exception {
    Path bilder = Path.of("uploads", "lieferanten", String.valueOf(TEST_LIEFERANT_ID), "bilder");
    if (dateiname != null) {
      Files.deleteIfExists(bilder.resolve(dateiname));
    }
    Files.deleteIfExists(bilder);
    Files.deleteIfExists(bilder.getParent());
  }

  /** Legt eine Bilddatei dort ab, wo der Vorschau-Endpunkt sie erwartet. */
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

    Path ziel = Path.of("uploads", "lieferanten", String.valueOf(lieferantId), "bilder", dateiname);
    Files.createDirectories(ziel.getParent());
    Files.write(ziel, daten);
    return daten;
  }

  /**
   * Meldet dem Repository ein Bild mit dem angegebenen gespeicherten Dateinamen.
   * Der Lookup greift fuer jeden angefragten Namen – so laesst sich auch ein
   * Datenbankwert testen, der sich nicht als Request-Pfad schreiben liesse.
   */
  private void registriereBild(String gespeicherterDateiname) {
    Lieferanten lieferant = new Lieferanten();
    lieferant.setId(TEST_LIEFERANT_ID);
    var bild = new org.example.kalkulationsprogramm.domain.LieferantBild();
    bild.setLieferant(lieferant);
    bild.setGespeicherterDateiname(gespeicherterDateiname);
    when(lieferantBildRepository.findByGespeicherterDateiname(any()))
        .thenReturn(Optional.of(bild));
  }

  @Test
  @DisplayName("Vorschau liefert ein verkleinertes JPEG statt des Originalfotos")
  void vorschauVerkleinertDasBild() throws Exception {
    String dateiname = "vorschau-test-" + java.util.UUID.randomUUID() + ".jpg";
    byte[] original = legeLieferantenBildAn(TEST_LIEFERANT_ID, dateiname, 2000, 1500);
    registriereBild(dateiname);

    try {
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
    } finally {
      raeumeTestBildOrdnerAuf(dateiname);
    }
  }

  @Test
  @DisplayName("Vorschau meldet 404, wenn der Dateiname unbekannt ist")
  void vorschauMeldet404BeiUnbekannterDatei() throws Exception {
    when(lieferantBildRepository.findByGespeicherterDateiname(any())).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/lieferanten/bilder/file/gibtesnicht.jpg/vorschau"))
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
   * Regression: Der gespeicherte Dateiname stammt zwar aus der Datenbank, wird beim
   * Upload aber nur von {@code StringUtils.cleanPath} bereinigt – und das behaelt
   * fuehrende {@code ../}-Elemente. Ein so benanntes Bild darf keine Datei ausserhalb
   * des Bilder-Ordners ausliefern.
   *
   * <p>Die Zieldatei liegt bewusst zwei Ebenen ueber dem Bilder-Ordner und existiert
   * wirklich: Ohne die Absicherung in {@code loeseBildPfadAuf} wuerde der Endpunkt sie
   * mit 200 samt Inhalt ausliefern. Der Request-Pfad selbst ist harmlos – geprueft wird
   * ausschliesslich der Wert aus der Datenbank.</p>
   */
  @Test
  @DisplayName("Vorschau liefert keine Datei ausserhalb des Bilder-Ordners aus")
  void vorschauBlocktTraversalImGespeichertenDateinamen() throws Exception {
    Path geheim = Path.of("uploads", "lieferanten", "geheim-testdatei.txt");
    Files.createDirectories(geheim.getParent());
    Files.writeString(geheim, "streng vertraulich");

    // Vom Bilder-Ordner (uploads/lieferanten/<id>/bilder) zwei Ebenen hoch
    String boesartig = "../../geheim-testdatei.txt";
    registriereBild(boesartig);

    try {
      byte[] antwort = mockMvc.perform(
          get("/api/lieferanten/bilder/file/harmlos.jpg/vorschau"))
          .andExpect(status().isNotFound())
          .andReturn().getResponse().getContentAsByteArray();

      org.assertj.core.api.Assertions.assertThat(new String(antwort))
          .doesNotContain("streng vertraulich");
    } finally {
      Files.deleteIfExists(geheim);
    }
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
