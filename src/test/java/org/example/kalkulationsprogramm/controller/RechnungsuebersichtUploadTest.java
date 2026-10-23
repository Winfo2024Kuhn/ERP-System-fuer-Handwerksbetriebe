package org.example.kalkulationsprogramm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService;
import org.example.kalkulationsprogramm.service.LieferantDateiablage;
import org.example.kalkulationsprogramm.service.LieferantDokumentDateiService;
import org.example.kalkulationsprogramm.testsupport.LieferantDateiablageImTestordner;
import org.example.kalkulationsprogramm.service.LieferantDokumentZugriffService;
import org.example.kalkulationsprogramm.service.RechnungsuebersichtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Manueller Rechnungs-Upload in der Rechnungsübersicht (Analyse und Speichern):
 * Der Dateiname aus dem Browser darf nie aus dem Upload-Ordner herausführen, und
 * angenommen werden nur PDF, XML, JPG und PNG. Testdaten sind Dummy-Daten (DSGVO).
 */
@WebMvcTest(RechnungsuebersichtController.class)
@AutoConfigureMockMvc(addFilters = false)
class RechnungsuebersichtUploadTest {

    private static final String MELDUNG_DATEI_NICHT_ERLAUBT =
            "Diese Datei kann hier nicht hochgeladen werden. Erlaubt sind PDF, XML (E-Rechnung), JPG und PNG.";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private RechnungsuebersichtService rechnungsuebersichtService;
    @MockitoBean private LieferantGeschaeftsdokumentRepository lieferantGeschaeftsdokumentRepository;
    @MockitoBean private MitarbeiterRepository mitarbeiterRepository;
    @MockitoBean private LieferantenRepository lieferantenRepository;
    @MockitoBean private GeminiDokumentAnalyseService geminiService;
    @MockitoBean private LieferantDokumentRepository lieferantDokumentRepository;
    @MockitoBean private LieferantDokumentZugriffService zugriffService;
    @MockitoBean private LieferantDokumentDateiService dokumentDateiService;
    @MockitoBean private LieferantDateiablage dateiablage;

    @TempDir private Path arbeitsordner;
    private Path uploads;

    @BeforeEach
    void uploadOrdnerInDenTestordnerLegen() throws IOException {
        uploads = arbeitsordner.resolve("uploads");
        LieferantDateiablageImTestordner.leiteUm(dateiablage, uploads);
    }

