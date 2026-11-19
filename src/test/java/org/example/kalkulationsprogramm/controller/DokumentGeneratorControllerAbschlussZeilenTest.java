package org.example.kalkulationsprogramm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.kalkulationsprogramm.controller.advice.RestExceptionHandler;
import org.example.kalkulationsprogramm.mapper.ZugferdRechnungMapper;
import org.example.kalkulationsprogramm.service.RechnungPdfService;
import org.example.kalkulationsprogramm.service.ZugferdErstellService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * PDF-Anfrage mit angepassten Zeilen im Abrechnungsstand ({@code abrechnungsverlauf.dieseRechnungZeile}
 * und {@code .differenzZeile}): exakte JSON-Feldnamen, optionale Felder, serverseitige Grenzen.
 * Nutzerfall: Angebot 6.430,76 € netto, eine Rechnung über 7.710,90 € schließt den Auftrag ab.
 * Nur Dummy-Daten.
 */
class DokumentGeneratorControllerAbschlussZeilenTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        DokumentGeneratorController controller = new DokumentGeneratorController(new RechnungPdfService(),
                mock(ZugferdErstellService.class), new ZugferdRechnungMapper());
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler(null))
                .build();
    }

    /** Baut die Anfrage; {@code zusatz} wird roh in {@code abrechnungsverlauf} eingesetzt. */
    private static String anfrage(String zusatz) {
        return """
                {
                  "dokumentTyp": "RECHNUNG",
                  "kopfdaten": {"dokumentnummer": "RE-2026-0042", "rechnungsDatum": "2026-11-12",
                                "kundenName": "Max Mustermann", "kundenAdresse": "Musterweg 7\\n54321 Beispielstadt"},
                  "layoutBlocks": [],
                  "contentBlocks": [
                    {"id": "1", "type": "SERVICE", "pos": "1", "title": "Treppengeländer", "quantity": 1,
                     "unit": "psch", "price": 7710.90}
                  ],
                  "abrechnungsverlauf": {
                    "basisdokumentNummer": "AG-2026/03/00003",
                    "basisdokumentTyp": "Angebot",
                    "basisdokumentDatum": "2026-03-02",
                    "basisdokumentBetragNetto": 6430.76,
                    "positionen": [],
                    "differenzHinweis": "Treppengeländer Edelstahl mit Glasfüllung",
                    "schliesstAuftragAb": true%s
                  }
                }
                """.formatted(zusatz);
    }

    private String pdfText(String json) throws Exception {
        byte[] pdf = mvc.perform(post("/api/dokument-generator/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    void ohneNeueFelderBleibtAllesWieBisher() throws Exception {
        String text = pdfText(anfrage(""));

        assertThat(text).contains("diese Rechnung", "Zusätzliche Leistungen",
                "Treppengeländer Edelstahl mit Glasfüllung", "1.523,37 €", "9.175,97 €");
    }

    @Test
    void mitNullObjektenBleibtAllesWieBisher() throws Exception {
        String text = pdfText(anfrage(", \"dieseRechnungZeile\": null, \"differenzZeile\": null"));

        assertThat(text).contains("diese Rechnung", "Zusätzliche Leistungen", "1.523,37 €");
    }

    @Test
    void vertragsbeispielBenenntUmUndBlendetDieDifferenzzeileAus() throws Exception {
        String text = pdfText(anfrage("""
                ,
                "dieseRechnungZeile": { "bezeichnung": "Rechnung Treppengeländer", "unterzeile": "Fertigstellung 11/2026", "ausblenden": false },
                "differenzZeile":     { "bezeichnung": "Mehrleistung", "unterzeile": "Glasfüllung statt Lochblech", "ausblenden": true }
                """));

        assertThat(text).contains("Rechnung Treppengeländer", "Fertigstellung 11/2026", "9.175,97 €");
        assertThat(text).doesNotContain("diese Rechnung", "Mehrleistung", "Glasfüllung statt Lochblech",
                "Zusätzliche Leistungen", "1.523,37");
    }

    @Test
    void einzelneFelderSindOptional() throws Exception {
        String text = pdfText(anfrage(", \"differenzZeile\": { \"bezeichnung\": \"Mehrleistung\" }"));

        assertThat(text).contains("diese Rechnung", "Mehrleistung", "+ 1.523,37 €",
                "Treppengeländer Edelstahl mit Glasfüllung");
    }

    @Test
    void ueberlangeTexteUndSteuerzeichenWerdenGekuerztStattAbgelehnt() throws Exception {
        String lang = "B".repeat(10_000);
        String text = pdfText(anfrage(", \"dieseRechnungZeile\": { \"bezeichnung\": \"" + lang
                + "\", \"unterzeile\": \"Zeile 1\\nZeile 2\\u0000Ende\" }"));

        // Das PDF bricht das lange Wort um; ohne Zeilenumbrueche zaehlt die Laenge.
        String ohneUmbrueche = text.replaceAll("\\s++", "");
        assertThat(ohneUmbrueche).contains("B".repeat(79) + "…").doesNotContain("B".repeat(80));
        assertThat(text).contains("Zeile 1 Zeile 2 Ende");
    }

    @Test
    void sqlUndHtmlBleibenWoertlicherText() throws Exception {
        String text = pdfText(anfrage(", \"dieseRechnungZeile\": { \"bezeichnung\": \"<script>alert(1)</script>\","
                + " \"unterzeile\": \"'; DROP TABLE x; --\" }"));

        assertThat(text).contains("<script>alert(1)</script>", "'; DROP TABLE x; --");
    }
}
