package org.example.kalkulationsprogramm.service.pdf;

import org.example.kalkulationsprogramm.util.PdfWriterFabrik;
import org.junit.jupiter.api.Test;
import org.openpdf.text.Document;
import org.openpdf.text.Image;
import org.openpdf.text.Paragraph;

import java.io.ByteArrayOutputStream;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PdfBerichtBausteineTest {

    @Test
    void zahlenWerdenDeutschMitEinerNachkommastelleGeschrieben() {
        assertThat(PdfBerichtBausteine.zahl(new BigDecimal("1234.56"))).isEqualTo("1.234,6");
        assertThat(PdfBerichtBausteine.zahl(new BigDecimal("0"))).isEqualTo("0,0");
        assertThat(PdfBerichtBausteine.zahl(new BigDecimal("-3.5"))).isEqualTo("-3,5");
        assertThat(PdfBerichtBausteine.zahl(new BigDecimal("-0.01"))).isEqualTo("0,0");
        assertThat(PdfBerichtBausteine.zahl(null)).isEmpty();
    }

    @Test
    void logoSetztEineVerkleinerteKopieDesFirmenlogosUndLaesstOhneLogoNichtsWeg() throws Exception {
        Image logo = PdfDesignPruefung.testLogo();
        float breiteVorher = logo.getScaledWidth();

        byte[] mitLogo = pdfMitLogo(PdfDesign.aus(null, logo, ""));
        byte[] ohneLogo = pdfMitLogo(PdfDesign.aus(null, null, ""));

        assertThat(PdfDesignPruefung.anzahlBilder(mitLogo)).isEqualTo(1);
        assertThat(PdfDesignPruefung.anzahlBilder(ohneLogo)).isZero();
        assertThat(logo.getScaledWidth()).as("Original bleibt unverändert").isEqualTo(breiteVorher);
    }

    private static byte[] pdfMitLogo(PdfDesign design) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document();
        PdfWriterFabrik.writer(doc, out);
        doc.open();
        PdfBerichtBausteine.logo(doc, design, 150, 70);
        doc.add(new Paragraph("Inhalt"));
        doc.close();
        return out.toByteArray();
    }
}
