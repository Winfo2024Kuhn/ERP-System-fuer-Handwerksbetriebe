package org.example.kalkulationsprogramm.service.vorschau;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Erste PDF-Seite als kleines JPEG; Test-PDFs entstehen hier mit PDFBox (keine Personendaten). */
class PdfErsteSeiteAlsBildTest {

    @TempDir Path tmp;

    private final PdfErsteSeiteAlsBild renderer = new PdfErsteSeiteAlsBild();

    @Test
    @DisplayName("A4 hochkant → JPEG, längste Kante genau 512, Seitenverhältnis bleibt, Hintergrund weiß")
    void a4Hochkant() throws IOException {
        Path pdf = pdf(PDRectangle.A4, 2);

        BufferedImage bild = lese(renderer.alsJpeg(pdf, 512));

        assertThat(bild.getHeight()).isEqualTo(512);
        assertThat(bild.getWidth()).isBetween(360, 363);
        assertThat(hell(bild.getRGB(2, 2))).as("Ecke ist weiß").isTrue();
        assertThat(hell(bild.getRGB(bild.getWidth() / 2, bild.getHeight() / 2))).as("Mitte ist das schwarze Rechteck")
                .isFalse();
    }

    @Test
    @DisplayName("Querformat → Breite 512; nur die erste Seite zählt")
    void querformat() throws IOException {
        Path pdf = pdf(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()), 3);

        BufferedImage bild = lese(renderer.alsJpeg(pdf, 512));

        assertThat(bild.getWidth()).isEqualTo(512);
        assertThat(bild.getHeight()).isBetween(360, 363);
    }

    @Test
    @DisplayName("Gedrehte Seite (/Rotate 90) bleibt in der Kantenlänge")
    void gedrehteSeite() throws IOException {
        Path pdf = tmp.resolve("gedreht.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage seite = new PDPage(PDRectangle.A4);
            seite.setRotation(90);
            doc.addPage(seite);
            doc.save(pdf.toFile());
        }

        BufferedImage bild = lese(renderer.alsJpeg(pdf, 512));

        assertThat(bild.getWidth()).isEqualTo(512);
        assertThat(bild.getHeight()).isLessThan(512);
    }

    @Test
    @DisplayName("Kaputte Datei, PDF ohne Seiten und PDF mit Öffnen-Passwort → null statt Ausnahme")
    void nichtDarstellbar() throws IOException {
        Path kaputt = Files.write(tmp.resolve("kaputt.pdf"), "%PDF-1.7\nkein echtes PDF".getBytes(StandardCharsets.US_ASCII));
        Path leer = tmp.resolve("leer.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.save(leer.toFile());
        }
        Path geschuetzt = tmp.resolve("geschuetzt.pdf");
        try (PDDocument doc = dokument(PDRectangle.A4, 1)) {
            StandardProtectionPolicy schutz = new StandardProtectionPolicy("eigentuemer", "nutzer", new AccessPermission());
            schutz.setEncryptionKeyLength(128);
            doc.protect(schutz);
            doc.save(geschuetzt.toFile());
        }

        assertThat(renderer.alsJpeg(kaputt, 512)).isNull();
        assertThat(renderer.alsJpeg(leer, 512)).isNull();
        assertThat(renderer.alsJpeg(geschuetzt, 512)).isNull();
        assertThat(renderer.alsJpeg(tmp.resolve("gibt-es-nicht.pdf"), 512)).isNull();
    }

    @Test
    @DisplayName("Seite ohne Fläche (Breite 0) → null statt Division durch null")
    void seiteOhneFlaeche() throws IOException {
        Path pdf = pdf(new PDRectangle(0, 0), 1);

        assertThat(renderer.alsJpeg(pdf, 512)).isNull();
    }

    private Path pdf(PDRectangle format, int seiten) throws IOException {
        Path pdf = tmp.resolve("test-" + System.nanoTime() + ".pdf");
        try (PDDocument doc = dokument(format, seiten)) {
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    /** Seiten mit einem schwarzen Rechteck in der Mitte. */
    private static PDDocument dokument(PDRectangle format, int seiten) throws IOException {
        PDDocument doc = new PDDocument();
        for (int i = 0; i < seiten; i++) {
            PDPage seite = new PDPage(format);
            doc.addPage(seite);
            try (PDPageContentStream inhalt = new PDPageContentStream(doc, seite)) {
                inhalt.setNonStrokingColor(Color.BLACK);
                inhalt.addRect(format.getWidth() / 4, format.getHeight() / 4, format.getWidth() / 2,
                        format.getHeight() / 2);
                inhalt.fill();
            }
        }
        return doc;
    }

    private static BufferedImage lese(byte[] jpeg) throws IOException {
        assertThat(jpeg).isNotNull();
        assertThat(jpeg[0]).isEqualTo((byte) 0xFF);
        assertThat(jpeg[1]).isEqualTo((byte) 0xD8);
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }

    private static boolean hell(int rgb) {
        Color c = new Color(rgb);
        return c.getRed() > 230 && c.getGreen() > 230 && c.getBlue() > 230;
    }
}
