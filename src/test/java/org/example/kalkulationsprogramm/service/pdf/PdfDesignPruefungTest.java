package org.example.kalkulationsprogramm.service.pdf;

import org.example.kalkulationsprogramm.util.PdfWriterFabrik;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.pdf.PdfPTable;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Gegenprobe für den Test-Helfer: findet gesetzte Farben und Bilder, aber keine fremden. */
class PdfDesignPruefungTest {

    private static final Color BLAU = new Color(0x1d, 0x4e, 0xd8);

    @Test
    void liestGekuerzteRgbWerteAusDemInhaltsstrom() {
        assertThat(PdfDesignPruefung.enthaeltFarbe("BT 0.11373 0.30588 0.84706 rg ET", BLAU)).isTrue();
        assertThat(PdfDesignPruefung.enthaeltFarbe("0.11373 0.30588 0.84706 RG", BLAU)).isTrue();
        assertThat(PdfDesignPruefung.enthaeltFarbe("1 1 1 rg", Color.WHITE)).isTrue();
        assertThat(PdfDesignPruefung.enthaeltFarbe(".5 0 0 rg", new Color(128, 0, 0))).isTrue();
        assertThat(PdfDesignPruefung.enthaeltFarbe("0.11373 0.30588 0.84706 rgx", BLAU)).isFalse();
        assertThat(PdfDesignPruefung.enthaeltFarbe("0.86275 0.14902 0.14902 rg", BLAU)).isFalse();
    }

    @Test
    void findetDieFirmenfarbeUndDasLogoImEchtenPdf() throws Exception {
        byte[] mitFarbe = pdf(PdfDesign.aus("#1d4ed8", PdfDesignPruefung.testLogo(), "Musterbetrieb GmbH"));
        byte[] ohneFarbe = pdf(PdfDesign.aus(null, null, "Musterbetrieb GmbH"));

        assertThat(PdfDesignPruefung.enthaeltFarbe(mitFarbe, BLAU)).isTrue();
        assertThat(PdfDesignPruefung.enthaeltFarbe(mitFarbe, PdfFarben.STANDARD_AKZENT)).isFalse();
        assertThat(PdfDesignPruefung.enthaeltFarbe(ohneFarbe, PdfFarben.STANDARD_AKZENT)).isTrue();
        assertThat(PdfDesignPruefung.enthaeltFarbe(ohneFarbe, BLAU)).isFalse();
        assertThat(PdfDesignPruefung.anzahlBilder(mitFarbe)).isEqualTo(1);
        assertThat(PdfDesignPruefung.anzahlBilder(ohneFarbe)).isZero();
    }

    private static byte[] pdf(PdfDesign design) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document();
        PdfWriterFabrik.writer(doc, out);
        doc.open();
        PdfBerichtBausteine.kopf(doc, design, "Titel", "Untertitel");
        PdfPTable tabelle = new PdfPTable(1);
        tabelle.addCell(PdfBerichtBausteine.kopfZelle("Spalte", design, Element.ALIGN_LEFT));
        doc.add(tabelle);
        doc.close();
        return out.toByteArray();
    }
}
