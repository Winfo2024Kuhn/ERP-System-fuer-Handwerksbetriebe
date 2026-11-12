package org.example.kalkulationsprogramm.service.firmendokumente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.example.kalkulationsprogramm.service.BildVorschauService;
import org.example.kalkulationsprogramm.service.vorschau.PdfErsteSeiteAlsBild;
import org.example.kalkulationsprogramm.service.vorschau.UmrechnungAusgelastetException;
import org.example.kalkulationsprogramm.service.vorschau.Umrechnungsplaetze;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

/**
 * Vorschaubilder der Firmendokumente mit echter Verkleinerung (Bild) und echtem PDF-Rendering; Rechte und Pfad kommen
 * aus {@link FirmendokumentService#datei} (hier gemockt, dessen Rechteprüfung steht in {@code FirmendokumentServiceTest}).
 */
class FirmendokumentVorschaubildServiceTest {

    private static final String KEIN = "Für diese Datei gibt es kein Vorschaubild.";
    private static final long GROSSE_GRENZE = 100L * 1024 * 1024;

    @TempDir Path tmp;

    private final Authentication max = mock(Authentication.class);
    private FirmendokumentService dokumente;
    private BildVorschauService bilder;
    private PdfErsteSeiteAlsBild pdfs;
    private FirmendokumentVorschaubildCache cache;
    private FirmendokumentVorschaubildService service;

    @BeforeEach
    void setUp() {
        dokumente = mock(FirmendokumentService.class);
        bilder = spy(new BildVorschauService());
        pdfs = spy(new PdfErsteSeiteAlsBild());
        cache = new FirmendokumentVorschaubildCache();
        service = new FirmendokumentVorschaubildService(dokumente, cache, bilder, pdfs, new Umrechnungsplaetze(2, 1_000),
                GROSSE_GRENZE);
    }

    @Test
    @DisplayName("Großes Foto → JPEG mit längster Kante 512, Seitenverhältnis bleibt")
    void grossesFoto() throws IOException {
        datei(7L, bildDatei("foto.bin", 2000, 1000, "jpg"), FirmendokumentDateiart.BILD);

        BufferedImage bild = lese(service.vorschaubild(7L, null, max).jpeg());

        assertThat(bild.getWidth()).isEqualTo(FirmendokumentVorschaubildService.MAX_KANTE);
        assertThat(bild.getHeight()).isEqualTo(256);
    }

    @Test
    @DisplayName("Hochkant-PNG wird ebenfalls verkleinert; kleines Bild wird nicht vergrößert")
    void kleinesBildBleibtKlein() throws IOException {
        datei(8L, bildDatei("hoch.bin", 600, 1200, "png"), FirmendokumentDateiart.BILD);
        datei(9L, bildDatei("klein.bin", 100, 50, "png"), FirmendokumentDateiart.BILD);

        BufferedImage hoch = lese(service.vorschaubild(8L, null, max).jpeg());
        BufferedImage klein = lese(service.vorschaubild(9L, null, max).jpeg());

        assertThat(hoch.getHeight()).isEqualTo(512);
        assertThat(hoch.getWidth()).isEqualTo(256);
        assertThat(klein.getWidth()).isEqualTo(100);
        assertThat(klein.getHeight()).isEqualTo(50);
    }

    @Test
    @DisplayName("PDF → erste Seite als JPEG, längste Kante 512")
    void pdf() throws IOException {
        Path pdf = tmp.resolve("pruefbericht.bin");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            doc.addPage(new PDPage(PDRectangle.A4));
            doc.save(pdf.toFile());
        }
        datei(10L, pdf, FirmendokumentDateiart.PDF);

        BufferedImage bild = lese(service.vorschaubild(10L, null, max).jpeg());

