package org.example.kalkulationsprogramm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.kalkulationsprogramm.controller.DokumentGeneratorController.AbrechnungspositionRequest;
import org.example.kalkulationsprogramm.controller.DokumentGeneratorController.AbrechnungsverlaufRequest;
import org.example.kalkulationsprogramm.controller.DokumentGeneratorController.AbschlagInfoRequest;
import org.example.kalkulationsprogramm.controller.DokumentGeneratorController.DocBlockRequest;
import org.example.kalkulationsprogramm.controller.DokumentGeneratorController.GeneratePdfRequest;
import org.example.kalkulationsprogramm.controller.DokumentGeneratorController.KopfdatenRequest;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.controller.advice.RestExceptionHandler;
import org.example.kalkulationsprogramm.exception.FirmenstammdatenUnvollstaendigException;
import org.example.kalkulationsprogramm.mapper.ZugferdRechnungMapper;
import org.example.kalkulationsprogramm.service.FirmeninformationService;
import org.example.kalkulationsprogramm.service.RechnungPdfService;
import org.example.kalkulationsprogramm.service.ZugferdErstellService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mustangproject.ZUGFeRD.ZUGFeRDImporter;
import org.mustangproject.validator.ZUGFeRDValidator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Ende zu Ende: Anfrage aus dem Editor → sichtbares PDF mit eingebetteter E-Rechnung. Jede
 * erzeugte Datei muss den Mustang-Validator bestehen (PDF/A-3 und EN 16931), und die Beträge im
 * XML müssen auf den Cent mit dem gedruckten Zahlbetrag übereinstimmen.
 * Nur Dummy-Daten (Max Mustermann).
 */
@ExtendWith(MockitoExtension.class)
class DokumentGeneratorControllerZugferdExportTest {

    @Mock
    private FirmeninformationService firmeninformationService;

    @TempDir
    private Path dir;

