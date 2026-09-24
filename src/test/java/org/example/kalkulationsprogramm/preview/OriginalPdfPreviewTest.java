package org.example.kalkulationsprogramm.preview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OriginalPdfPreviewTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void rendersOriginalWorksheetWithCurrentMockPositions() throws Exception {
        byte[] pdf = OriginalPdfPreview.render(JSON.readTree("""
                {"art":"bedarf","id":7,"bestellungen":[
                  {"projektId":7,"projektName":"Musterhalle","projektNummer":"TEST-7",
                   "kundenName":"Max Mustermann","produktname":"Vierkantrohr 40 x 40",
                   "werkstoffName":"S235JR","rootKategorieId":1,"stueckzahl":3,
                   "menge":3,"einheit":"Stk","fixmassMm":1200},
                  {"projektId":8,"projektName":"Anderes Projekt","produktname":"Nicht enthalten"}]}
                """));
        try (PdfReader reader = new PdfReader(pdf)) {
            assertTrue(reader.getPageSizeWithRotation(1).getWidth() > reader.getPageSizeWithRotation(1).getHeight());
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertTrue(text.contains("Material-Bedarfsliste"));
            assertTrue(text.contains("Vierkantrohr 40 x 40"));
            assertTrue(text.contains("1200 mm"));
            assertTrue(text.contains("Musterhalle"));
            assertFalse(text.contains("Nicht enthalten"));
        }
    }

    @Test
    void rendersOriginalSupplierOrder() throws Exception {
        byte[] pdf = OriginalPdfPreview.render(JSON.readTree("""
                {"art":"bestellung","id":21,"bestellungen":[
                  {"projektId":7,"projektName":"Musterhalle","projektNummer":"TEST-7",
                   "lieferantId":21,"lieferantName":"Musterstahl","produktname":"Vierkantrohr",
                   "rootKategorieId":1,"stueckzahl":3,"menge":3,"einheit":"Stk"}]}
                """));
        try (PdfReader reader = new PdfReader(pdf)) {
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertTrue(text.contains("Vierkantrohr"));
            assertTrue(text.contains("Musterhalle"));
        }
    }

    @Test
    void rendersOriginalRequestWithSupplierTokenAndPositions() throws Exception {
        byte[] pdf = OriginalPdfPreview.render(JSON.readTree("""
                {"art":"preisanfrage","id":91,"bestellungen":[],
                 "preisanfrage":{"id":9,"nummer":"PA-TEST-9","bauvorhaben":"Musterhalle",
                    "antwortFrist":"2026-10-01","token":"DEMO-91"},
                 "positionen":[{"produktname":"Flachstahl","menge":6,"einheit":"m"}]}
                """));
        try (PdfReader reader = new PdfReader(pdf)) {
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertTrue(text.contains("PA-TEST-9"));
            assertTrue(text.contains("DEMO-91"));
            assertTrue(text.contains("Flachstahl"));
            assertTrue(text.contains("01.10.2026"));
        }
    }

    @Test
    void rejectsUnknownKindAndInvalidId() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> OriginalPdfPreview.render(JSON.readTree("{\"art\":\"mail\",\"id\":7}")));
        assertThrows(IllegalArgumentException.class,
                () -> OriginalPdfPreview.render(JSON.readTree("{\"art\":\"bedarf\",\"id\":0}")));
    }
}