        assertThat(bild.getHeight()).isEqualTo(512);
        assertThat(bild.getWidth()).isLessThan(512);
        verifyNoInteractions(bilder);
    }

    @Test
    @DisplayName("Word-Datei (SONSTIGE) → 404 mit Meldung, die Datei wird gar nicht gelesen")
    void sonstige() throws IOException {
        Path word = Files.write(tmp.resolve("police.bin"), new byte[] { 'P', 'K', 3, 4 });
        datei(11L, word, FirmendokumentDateiart.SONSTIGE);

        assertKeinVorschaubild(11L);
        verifyNoInteractions(bilder, pdfs);
    }

    @Test
    @DisplayName("Kaputtes Bild, unlesbares Bildformat und kaputtes PDF → 404 mit Meldung, kein Serverfehler")
    void kaputteDateien() throws IOException {
        Path kaputtesBild = Files.write(tmp.resolve("kaputt.bin"),
                new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0 });
        Path keinBild = Files.write(tmp.resolve("text.bin"), "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
        Path kaputtesPdf = Files.write(tmp.resolve("kaputt-pdf.bin"), "%PDF-1.7\nkaputt".getBytes(StandardCharsets.US_ASCII));
        datei(12L, kaputtesBild, FirmendokumentDateiart.BILD);
        datei(13L, keinBild, FirmendokumentDateiart.BILD);
        datei(14L, kaputtesPdf, FirmendokumentDateiart.PDF);

        assertKeinVorschaubild(12L);
        assertKeinVorschaubild(13L);
        assertKeinVorschaubild(14L);
    }

    @Test
    @DisplayName("Wirft der Bild-Decoder eine Laufzeit-Ausnahme, wird daraus 404 mit Meldung")
    void decoderLaufzeitfehler() throws IOException {
        datei(15L, bildDatei("foto.bin", 50, 50, "png"), FirmendokumentDateiart.BILD);
        org.mockito.Mockito.doThrow(new ArrayIndexOutOfBoundsException("kaputter Decoder"))
                .when(bilder).erzeugeVerkleinert(any(Resource.class), anyInt());

        assertKeinVorschaubild(15L);
    }

    @Test
    @DisplayName("Unsichtbares oder unbekanntes Dokument: 404 wie beim Datei-Abruf, nichts wird umgerechnet")
    void nichtSichtbar() {
        ResponseStatusException fehlt = new ResponseStatusException(HttpStatus.NOT_FOUND, "Das Dokument gibt es nicht mehr.");
        for (long id : new long[] { 0L, -1L, Long.MAX_VALUE, 20L }) {
            given(dokumente.datei(id, max)).willThrow(fehlt);
            assertThatThrownBy(() -> service.vorschaubild(id, null, max)).isSameAs(fehlt);
        }
        verifyNoInteractions(bilder, pdfs);
    }

    @Test
    @DisplayName("Rechte werden auch bei vorhandenem Cache-Eintrag jedes Mal geprüft")
    void rechteTrotzCache() throws IOException {
        datei(7L, bildDatei("foto.bin", 800, 600, "jpg"), FirmendokumentDateiart.BILD);
        service.vorschaubild(7L, null, max);
        ResponseStatusException fehlt = new ResponseStatusException(HttpStatus.NOT_FOUND, "Das Dokument gibt es nicht mehr.");
        given(dokumente.datei(7L, max)).willThrow(fehlt);

        assertThatThrownBy(() -> service.vorschaubild(7L, null, max)).isSameAs(fehlt);
    }

    @Test
    @DisplayName("Cache: der zweite Abruf rechnet nicht neu; auch „kein Vorschaubild“ wird nur einmal ermittelt")
    void cache() throws IOException {
        datei(7L, bildDatei("foto.bin", 800, 600, "jpg"), FirmendokumentDateiart.BILD);
        Path kaputtesPdf = Files.write(tmp.resolve("kaputt-pdf.bin"), "%PDF-1.7\nkaputt".getBytes(StandardCharsets.US_ASCII));
        datei(14L, kaputtesPdf, FirmendokumentDateiart.PDF);

        byte[] erstes = service.vorschaubild(7L, null, max).jpeg();
        byte[] zweites = service.vorschaubild(7L, null, max).jpeg();
        assertKeinVorschaubild(14L);
        assertKeinVorschaubild(14L);

        assertThat(zweites).isSameAs(erstes);
        verify(bilder, times(1)).erzeugeVerkleinert(any(Resource.class), anyInt());
        verify(pdfs, times(1)).alsJpeg(any(Path.class), anyInt());
    }

    @Test
    @DisplayName("Ausgelastet: UmrechnungAusgelastetException, nichts landet im Cache; Vorhandenes kommt trotzdem")
    void ausgelastet() throws IOException {
        datei(7L, bildDatei("foto.bin", 800, 600, "jpg"), FirmendokumentDateiart.BILD);
        datei(8L, bildDatei("foto2.bin", 800, 600, "jpg"), FirmendokumentDateiart.BILD);
        byte[] vorhanden = service.vorschaubild(7L, null, max).jpeg();
        FirmendokumentVorschaubildService voll = new FirmendokumentVorschaubildService(dokumente, cache, bilder, pdfs,
                new Umrechnungsplaetze(0, 0), GROSSE_GRENZE);

        assertThatThrownBy(() -> voll.vorschaubild(8L, null, max)).isInstanceOf(UmrechnungAusgelastetException.class);
        assertThat(cache.hole(8L)).isNull();
        assertThat(voll.vorschaubild(7L, null, max).jpeg()).isSameAs(vorhanden);
    }

    @Test
    @DisplayName("Hat ein paralleler Abruf das Bild erzeugt, während dieser auf einen Platz wartete, wird nicht neu gerechnet")
    void parallelErzeugt() throws IOException {
        datei(7L, bildDatei("foto.bin", 800, 600, "jpg"), FirmendokumentDateiart.BILD);
        byte[] vonNebenan = { (byte) 0xFF, (byte) 0xD8, 1 };
        Umrechnungsplaetze nebenanFertig = new Umrechnungsplaetze(1, 0) {
            @Override
            public <T, E extends Exception> T belege(Umrechnung<T, E> umrechnung) throws E {
                cache.lege(7L, vonNebenan);
                return super.belege(umrechnung);
            }
        };
        FirmendokumentVorschaubildService mitAndrang = new FirmendokumentVorschaubildService(dokumente, cache, bilder,
                pdfs, nebenanFertig, GROSSE_GRENZE);

        assertThat(mitAndrang.vorschaubild(7L, null, max).jpeg()).isSameAs(vonNebenan);
        verifyNoInteractions(bilder);
    }

    @Test
    @DisplayName("Fehlerhafter gespeicherter Dateiname: Die Ausnahme aus datei() kommt durch, nichts wird gelesen")
    void nurPfadeAusDerAblage() {
        given(dokumente.datei(16L, max)).willThrow(new IllegalArgumentException("Ungültiger Dateiname"));

        assertThatThrownBy(() -> service.vorschaubild(16L, null, max)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(bilder, pdfs);
    }

    @Test
    @DisplayName("ETag hängt nur an der ID; passendes If-None-Match → unverändert (304) ohne Umrechnung")
    void etagUnveraendert() throws IOException {
        datei(7L, bildDatei("foto.bin", 800, 600, "jpg"), FirmendokumentDateiart.BILD);
        String etag = "\"firmendokument-vorschau-v1-7\"";

        FirmendokumentVorschaubildService.Vorschaubild erstes = service.vorschaubild(7L, null, max);
        assertThat(erstes.etag()).isEqualTo(etag);
        assertThat(erstes.unveraendert()).isFalse();

        cache.vergiss(7L); // auch ohne Cache-Eintrag wird für 304 nicht neu gerechnet
        for (String wennNichtGleich : new String[] { etag, "W/" + etag, "\"anders\", " + etag, "*" }) {
            FirmendokumentVorschaubildService.Vorschaubild antwort = service.vorschaubild(7L, wennNichtGleich, max);
            assertThat(antwort.unveraendert()).as(wennNichtGleich).isTrue();
            assertThat(antwort.jpeg()).isNull();
            assertThat(antwort.etag()).isEqualTo(etag);
        }
        verify(bilder, times(1)).erzeugeVerkleinert(any(Resource.class), anyInt());

        assertThat(service.vorschaubild(7L, "\"firmendokument-vorschau-v1-8\"", max).unveraendert()).isFalse();
        assertThat(service.vorschaubild(7L, "kein gültiger Wert <script>", max).unveraendert()).isFalse();
    }

    @Test
    @DisplayName("If-None-Match ohne Recht → trotzdem 404 (Rechte vor 304); SONSTIGE und gemerktes „kein Bild“ → 404")
    void etagNurMitRecht() throws IOException {
        ResponseStatusException fehlt = new ResponseStatusException(HttpStatus.NOT_FOUND, "Das Dokument gibt es nicht mehr.");
        given(dokumente.datei(20L, max)).willThrow(fehlt);
        Path word = Files.write(tmp.resolve("police.bin"), new byte[] { 'P', 'K', 3, 4 });
        datei(11L, word, FirmendokumentDateiart.SONSTIGE);
        Path kaputtesPdf = Files.write(tmp.resolve("kaputt-pdf.bin"), "%PDF-1.7\nkaputt".getBytes(StandardCharsets.US_ASCII));
        datei(14L, kaputtesPdf, FirmendokumentDateiart.PDF);
        assertKeinVorschaubild(14L);

        assertThatThrownBy(() -> service.vorschaubild(20L, "\"firmendokument-vorschau-v1-20\"", max)).isSameAs(fehlt);
        assertThatThrownBy(() -> service.vorschaubild(20L, "*", max)).isSameAs(fehlt);
        assertKeinVorschaubild(11L, "\"firmendokument-vorschau-v1-11\"");
        assertKeinVorschaubild(14L, "\"firmendokument-vorschau-v1-14\"");
    }

    @Test
    @DisplayName("Datei über der Größengrenze → 404 „kein Vorschaubild“ ohne Öffnen, wird gemerkt")
    void zuGross() throws IOException {
        FirmendokumentVorschaubildService klein = new FirmendokumentVorschaubildService(dokumente, cache, bilder, pdfs,
                new Umrechnungsplaetze(1, 0), 100);
        Path grossesPdf = Files.write(tmp.resolve("gross.bin"), new byte[101]);
        datei(21L, grossesPdf, FirmendokumentDateiart.PDF);
        datei(22L, bildDatei("gross-foto.bin", 50, 50, "jpg"), FirmendokumentDateiart.BILD); // JPEG-Kopf allein > 100 Bytes

        assertKeinVorschaubild(klein, 21L, null);
        assertKeinVorschaubild(klein, 22L, null);

        verifyNoInteractions(bilder, pdfs);
        assertThat(cache.hole(21L)).isEmpty();
        assertThat(cache.hole(22L)).isEmpty();
    }

    @Test
    @DisplayName("StackOverflowError im Renderer (bösartig verschachteltes PDF) → 404 „kein Vorschaubild“, gemerkt")
    void stackOverflow() throws IOException {
        Path pdf = tmp.resolve("tief.bin");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            doc.save(pdf.toFile());
        }
        datei(23L, pdf, FirmendokumentDateiart.PDF);
        org.mockito.Mockito.doThrow(new StackOverflowError()).when(pdfs).alsJpeg(any(Path.class), anyInt());

        assertKeinVorschaubild(23L);
        assertKeinVorschaubild(23L);

        verify(pdfs, times(1)).alsJpeg(any(Path.class), anyInt());
        assertThat(cache.hole(23L)).isEmpty();
    }

    @Test
    @DisplayName("Vorübergehender Lesefehler → 404 ohne Merken; der nächste Abruf versucht es neu")
    void voruebergehenderLesefehler() throws IOException {
        datei(24L, bildDatei("foto.bin", 800, 600, "jpg"), FirmendokumentDateiart.BILD);
        org.mockito.Mockito.doThrow(new IOException("Netzlaufwerk kurz weg")).doCallRealMethod()
                .when(bilder).erzeugeVerkleinert(any(Resource.class), anyInt());

        assertKeinVorschaubild(24L);
        assertThat(cache.hole(24L)).isNull();

        assertThat(lese(service.vorschaubild(24L, null, max).jpeg()).getWidth()).isEqualTo(512);
    }

    @Test
    @DisplayName("Datei verschwindet zwischen Rechteprüfung und Umrechnung → 404 ohne Merken")
    void dateiVerschwunden() throws IOException {
        Path foto = bildDatei("weg.bin", 80, 60, "jpg");
        datei(25L, foto, FirmendokumentDateiart.BILD);
        Files.delete(foto);

        assertKeinVorschaubild(25L);

        assertThat(cache.hole(25L)).isNull();
        verifyNoInteractions(bilder);
    }

    private void assertKeinVorschaubild(long id) {
        assertKeinVorschaubild(service, id, null);
    }

    private void assertKeinVorschaubild(long id, String wennNichtGleich) {
        assertKeinVorschaubild(service, id, wennNichtGleich);
    }

    private void assertKeinVorschaubild(FirmendokumentVorschaubildService dienst, long id, String wennNichtGleich) {
        assertThatThrownBy(() -> dienst.vorschaubild(id, wennNichtGleich, max))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(404);
                    assertThat(e.getReason()).isEqualTo(KEIN);
                });
    }

    private void datei(long id, Path pfad, FirmendokumentDateiart art) {
        MediaType typ = switch (art) {
            case PDF -> MediaType.APPLICATION_PDF;
            case BILD -> MediaType.IMAGE_JPEG;
            case SONSTIGE -> MediaType.APPLICATION_OCTET_STREAM;
        };
        given(dokumente.datei(id, max)).willReturn(new FirmendokumentService.Datei(pfad, "Max Mustermann.x", typ, art));
    }

    /** Graue Fläche (keine Personendaten) im gewünschten Format. */
    private Path bildDatei(String name, int breite, int hoehe, String format) throws IOException {
        BufferedImage bild = new BufferedImage(breite, hoehe, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bild.createGraphics();
        g.setColor(Color.GRAY);
        g.fillRect(0, 0, breite, hoehe);
        g.dispose();
        Path pfad = tmp.resolve(name);
        ImageIO.write(bild, format, pfad.toFile());
        return pfad;
    }

    private static BufferedImage lese(byte[] jpeg) throws IOException {
        assertThat(jpeg[0]).isEqualTo((byte) 0xFF);
        assertThat(jpeg[1]).isEqualTo((byte) 0xD8);
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }
}
