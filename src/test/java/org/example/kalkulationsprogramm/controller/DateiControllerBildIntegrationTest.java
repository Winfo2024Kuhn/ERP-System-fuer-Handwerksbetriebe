package org.example.kalkulationsprogramm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.example.kalkulationsprogramm.mapper.ProduktkategorieMapper;
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService;
import org.example.kalkulationsprogramm.service.BildVorschauService;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.MobileObjectAccessService;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.ZugferdExtractorService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.FileSystemUtils;

/**
 * Integrationstest für die Bildauslieferung der Tagebücher: echter Controller,
 * echter {@link DateiSpeicherService} und echte Verkleinerung, die Bilder liegen
 * als Dateien auf der Platte – genau wie im Betrieb. Gemockt sind nur Repositories,
 * die beim reinen Ausliefern nicht gebraucht werden.
 *
 * <p>Bewusst ohne Spring-Kontext verdrahtet: {@link DateiSpeicherService} zieht per
 * Setter-Injection rund ein Dutzend weitere Repositories nach, die hier keine Rolle
 * spielen. Controller und Dienste sind trotzdem die echten Klassen.</p>
 *
 * <p>Fotos aus dem Bautagebuch liegen im Upload-Ordner ({@code /api/dokumente/…}),
 * Fotos aus dem Anfrage-Tagebuch im Bilder-Ordner ({@code /api/images/…}). Vorschau
 * und Anzeigegröße hängen für beide unter {@code /api/dokumente/<name>/…}.</p>
 */
class DateiControllerBildIntegrationTest {

    private static final String DREISSIG_TAGE_PRIVAT = "max-age=2592000, private";

    private static Path wurzel;
    private static Path uploadOrdner;
    private static Path bilderOrdner;
    private static Path anfrageOrdner;

    private static MockMvc mockMvc;
    private static DateiSpeicherService speicher;

