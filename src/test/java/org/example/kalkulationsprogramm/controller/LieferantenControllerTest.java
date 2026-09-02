package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantRolle;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantDetailDto;
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
  @MockBean
  private org.example.kalkulationsprogramm.service.SystemSettingsService systemSettingsService;

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
}
