package org.example.kalkulationsprogramm.service.kassenbuchpdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.service.pdf.PdfDesignPruefung;
import org.example.kalkulationsprogramm.util.PdfWriterFabrik;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpdf.text.Document;
import org.openpdf.text.Paragraph;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Belegbilder-Anhang des Kassenbuchs: ein Beleg je Seite, Bild nur aus dem Belegordner. Nur Dummy-Daten. */
class KassenbuchBelegbilderPdfTest {

    @TempDir
    Path uploads;

    @Test
    void bettetPngBelegEinUndVerweistBeiPdfUndUnsicheremNamenAufDasPaket() throws Exception {
        Files.createDirectories(uploads.resolve("belege"));
        BufferedImage bild = new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(bild, "png", uploads.resolve("belege").resolve("foto.png").toFile());

        Beleg foto = beleg(1L, "foto.png", "image/png");
        Beleg pdfBeleg = beleg(2L, "rechnung.pdf", "application/pdf");
        Beleg boese = beleg(3L, "../foto.png", "image/png");
        Beleg ohneDatei = beleg(4L, null, "image/png");

        byte[] pdf = erzeuge(List.of(foto, pdfBeleg, boese, ohneDatei));

        assertThat(PdfDesignPruefung.anzahlBilder(pdf)).isEqualTo(1);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(5); // Vorspann + Übersicht + 3 Belege
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("Belege zu diesem Kassenbuch", "3 Belege", "Beleg Nr. 1",
                    "liegt als PDF vor", "konnte nicht eingebunden werden", "Barverkauf Max Mustermann");
        }
    }

    @Test
    void ohneBelegdateienKommtKeinAnhang() throws Exception {
        byte[] pdf = erzeuge(List.of(beleg(1L, " ", "image/png")));

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
        }
    }

    private byte[] erzeuge(List<Beleg> belege) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document();
        PdfWriterFabrik.writer(doc, out);
        doc.open();
        doc.add(new Paragraph("Kassenbuch"));
        new KassenbuchBelegbilderPdf(uploads.toString()).anhaengen(doc, belege);
        doc.close();
        return out.toByteArray();
    }

    private static Beleg beleg(Long nummer, String datei, String mime) {
        Beleg b = new Beleg();
        b.setId(nummer);
        b.setLaufendeNummer(nummer);
        b.setBelegKategorie(BelegKategorie.KASSE_AUSGABE);
        b.setBelegDatum(LocalDate.of(2026, 3, 5));
        b.setBetragBrutto(new BigDecimal("12.50"));
        b.setBeschreibung("Barverkauf Max Mustermann");
        b.setGespeicherterDateiname(datei);
        b.setOriginalDateiname("beleg.png");
        b.setMimeType(mime);
        b.setDateiHash("abc");
        return b;
    }
}
