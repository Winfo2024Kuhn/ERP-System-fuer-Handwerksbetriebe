package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.example.kalkulationsprogramm.domain.AusgelesenePosition;
import org.example.kalkulationsprogramm.service.LieferantDokumentPositionLeser.KiAntwort;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class LieferantDokumentPositionLeserTest {

    private final LieferantDokumentPositionLeser leser = new LieferantDokumentPositionLeser(new ObjectMapper());

    private static byte[] pdfMitSeiten(int seiten) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < seiten; i++) {
                doc.addPage(new PDPage());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static int seitenzahl(byte[] pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        } catch (Exception e) {
            return -1;
        }
    }

    private static KiAntwort positionen(String... bezeichnungen) {
        StringBuilder json = new StringBuilder("{\"artikelPositionen\":[");
        for (int i = 0; i < bezeichnungen.length; i++) {
            json.append(i > 0 ? "," : "").append("{\"bezeichnung\":\"").append(bezeichnungen[i]).append("\"}");
        }
        return new KiAntwort(json.append("]}").toString(), false);
    }

    @Test
    void ganzesDokumentReicht() throws Exception {
        List<AusgelesenePosition> ergebnis = leser.lese(pdfMitSeiten(3), "application/pdf",
                (bytes, mime, prompt) -> positionen("Flachstahl", "Rundrohr"));

        assertThat(ergebnis).extracting(AusgelesenePosition::bezeichnung).containsExactly("Flachstahl", "Rundrohr");
    }

    @Test
    void abgeschnittenLiestSeitenweiseUndFuegtZusammen() throws Exception {
        List<Integer> aufrufe = new ArrayList<>();
        List<AusgelesenePosition> ergebnis = leser.lese(pdfMitSeiten(5), "application/pdf", (bytes, mime, prompt) -> {
            int seiten = seitenzahl(bytes);
            aufrufe.add(seiten);
            if (seiten == 5) {
                return new KiAntwort("{\"artikelPositionen\":[{\"bezeichnung\":\"abgeb", true);
            }
            return positionen("Block mit " + seiten + " Seiten");
        });

        // 5 Seiten -> Blöcke 2 + 2 + 1
        assertThat(aufrufe).containsExactly(5, 2, 2, 1);
        assertThat(ergebnis).hasSize(3);
    }

    @Test
    void zuDichterBlockWirdSeiteFuerSeiteGelesen() throws Exception {
        List<AusgelesenePosition> ergebnis = leser.lese(pdfMitSeiten(2), "application/pdf", (bytes, mime, prompt) -> {
            int seiten = seitenzahl(bytes);
            return seiten > 1 ? new KiAntwort("{", true) : positionen("Seite");
        });

        assertThat(ergebnis).hasSize(2);
    }

    @Test
    void scheitertEinBlockGibtEsKeineHalbeListe() throws Exception {
        int[] zweierBloecke = { 0 };
        List<AusgelesenePosition> ergebnis = leser.lese(pdfMitSeiten(4), "application/pdf", (bytes, mime, prompt) -> {
            int seiten = seitenzahl(bytes);
            if (seiten == 2 && zweierBloecke[0]++ == 0) {
                return positionen("erster Block ok");
            }
            return new KiAntwort("{", true); // zweiter Block und seine Einzelseiten scheitern
        });

        assertThat(ergebnis).isNull();
    }

    @Test
    void bildWirdNichtAufgeteilt() {
        List<AusgelesenePosition> ergebnis = leser.lese(new byte[] { 1, 2, 3 }, "image/jpeg",
                (bytes, mime, prompt) -> new KiAntwort("{", true));

        assertThat(ergebnis).isNull();
    }

    @Test
    void aufrufFehlgeschlagen() throws Exception {
        assertThat(leser.lese(pdfMitSeiten(1), "application/pdf", (b, m, p) -> null)).isNull();
        assertThat(leser.lese(null, "application/pdf", (b, m, p) -> positionen("x"))).isNull();
        assertThat(leser.lese(pdfMitSeiten(1), "application/pdf",
                (b, m, p) -> new KiAntwort("kein json", false))).isNull();
    }

    @Test
    void einseitigesPdfKannNichtWeiterGeteiltWerden() throws Exception {
        assertThat(leser.leseSeitenweise(pdfMitSeiten(1), "application/pdf", (b, m, p) -> positionen("x"))).isNull();
        assertThat(leser.leseSeitenweise(new byte[] { 0 }, "application/pdf", (b, m, p) -> positionen("x"))).isNull();
    }

    @Test
    void promptVerlangtNurPositionen() {
        assertThat(LieferantDokumentPositionLeser.PROMPT_NUR_POSITIONEN)
                .contains("artikelPositionen", "NEBENKOSTEN", "RABATT", "KEINE Summenzeilen");
    }
}