    @BeforeAll
    static void legeDateienAn() throws IOException {
        wurzel = Files.createTempDirectory("tagebuch-bilder");
        uploadOrdner = Files.createDirectories(wurzel.resolve("uploads"));
        bilderOrdner = Files.createDirectories(wurzel.resolve("bilder"));
        anfrageOrdner = Files.createDirectories(wurzel.resolve("anfragen"));

        // Bautagebuch: Handyfoto quer, 4000 x 3000
        Files.write(uploadOrdner.resolve("projekt-notiz.jpg"), jpeg(4000, 3000, null));
        // Anfrage-Tagebuch: hochkant fotografiert, Pixel liegen quer, EXIF sagt "um 90° drehen"
        Files.write(bilderOrdner.resolve("anfrage-notiz.jpg"), jpeg(3200, 2400, 6));
        // Rechnung: wird unter demselben Namen neu erzeugt und darf nicht gecacht werden
        Files.write(uploadOrdner.resolve("rechnung.pdf"), "%PDF-1.4 Dummy".getBytes(StandardCharsets.UTF_8));

        SystemSettingsService einstellungen = mock(SystemSettingsService.class);
        when(einstellungen.getDateiOrdnerPfad()).thenReturn(wurzel.resolve("hicad").toString());
        speicher = new DateiSpeicherService(
                uploadOrdner.toString(), anfrageOrdner.toString(),
                bilderOrdner.toString(), bilderOrdner.toString(), "",
                mock(ProjektDokumentRepository.class), mock(ProjektRepository.class),
                mock(AnfrageDokumentRepository.class), mock(AnfrageRepository.class),
                mock(ProduktkategorieRepository.class), mock(KundeRepository.class),
                mock(ZugferdExtractorService.class), mock(ProduktkategorieMapper.class),
                einstellungen, mock(AusgangsGeschaeftsDokumentService.class));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DateiController(speicher, new BildVorschauService(), mock(MobileObjectAccessService.class)))
                .build();
    }

    @AfterAll
    static void raeumeAuf() throws IOException {
        FileSystemUtils.deleteRecursively(wurzel);
    }

    @Test
    void mobileDateiBleibtAuchBeiGleichenNamenImFreigegebenenSpeicher() throws Exception {
        Files.writeString(uploadOrdner.resolve("gleicher-name.jpg"), "Projektfoto");
        Files.writeString(anfrageOrdner.resolve("gleicher-name.jpg"), "Anfragefoto");
        Files.writeString(bilderOrdner.resolve("gleicher-name.jpg"), "Notizfoto");

        for (var eintrag : java.util.Map.of(
                MobileObjectAccessService.Speicher.PROJEKT, "Projektfoto",
                MobileObjectAccessService.Speicher.ANFRAGE, "Anfragefoto",
                MobileObjectAccessService.Speicher.BILD, "Notizfoto").entrySet()) {
            var resource = speicher.ladeMobileDateiAlsResource(new MobileObjectAccessService.FreigegebeneDatei(
                    "gleicher-name.jpg", eintrag.getKey()));
            assertThat(resource.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(eintrag.getValue());
        }
    }

    @Test
    void fehlendeMobileDateiFaelltNichtAufAnderenSpeicherOderSymlinkZurueck() throws Exception {
        Files.writeString(bilderOrdner.resolve("nur-im-bilderordner.jpg"), "Privater Inhalt");
        var falscherSpeicher = new MobileObjectAccessService.FreigegebeneDatei(
                "nur-im-bilderordner.jpg", MobileObjectAccessService.Speicher.PROJEKT);
        assertThatThrownBy(() -> speicher.ladeMobileDateiAlsResource(falscherSpeicher))
                .isInstanceOf(org.example.kalkulationsprogramm.exception.NotFoundException.class);

        Files.writeString(wurzel.resolve("private-akte.jpg"), "Private Akte");
        Files.createSymbolicLink(uploadOrdner.resolve("verknuepfung.jpg"), wurzel.resolve("private-akte.jpg"));
        var symlink = new MobileObjectAccessService.FreigegebeneDatei(
                "verknuepfung.jpg", MobileObjectAccessService.Speicher.PROJEKT);
        assertThatThrownBy(() -> speicher.ladeMobileDateiAlsResource(symlink))
                .isInstanceOf(org.example.kalkulationsprogramm.exception.NotFoundException.class);
    }

    @Test
    void projektNotizUploadUndLoeschenVerwendenDenKonfiguriertenSpeicher() throws Exception {
        byte[] bild = jpeg(10, 10, null);
        var upload = new org.springframework.mock.web.MockMultipartFile("datei", "../../foto.jpg", "image/jpeg", bild);
        String dateiname = speicher.speichereProjektNotizBild(upload);

        assertThat(dateiname).matches("[0-9a-f-]+\\.jpg");
        assertThat(Files.readAllBytes(uploadOrdner.resolve(dateiname))).isEqualTo(bild);
        assertThat(Files.exists(bilderOrdner.resolve(dateiname))).isFalse();
        speicher.loescheProjektNotizBild(dateiname);
        assertThat(Files.exists(uploadOrdner.resolve(dateiname))).isFalse();
    }

    @Test
    void projektNotizUploadLehntAktiveDateitypenAbUndBereinigtBildnamen() throws Exception {
        var html = new org.springframework.mock.web.MockMultipartFile("datei", "datei.html", "text/html", "<script></script>".getBytes());
        assertThatThrownBy(() -> speicher.speichereProjektNotizBild(html)).isInstanceOf(IllegalArgumentException.class);
        var leer = new org.springframework.mock.web.MockMultipartFile("datei", "foto.jpg", "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> speicher.speichereProjektNotizBild(leer)).isInstanceOf(IllegalArgumentException.class);
        var bild = new org.springframework.mock.web.MockMultipartFile("datei", "C:\\tmp\\foto.html", "image/jpeg", jpeg(10, 10, null));
        String dateiname = speicher.speichereProjektNotizBild(bild);
        assertThat(dateiname).matches("[0-9a-f-]+\\.jpg");
    }

    @Test
    @DisplayName("Bautagebuch: Anzeigegröße kommt verkleinert von der Platte und darf ins Handy")
    void bautagebuchLiefertAnzeigegroesse() throws Exception {
        byte[] antwort = mockMvc.perform(get("/api/dokumente/projekt-notiz.jpg/anzeige"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, DREISSIG_TAGE_PRIVAT))
                .andReturn().getResponse().getContentAsByteArray();

        BufferedImage bild = ImageIO.read(new ByteArrayInputStream(antwort));
        assertThat(bild.getWidth()).isEqualTo(1600);
        assertThat(bild.getHeight()).isEqualTo(1200);
        assertThat((long) antwort.length).isLessThan(Files.size(uploadOrdner.resolve("projekt-notiz.jpg")));
    }

    @Test
    @DisplayName("Anfrage-Tagebuch: Anzeigegröße aus dem Bilder-Ordner, hochkant gedreht wie im Original")
    void anfrageTagebuchLiefertGedrehteAnzeigegroesse() throws Exception {
        byte[] antwort = mockMvc.perform(get("/api/dokumente/anfrage-notiz.jpg/anzeige"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andReturn().getResponse().getContentAsByteArray();

        BufferedImage bild = ImageIO.read(new ByteArrayInputStream(antwort));
        assertThat(bild.getWidth()).isEqualTo(1200);
        assertThat(bild.getHeight()).isEqualTo(1600);
    }

    @Test
    @DisplayName("Anfrage-Tagebuch: Vorschaubild unter /api/dokumente, obwohl das Foto unter /api/images liegt")
    void anfrageTagebuchLiefertVorschau() throws Exception {
        byte[] antwort = mockMvc.perform(get("/api/dokumente/anfrage-notiz.jpg/thumbnail"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, DREISSIG_TAGE_PRIVAT))
                .andReturn().getResponse().getContentAsByteArray();

        BufferedImage bild = ImageIO.read(new ByteArrayInputStream(antwort));
        assertThat(Math.max(bild.getWidth(), bild.getHeight())).isEqualTo(BildVorschauService.THUMBNAIL_MAX_SIZE);
    }

    @Test
    @DisplayName("Originale beider Tagebücher dürfen im Gerät gecacht werden")
    void originaleWerdenMitCacheAusgeliefert() throws Exception {
        mockMvc.perform(get("/api/images/anfrage-notiz.jpg"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, DREISSIG_TAGE_PRIVAT));
        mockMvc.perform(get("/api/dokumente/projekt-notiz.jpg"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, DREISSIG_TAGE_PRIVAT));
    }

    @Test
    @DisplayName("PDFs bekommen keinen langen Cache")
    void pdfOhneCache() throws Exception {
        mockMvc.perform(get("/api/dokumente/rechnung.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.CACHE_CONTROL));
    }

    @Test
    @DisplayName("Gelöschtes Foto kommt nicht mehr aus dem Server-Cache")
    void geloeschtesFotoNichtMehrAusDemCache() throws Exception {
        Path foto = uploadOrdner.resolve("wird-geloescht.jpg");
        Files.write(foto, jpeg(2000, 1500, null));
        mockMvc.perform(get("/api/dokumente/wird-geloescht.jpg/anzeige")).andExpect(status().isOk());
        mockMvc.perform(get("/api/dokumente/wird-geloescht.jpg/thumbnail")).andExpect(status().isOk());

        Files.delete(foto);

        mockMvc.perform(get("/api/dokumente/wird-geloescht.jpg/anzeige")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/dokumente/wird-geloescht.jpg/thumbnail")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Fehlendes Bild meldet 404 statt Serverfehler")
    void fehlendesBildMeldet404() throws Exception {
        mockMvc.perform(get("/api/dokumente/gibt-es-nicht.jpg/anzeige"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Path-Traversal über die Anzeigegröße liefert keine Datei außerhalb der Speicher")
    void pathTraversalWirdAbgewiesen() throws Exception {
        Files.write(wurzel.resolve("geheim.jpg"), jpeg(400, 300, null));

        mockMvc.perform(get("/api/dokumente/..%2Fgeheim.jpg/anzeige"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/dokumente/{name}/anzeige", "../geheim.jpg"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Bei Überlast: 503 ohne Speichern, damit das Handy nicht auf dem Original sitzen bleibt")
    void ueberlastWirdNichtGespeichert() throws Exception {
        MockMvc ausgelastet = MockMvcBuilders
                .standaloneSetup(new DateiController(speicher, new BildVorschauService(0, 0), mock(MobileObjectAccessService.class)))
                .build();

        ausgelastet.perform(get("/api/dokumente/projekt-notiz.jpg/anzeige"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"));
        // Die Vorschau ist davon nicht betroffen
        ausgelastet.perform(get("/api/dokumente/projekt-notiz.jpg/thumbnail"))
                .andExpect(status().isOk());
    }

    /**
     * Graue Fläche als JPEG (keine Personendaten), optional mit EXIF-Orientierung.
     *
     * @param orientierung EXIF-Wert 1–8 oder {@code null} für "kein EXIF-Block"
     */
    private static byte[] jpeg(int breite, int hoehe, Integer orientierung) throws IOException {
        BufferedImage bild = new BufferedImage(breite, hoehe, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bild.createGraphics();
        g.setColor(Color.GRAY);
        g.fillRect(0, 0, breite, hoehe);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(bild, "jpg", out);
        byte[] daten = out.toByteArray();
        if (orientierung == null) {
            return daten;
        }

        // Minimaler APP1-Block (Little Endian) mit genau einem Eintrag: Orientation
        byte[] nutzdaten = {
                'E', 'x', 'i', 'f', 0, 0, 'I', 'I', 42, 0, 8, 0, 0, 0,
                1, 0, 0x12, 0x01, 3, 0, 1, 0, 0, 0, (byte) (int) orientierung, 0, 0, 0, 0, 0, 0, 0 };
        int laenge = nutzdaten.length + 2;
        ByteArrayOutputStream mitExif = new ByteArrayOutputStream();
        mitExif.write(daten, 0, 2);
        mitExif.write(new byte[] { (byte) 0xFF, (byte) 0xE1, (byte) (laenge >> 8), (byte) (laenge & 0xFF) });
        mitExif.write(nutzdaten);
        mitExif.write(daten, 2, daten.length - 2);
        return mitExif.toByteArray();
    }
}