    private static MockMultipartFile datei(String name) {
        return new MockMultipartFile("datei", name, MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.4 Dummy".getBytes(StandardCharsets.UTF_8));
    }

    /** Meldet eine Größe knapp über der Grenze von 25 MB, ohne so viel Speicher zu belegen. */
    private static MockMultipartFile zuGrosseDatei() {
        return new MockMultipartFile("datei", "rechnung.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[] { 1 }) {
            @Override
            public long getSize() {
                return 25L * 1024 * 1024 + 1;
            }
        };
    }

    private static MockMultipartFile metadaten(String json) {
        return new MockMultipartFile("metadata", "", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
    }

    /** Alle Dateien, die der Test irgendwo im Arbeitsordner hinterlassen hat. */
    private List<Path> geschriebeneDateien() throws IOException {
        try (Stream<Path> dateien = Files.walk(arbeitsordner)) {
            return dateien.filter(Files::isRegularFile).toList();
        }
    }

    @Nested
    @DisplayName("POST /api/rechnungsuebersicht/import-upload")
    class Speichern {

        private static final String METADATEN = "{\"lieferantId\":7,\"dokumentNummer\":\"RE-2026-001\"}";

        @BeforeEach
        void lieferantVorhanden() {
            var lieferant = new Lieferanten();
            lieferant.setId(7L);
            lieferant.setLieferantenname("Musterbau GmbH");
            given(lieferantenRepository.findById(7L)).willReturn(Optional.of(lieferant));
            given(lieferantDokumentRepository.save(any())).willAnswer(aufruf -> {
                LieferantDokument dokument = aufruf.getArgument(0);
                dokument.setId(42L);
                return dokument;
            });
        }

        private LieferantDokument gespeichertesDokument() {
            ArgumentCaptor<LieferantDokument> captor = ArgumentCaptor.forClass(LieferantDokument.class);
            verify(lieferantDokumentRepository).save(captor.capture());
            return captor.getValue();
        }

        @Test
        @DisplayName("PDF landet im Ordner des Lieferanten und wird als Rechnung angelegt")
        void speichertPdfImOrdnerDesLieferanten() throws Exception {
            mockMvc.perform(multipart("/api/rechnungsuebersicht/import-upload")
                            .file(datei("rechnung.pdf"))
                            .file(metadaten(METADATEN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(42));

            List<Path> dateien = geschriebeneDateien();
            assertThat(dateien).hasSize(1);
            Path gespeichert = dateien.getFirst();
            assertThat(gespeichert.getParent()).isEqualTo(uploads.resolve("lieferanten").resolve("7"));
            assertThat(gespeichert.getFileName().toString()).endsWith("_rechnung.pdf");

            LieferantDokument dokument = gespeichertesDokument();
            assertThat(dokument.getOriginalDateiname()).isEqualTo("rechnung.pdf");
            assertThat(dokument.getGespeicherterDateiname()).isEqualTo(gespeichert.getFileName().toString());
            verify(lieferantGeschaeftsdokumentRepository).save(any());
        }

        static Stream<Arguments> namenMitPfadAngaben() {
            return Stream.of(
                    Arguments.of("../../../../rechnung.pdf", "rechnung.pdf"),
                    Arguments.of("..\\..\\..\\rechnung.pdf", "rechnung.pdf"),
                    Arguments.of("/etc/cron.d/rechnung.pdf", "rechnung.pdf"),
                    Arguments.of("C:\\Windows\\System32\\rechnung.pdf", "rechnung.pdf"),
                    Arguments.of("../8/rechnung.pdf", "rechnung.pdf"),
                    Arguments.of("<script>alert(1)</script>.pdf", "script_.pdf"),
                    Arguments.of("rechnung?:*|\".pdf", "rechnung_____.pdf"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("namenMitPfadAngaben")
        @DisplayName("Pfad-Angaben im Dateinamen werden entfernt – die Datei bleibt im Ordner des Lieferanten")
        void pfadAngabenImNamenFuehrenNichtAusDemOrdner(String rohName, String erwarteterName) throws Exception {
            mockMvc.perform(multipart("/api/rechnungsuebersicht/import-upload")
                            .file(datei(rohName))
                            .file(metadaten(METADATEN)))
                    .andExpect(status().isOk());

            List<Path> dateien = geschriebeneDateien();
            assertThat(dateien).hasSize(1);
            assertThat(dateien.getFirst().getParent()).isEqualTo(uploads.resolve("lieferanten").resolve("7"));
            assertThat(dateien.getFirst().getFileName().toString()).endsWith("_" + erwarteterName);
            assertThat(gespeichertesDokument().getOriginalDateiname()).isEqualTo(erwarteterName);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = { "rechnung.xml", "foto.jpg", "foto.jpeg", "foto.png", "RECHNUNG.PDF", "Foto.JpG" })
        @DisplayName("Alle erlaubten Dateitypen werden angenommen, egal ob groß oder klein geschrieben")
        void erlaubteDateitypenWerdenGespeichert(String name) throws Exception {
            mockMvc.perform(multipart("/api/rechnungsuebersicht/import-upload")
                            .file(datei(name))
                            .file(metadaten(METADATEN)))
                    .andExpect(status().isOk());

            assertThat(geschriebeneDateien()).hasSize(1);
            assertThat(gespeichertesDokument().getOriginalDateiname()).isEqualTo(name);
        }

        @ParameterizedTest(name = "[{index}] »{0}«")
        @ValueSource(strings = {
                "../../etc/passwd", "..\\..\\windows\\win.ini", "../../", "/", " ", "schadcode.exe", "start.bat",
                "skript.js", "seite.html", "bild.svg", "makro.ps1", "rechnung.pdf.exe", "rechnung" })
        @DisplayName("Gefährliche Dateitypen und Pfade ohne erlaubte Endung: 400, nichts wird gespeichert")
        void verboteneDateienWerdenAbgelehnt(String rohName) throws Exception {
            mockMvc.perform(multipart("/api/rechnungsuebersicht/import-upload")
                            .file(datei(rohName))
                            .file(metadaten(METADATEN)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(MELDUNG_DATEI_NICHT_ERLAUBT));

            assertThat(geschriebeneDateien()).isEmpty();
            verify(lieferantDokumentRepository, never()).save(any());
            verifyNoInteractions(lieferantGeschaeftsdokumentRepository);
        }

        static Stream<Arguments> ueberlangeNamen() {
            return Stream.of(
                    Arguments.of("a".repeat(10_000) + ".pdf"),
                    Arguments.of("ä".repeat(5_000) + ".pdf"),
                    Arguments.of("Rechnung_" + "😀".repeat(3_000) + ".PDF"));
        }

        @ParameterizedTest(name = "{index}")
        @MethodSource("ueberlangeNamen")
        @DisplayName("Überlange Dateinamen werden gekürzt – Endung bleibt, Dateisystem-Grenze von 255 Bytes hält")
        void ueberlangeNamenWerdenGekuerzt(String rohName) throws Exception {
            // Ohne UTF-8-Locale (z. B. LANG nicht gesetzt) kann die JVM Umlaute und Emojis gar nicht
            // als Dateinamen schreiben – das ist eine Grenze der Umgebung, nicht der Kürzung.
            assumeTrue(Charset.forName(System.getProperty("sun.jnu.encoding", "UTF-8")).newEncoder().canEncode(rohName),
                    "Dateinamen-Kodierung dieser JVM kann den Namen nicht darstellen");

            mockMvc.perform(multipart("/api/rechnungsuebersicht/import-upload")
                            .file(datei(rohName))
                            .file(metadaten(METADATEN)))
                    .andExpect(status().isOk());

            List<Path> dateien = geschriebeneDateien();
            assertThat(dateien).hasSize(1);
            String gespeichert = dateien.getFirst().getFileName().toString();
            assertThat(gespeichert.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(255);

            String original = gespeichertesDokument().getOriginalDateiname();
            assertThat(original.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(200);
            assertThat(original).endsWith(rohName.substring(rohName.lastIndexOf('.')));
            // Kein halbes Zeichen am Ende: Hin- und Rückweg über UTF-8 ändern nichts.
            assertThat(new String(original.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8))
                    .isEqualTo(original);
        }

        @Test
        @DisplayName("Zu große Datei: 400 mit Hinweis, nichts wird gespeichert")
        void zuGrosseDateiWirdAbgelehnt() throws Exception {
            mockMvc.perform(multipart("/api/rechnungsuebersicht/import-upload")
                            .file(zuGrosseDatei())
                            .file(metadaten(METADATEN)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Die Datei ist zu groß. Erlaubt sind höchstens 25 MB."));

            assertThat(geschriebeneDateien()).isEmpty();
            verify(lieferantDokumentRepository, never()).save(any());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(longs = { 0L, -1L, 999L, Long.MAX_VALUE })
        @DisplayName("Unbekannter oder ungültiger Lieferant: 400, nichts wird gespeichert")
        void unbekannterLieferantWirdAbgelehnt(long lieferantId) throws Exception {
            mockMvc.perform(multipart("/api/rechnungsuebersicht/import-upload")
                            .file(datei("rechnung.pdf"))
                            .file(metadaten("{\"lieferantId\":" + lieferantId + ",\"dokumentNummer\":\"RE-2026-001\"}")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Lieferant nicht gefunden."));

            assertThat(geschriebeneDateien()).isEmpty();
            verify(lieferantDokumentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Dokumentnummer gibt es beim Lieferanten schon: 409, nichts wird gespeichert")
        void doppelteDokumentnummerWirdAbgelehnt() throws Exception {
            given(lieferantGeschaeftsdokumentRepository.existsByLieferantIdAndDokumentNummer(7L, "RE-2026-001"))
                    .willReturn(true);

            mockMvc.perform(multipart("/api/rechnungsuebersicht/import-upload")
                            .file(datei("rechnung.pdf"))
                            .file(metadaten(METADATEN)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message")
                            .value("Ein Dokument mit dieser Nummer existiert bereits für diesen Lieferanten."));

            assertThat(geschriebeneDateien()).isEmpty();
            verify(lieferantDokumentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Leere Datei: 400, nichts wird gespeichert")
        void leereDateiWirdAbgelehnt() throws Exception {
            var leer = new MockMultipartFile("datei", "rechnung.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[0]);

            mockMvc.perform(multipart("/api/rechnungsuebersicht/import-upload")
                            .file(leer)
                            .file(metadaten(METADATEN)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Die Datei ist leer."));

            assertThat(geschriebeneDateien()).isEmpty();
            verify(lieferantDokumentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Ohne Lieferant: 400 mit Hinweis")
        void ohneLieferantWirdAbgelehnt() throws Exception {
            mockMvc.perform(multipart("/api/rechnungsuebersicht/import-upload")
                            .file(datei("rechnung.pdf"))
                            .file(metadaten("{\"dokumentNummer\":\"RE-2026-001\"}")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Bitte wählen Sie einen Lieferanten aus."));

            assertThat(geschriebeneDateien()).isEmpty();
        }

        @Test
        @DisplayName("Interner Fehler: 500 ohne interne Details, die schon geschriebene Datei wird entfernt")
        void internerFehlerVerraetKeineDetailsUndRaeumtAuf() throws Exception {
            willThrow(new IllegalStateException("interner Pfad /srv/geheim/rechnung.pdf"))
                    .given(lieferantDokumentRepository).save(any());

            mockMvc.perform(multipart("/api/rechnungsuebersicht/import-upload")
                            .file(datei("rechnung.pdf"))
                            .file(metadaten(METADATEN)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message")
                            .value("Die Rechnung konnte nicht gespeichert werden. Bitte noch einmal versuchen."));

            assertThat(geschriebeneDateien()).isEmpty();
        }
    }

    @Nested
    @DisplayName("POST /api/rechnungsuebersicht/analyze-upload")
    class Analysieren {

        private final Path tempOrdner = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();

        private void analyseLiefert(String dateiname) {
            var ergebnis = LieferantDokumentDto.MultiInvoiceAnalyzeResponse.builder().pageRange("1-2").build();
            given(geminiService.analyzeFileForMultipleInvoices(any(), eq(dateiname)))
                    .willReturn(new ArrayList<>(List.of(ergebnis)));
        }

        private Path analysierterPfad(String dateiname) {
            ArgumentCaptor<Path> captor = ArgumentCaptor.forClass(Path.class);
            verify(geminiService).analyzeFileForMultipleInvoices(captor.capture(), eq(dateiname));
            return captor.getValue();
        }

        @Test
        @DisplayName("PDF wird im Temp-Ordner analysiert und danach wieder gelöscht – ohne Nutzer-Dateinamen")
        void analysiertPdfUndRaeumtAuf() throws Exception {
            analyseLiefert("Rechnung_Max_Mustermann.pdf");

            mockMvc.perform(multipart("/api/rechnungsuebersicht/analyze-upload")
                            .file(datei("Rechnung_Max_Mustermann.pdf")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].pageRange").value("1-2"));

            Path pfad = analysierterPfad("Rechnung_Max_Mustermann.pdf").toAbsolutePath().normalize();
            assertThat(pfad.getParent()).isEqualTo(tempOrdner);
            assertThat(pfad.getFileName().toString()).startsWith("rechnung-analyse-").endsWith(".pdf")
                    .doesNotContain("Mustermann");
            assertThat(pfad).doesNotExist();
        }

        @Test
        @DisplayName("Pfad-Angaben im Dateinamen werden entfernt – die Datei bleibt im Temp-Ordner")
        void pfadAngabenImNamenFuehrenNichtAusDemTempOrdner() throws Exception {
            analyseLiefert("rechnung.pdf");

            mockMvc.perform(multipart("/api/rechnungsuebersicht/analyze-upload")
                            .file(datei("../../../../rechnung.pdf")))
                    .andExpect(status().isOk());

            Path pfad = analysierterPfad("rechnung.pdf").toAbsolutePath().normalize();
            assertThat(pfad.getParent()).isEqualTo(tempOrdner);
            assertThat(pfad).doesNotExist();
        }

        @Test
        @DisplayName("Zu große Datei: 400, keine Analyse")
        void zuGrosseDateiWirdNichtAnalysiert() throws Exception {
            mockMvc.perform(multipart("/api/rechnungsuebersicht/analyze-upload").file(zuGrosseDatei()))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(geminiService);
        }

        @ParameterizedTest(name = "[{index}] »{0}«")
        @ValueSource(strings = {
                "../../etc/passwd", "..\\..\\windows\\win.ini", "../../", "/", " ", "schadcode.exe", "start.bat",
                "skript.js", "seite.html", "bild.svg", "makro.ps1", "rechnung.pdf.exe", "rechnung" })
        @DisplayName("Gefährliche Dateitypen und Pfade ohne erlaubte Endung: 400, keine Analyse")
        void verboteneDateienWerdenNichtAnalysiert(String rohName) throws Exception {
            mockMvc.perform(multipart("/api/rechnungsuebersicht/analyze-upload").file(datei(rohName)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(geminiService);
        }

        @Test
        @DisplayName("Leere Datei: 400, keine Analyse")
        void leereDateiWirdNichtAnalysiert() throws Exception {
            var leer = new MockMultipartFile("datei", "rechnung.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[0]);

            mockMvc.perform(multipart("/api/rechnungsuebersicht/analyze-upload").file(leer))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(geminiService);
        }

        @Test
        @DisplayName("Fehler in der Analyse: 500, Temp-Datei wird trotzdem gelöscht")
        void fehlerInDerAnalyseRaeumtAuf() throws Exception {
            given(geminiService.analyzeFileForMultipleInvoices(any(), eq("rechnung.pdf")))
                    .willThrow(new IllegalStateException("Dienst nicht erreichbar"));

            mockMvc.perform(multipart("/api/rechnungsuebersicht/analyze-upload").file(datei("rechnung.pdf")))
                    .andExpect(status().isInternalServerError());

            assertThat(analysierterPfad("rechnung.pdf")).doesNotExist();
        }
    }
}