    private DokumentGeneratorController controller;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        controller = new DokumentGeneratorController(new RechnungPdfService(),
                new ZugferdErstellService(firmeninformationService), new ZugferdRechnungMapper());
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler(null))
                .build();
    }

    private static FirmeninformationDto musterbetrieb() {
        FirmeninformationDto firma = new FirmeninformationDto();
        firma.setFirmenname("Musterbetrieb GmbH");
        firma.setStrasse("Musterstraße 1");
        firma.setPlz("12345");
        firma.setOrt("Musterstadt");
        firma.setEmail("info@musterbetrieb.example");
        firma.setUstIdNr("DE123456789");
        firma.setIban("DE89 3704 0044 0532 0130 00");
        return firma;
    }

    private record Export(String xml, String bericht, String pdfText) {
    }

    private Export exportiere(GeneratePdfRequest anfrage) throws Exception {
        when(firmeninformationService.getFirmeninformation()).thenReturn(musterbetrieb());
        ResponseEntity<byte[]> antwort = controller.generateZugferdPdf(anfrage);
        assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
        Path datei = dir.resolve("export.pdf");
        Files.write(datei, antwort.getBody());
        String pdfText;
        try (PDDocument doc = Loader.loadPDF(datei.toFile())) {
            pdfText = new PDFTextStripper().getText(doc);
        }
        return new Export(new ZUGFeRDImporter(datei.toString()).getUTF8(),
                new ZUGFeRDValidator().validate(datei.toString()), pdfText);
    }

    private static void assertGueltig(Export export) {
        assertGueltig(export.bericht());
    }

    /** PDF/A-3 und EN 16931: keine Fehler, Gesamturteil gültig. */
    private static void assertGueltig(String validatorBericht) {
        assertThat(validatorBericht).doesNotContain("<error").doesNotContain("status=\"invalid\"")
                .contains("<summary status=\"valid\"/>");
    }

    private static KopfdatenRequest kopf(String nummer, Integer zahlungszielTage) {
        return new KopfdatenRequest(nummer, "2026-10-12", "2026-10-05", "Max Mustermann",
                "Musterweg 7\n54321 Beispielstadt", "Badsanierung", "K-1001", null, null, null, null, null,
                zahlungszielTage);
    }

    private static DocBlockRequest leistung(String pos, String titel, double menge, String einheit, double preis,
            Double rabatt, boolean optional) {
        return new DocBlockRequest(pos, "SERVICE", null, pos, titel, menge, einheit, preis, null, optional,
                null, null, null, rabatt, null);
    }

    private static final List<DocBlockRequest> AUFTRAGSPOSITIONEN = List.of(
            leistung("1", "Fliesen verlegen", 35, "m²", 47.55, null, false),
            leistung("2", "Silikonfugen", 30, "m", 8.40, null, false),
            leistung("3", "Anfahrt", 1, "psch", 35, null, false));

    private static AbrechnungsverlaufRequest verlauf(AbrechnungspositionRequest... vorher) {
        return new AbrechnungsverlaufRequest("AB-2026-0007", "Auftragsbestätigung", "2026-09-01", 2000.0,
                List.of(vorher), true, null);
    }

    @Test
    void rechnungMitPositionsrabattPauschalrabattUndBedarfsposition() throws Exception {
        List<DocBlockRequest> bloecke = List.of(
                new DocBlockRequest("t", "TEXT", "<p>Sehr geehrter Herr Mustermann,</p>", null, null, null, null,
                        null, null, false, null, null, null, null, null),
                leistung("1", "Fliesen verlegen", 12.5, "m²", 47.55, 10.0, false),
                leistung("2", "Anfahrt", 1, "psch", 35, null, false),
                leistung("3", "Bedarfsposition Abdichtung", 1, "psch", 100, null, true));

        Export export = exportiere(new GeneratePdfRequest("RECHNUNG", "Standard", kopf("RE-2026-0042", 30),
                List.of(), bloecke, null, null, null, 5.0, null, null, null));

        assertGueltig(export);
        // 534,94 + 35,00 = 569,94 − Rabatt 28,50 = 541,44 netto + 102,87 USt = 644,31
        assertThat(export.xml()).contains("<ram:LineTotalAmount>534.94</ram:LineTotalAmount>",
                        "<ram:TaxBasisTotalAmount>541.44</ram:TaxBasisTotalAmount>",
                        "<ram:TaxTotalAmount currencyID=\"EUR\">102.87</ram:TaxTotalAmount>",
                        "<ram:DuePayableAmount>644.31</ram:DuePayableAmount>",
                        "<ram:CountryID>DE</ram:CountryID>", "<ram:CityName>Beispielstadt</ram:CityName>",
                        "<udt:DateTimeString format=\"102\">20261111</udt:DateTimeString>")
                .doesNotContain("Bedarfsposition");
        assertThat(export.pdfText()).contains("644,31");
    }

    @Test
    void schlussrechnungZiehtDieAbschlaegeAbWieImPdf() throws Exception {
        Export export = exportiere(new GeneratePdfRequest("SCHLUSSRECHNUNG", "Standard", kopf("RE-2026-0050", 14),
                List.of(), AUFTRAGSPOSITIONEN, null, null, null, null,
                verlauf(new AbrechnungspositionRequest("AR-2026-0001", "ABSCHLAGSRECHNUNG", "2026-09-15", 600.0, 1),
                        new AbrechnungspositionRequest("AR-2026-0002", "ABSCHLAGSRECHNUNG", "2026-09-29", 400.0, 2)),
                null, null));

        assertGueltig(export);
        // 1951,25 − 1000,00 = 951,25 netto + 180,74 USt = 1131,99
        assertThat(export.xml()).contains("<ram:LineTotalAmount>1951.25</ram:LineTotalAmount>",
                "<ram:AllowanceTotalAmount>1000.00</ram:AllowanceTotalAmount>",
                "<ram:TaxBasisTotalAmount>951.25</ram:TaxBasisTotalAmount>",
                "<ram:DuePayableAmount>1131.99</ram:DuePayableAmount>",
                "Bereits berechnet: Abschlagsrechnung Nr. AR-2026-0001 vom 15.09.2026");
        assertThat(export.pdfText()).contains("1.131,99");
    }

    @Test
    void abschlagsrechnungFordertDenFestenBetrag_ohneZahlungszielGeltenAchtTage() throws Exception {
        Export export = exportiere(new GeneratePdfRequest("ABSCHLAGSRECHNUNG", "Standard", kopf("AR-2026-0001", null),
                List.of(), AUFTRAGSPOSITIONEN, null, null, null, null, verlauf(), 1000.0,
                new AbschlagInfoRequest("prozent", 50.0)));

        assertGueltig(export);
        assertThat(export.xml()).contains("<ram:Name>Abschlagsrechnung</ram:Name>",
                        "<ram:Description>zu Auftragsbestätigung Nr. AB-2026-0007 vom 01.09.2026</ram:Description>",
                        "<ram:DuePayableAmount>1190.00</ram:DuePayableAmount>",
                        "<udt:DateTimeString format=\"102\">20261020</udt:DateTimeString>")
                .doesNotContain("Silikonfugen");
        assertThat(export.pdfText()).contains("1.190,00");
    }

    @Test
    void stornoUndAngebotSindGueltig_ohneZahlungsziel() throws Exception {
        Export storno = exportiere(new GeneratePdfRequest("STORNO", "Standard", kopf("ST-2026-0001", 14),
                List.of(), AUFTRAGSPOSITIONEN, null, null, null, null, null, null, null));
        assertGueltig(storno);
        assertThat(storno.xml()).contains("<ram:TypeCode>381</ram:TypeCode>", "Keine Zahlung fällig.")
                .doesNotContain("DueDateDateTime");

        Export angebot = exportiere(new GeneratePdfRequest("ANGEBOT", "Standard", kopf("AN-2026-0001", 14),
                List.of(), AUFTRAGSPOSITIONEN, null, null, null, null, null, null, null));
        assertGueltig(angebot);
        assertThat(angebot.xml()).contains("Keine Zahlung fällig.").doesNotContain("DueDateDateTime");
    }

    @Test
    void schlussrechnungVerweistAufIhreVorrechnungen() throws Exception {
        Export export = exportiere(new GeneratePdfRequest("SCHLUSSRECHNUNG", "Standard", kopf("RE-2026-0050", 14),
                List.of(), AUFTRAGSPOSITIONEN, null, null, null, null,
                verlauf(new AbrechnungspositionRequest("AR-2026-0001", "ABSCHLAGSRECHNUNG", "2026-09-15", 600.0, 1)),
                null, null));

        assertGueltig(export);
        assertThat(export.xml()).contains("<ram:InvoiceReferencedDocument>",
                "<ram:IssuerAssignedID>AR-2026-0001</ram:IssuerAssignedID>");
    }

    @Nested
    class UeberHttp {

        private static final ObjectMapper JSON = new ObjectMapper();

        private GeneratePdfRequest rechnung(KopfdatenRequest kopf, List<DocBlockRequest> bloecke,
                AbrechnungsverlaufRequest verlauf) {
            return new GeneratePdfRequest("RECHNUNG", "Standard", kopf, List.of(), bloecke, null, null, null, null,
                    verlauf, null, null);
        }

        @Test
        void liefertDieERechnungAlsDownload() throws Exception {
            when(firmeninformationService.getFirmeninformation()).thenReturn(musterbetrieb());

            byte[] pdf = mvc.perform(post("/api/dokument-generator/zugferd-pdf")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(rechnung(kopf("RE-2026/0042", 14), AUFTRAGSPOSITIONEN, null))))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                    // „/“ ist im Dateinamen nicht erlaubt
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                            org.hamcrest.Matchers.containsString("filename=\"zugferd_rechnung_RE-2026_0042.pdf\"")))
                    .andReturn().getResponse().getContentAsByteArray();

            Path datei = dir.resolve("download.pdf");
            Files.write(datei, pdf);
            assertThat(new ZUGFeRDImporter(datei.toString()).getUTF8()).contains("<ram:ID>RE-2026/0042</ram:ID>");
        }

        @Test
        void fehlendeFirmendatenGebenEineLesbareMeldung() throws Exception {
            when(firmeninformationService.getFirmeninformation()).thenReturn(new FirmeninformationDto());

            mvc.perform(post("/api/dokument-generator/zugferd-pdf")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(rechnung(kopf("RE-2026-0042", 14), AUFTRAGSPOSITIONEN, null))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Für die E-Rechnung fehlen Angaben")));
        }

        @Test
        void kaputtesDatumImAbrechnungsverlaufWirdAbgefangen() throws Exception {
            AbrechnungsverlaufRequest kaputt = new AbrechnungsverlaufRequest("AB-2026-0007", "Auftragsbestätigung",
                    "2026-13-45", 2000.0, List.of(), true, null);

            mvc.perform(post("/api/dokument-generator/zugferd-pdf")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(rechnung(kopf("RE-2026-0042", 14), AUFTRAGSPOSITIONEN, kaputt))))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        void ueberlangeUndSchadhafteEingabenErgebenTrotzdemEineGueltigeERechnung() throws Exception {
            when(firmeninformationService.getFirmeninformation()).thenReturn(musterbetrieb());
            String lang = "Fliesen ".repeat(1_500); // 12.000 Zeichen
            KopfdatenRequest kopf = new KopfdatenRequest("RE-2026-0042", "2026-10-12", "2026-10-05",
                    "<script>alert(1)</script>", lang + "\n'; DROP TABLE x; --\n54321 Beispielstadt", "Bad", "K-1001",
                    null, null, null, null, null, 14);
            List<DocBlockRequest> bloecke = List.of(new DocBlockRequest("1", "SERVICE", null, "1",
                    "<script>alert(1)</script>", 2.0, "<b>Stk</b>", 10.0, "<p>" + lang + "</p>", false, null, null, null,
                    null, null));

            byte[] pdf = mvc.perform(post("/api/dokument-generator/zugferd-pdf")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(rechnung(kopf, bloecke, null))))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();

            Path datei = dir.resolve("lang.pdf");
            Files.write(datei, pdf);
            String xml = new ZUGFeRDImporter(datei.toString()).getUTF8();
            assertThat(xml).doesNotContain("<script>").contains("&lt;script&gt;alert(1)&lt;/script&gt;")
                    .contains("unitCode=\"C62\"");
            assertGueltig(new ZUGFeRDValidator().validate(datei.toString()));
        }
    }

    @Test
    void ohneFirmendatenBrichtDerExportSprechendAb() {
        when(firmeninformationService.getFirmeninformation()).thenReturn(new FirmeninformationDto());

        assertThatThrownBy(() -> controller.generateZugferdPdf(new GeneratePdfRequest("RECHNUNG", "Standard",
                kopf("RE-2026-0042", 14), List.of(), AUFTRAGSPOSITIONEN, null, null, null, null, null, null, null)))
                .isInstanceOf(FirmenstammdatenUnvollstaendigException.class);
    }
}
