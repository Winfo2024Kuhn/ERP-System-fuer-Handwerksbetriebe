package org.example.kalkulationsprogramm.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openpdf.text.Document;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfCopy;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfWriterFabrikTest {

    private static final Path QUELLEN = Path.of("src", "main", "java");

    private static final Path FABRIK = QUELLEN.resolve(
            Path.of("org", "example", "kalkulationsprogramm", "util", "PdfWriterFabrik.java"));

    /**
     * Ein PdfWriter oder PdfCopy, der nicht aus der Fabrik kommt – auch mit Zeilenumbruch, Leerzeichen
     * vor der Klammer, statischem Import oder voll qualifiziertem Namen. Kommentare und Strings werden
     * mit durchsucht: Wer dort {@code PdfWriter.getInstance} schreibt, bekommt einen roten Build.
     */
    private static final Pattern WRITER_AN_DER_FABRIK_VORBEI = Pattern.compile(
            "PdfWriter\\s*+\\.\\s*+getInstance\\b|new\\s++(?:\\w++\\.)*+Pdf(?:Smart)?Copy\\s*+\\(");

    private static String kopf(byte[] pdf) {
        return new String(pdf, 0, 9, StandardCharsets.ISO_8859_1);
    }

    private static byte[] einseitigesPdf(Function<ByteArrayOutputStream, Document> anlegen) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = anlegen.apply(out);
        document.open();
        document.add(new Paragraph("Max Mustermann"));
        document.close();
        return out.toByteArray();
    }

    private static Document ueberFabrik(ByteArrayOutputStream out) {
        Document document = new Document();
        PdfWriterFabrik.writer(document, out);
        return document;
    }

    /** So schreibt OpenPDF 3 ohne Fabrik: PDF 2.0. */
    private static Document ohneFabrik(ByteArrayOutputStream out) {
        Document document = new Document();
        PdfWriter.getInstance(document, out);
        return document;
    }

    @Test
    @DisplayName("Neues Dokument beginnt mit dem Kopf %PDF-1.7, den die E-Rechnung (PDF/A-3) verlangt")
    void writerSchreibtPdf17() {
        assertEquals("%PDF-2.0\n", kopf(einseitigesPdf(PdfWriterFabrikTest::ohneFabrik)),
                "Gegenprobe: ohne Fabrik schreibt OpenPDF PDF 2.0. Faellt sie, schreibt OpenPDF wieder 1.x;"
                        + " dann Fabrik und BACKEND_ARCH.md pruefen.");
        assertEquals("%PDF-1.7\n", kopf(einseitigesPdf(PdfWriterFabrikTest::ueberFabrik)));
    }

    @Test
    @DisplayName("Zusammengefuegtes Dokument ist PDF 1.7, auch wenn die Teile PDF 2.0 sind")
    void kopieSchreibtPdf17() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PdfReader teil = new PdfReader(einseitigesPdf(PdfWriterFabrikTest::ohneFabrik))) {
            Document document = new Document();
            PdfCopy kopie = PdfWriterFabrik.kopie(document, out);
            document.open();
            kopie.addPage(kopie.getImportedPage(teil, 1));
            kopie.addPage(kopie.getImportedPage(teil, 1));
            document.close();
        }

        byte[] pdf = out.toByteArray();
        assertEquals("%PDF-1.7\n", kopf(pdf));
        try (PdfReader ergebnis = new PdfReader(pdf)) {
            assertEquals(2, ergebnis.getNumberOfPages());
        }
    }

    @Test
    @DisplayName("Schutztest erkennt auch umbrochene, statisch importierte und voll qualifizierte Aufrufe")
    void schutzmusterErkenntUmgehungen() {
        List.of("PdfWriter.getInstance(doc, out)",
                "PdfWriter\n        .getInstance(doc, out)",
                "PdfWriter.getInstance (doc, out)",
                "import static org.openpdf.text.pdf.PdfWriter.getInstance;",
                "new PdfCopy(doc, out)",
                "new PdfCopy (doc, out)",
                "new PdfCopy\n(doc, out)",
                "new PdfSmartCopy(doc, out)",
                "new org.openpdf.text.pdf.PdfCopy(doc, out)")
                .forEach(code -> assertTrue(WRITER_AN_DER_FABRIK_VORBEI.matcher(code).find(), code));
        List.of("PdfWriterFabrik.writer(doc, out)", "PdfWriterFabrik.kopie(doc, out)", "PdfWriter writer = x;")
                .forEach(code -> assertFalse(WRITER_AN_DER_FABRIK_VORBEI.matcher(code).find(), code));
    }

    @Test
    @DisplayName("Kein Dienst erzeugt einen PdfWriter oder PdfCopy an der Fabrik vorbei")
    void keinWriterAnDerFabrikVorbei() throws IOException {
        List<Path> javaDateien;
        try (Stream<Path> dateien = Files.walk(QUELLEN)) {
            javaDateien = dateien.filter(datei -> datei.toString().endsWith(".java")).toList();
        }
        assertTrue(javaDateien.contains(FABRIK), "Fabrik nicht gefunden – sucht der Test im falschen Ordner?");

        List<String> treffer = javaDateien.stream()
                .filter(datei -> !datei.equals(FABRIK))
                .filter(PdfWriterFabrikTest::nutztWriterDirekt)
                .map(Path::toString)
                .sorted()
                .toList();
        assertTrue(treffer.isEmpty(), "Diese Dateien erzeugen PDFs ohne PdfWriterFabrik und bekommen so PDF 2.0: "
                + treffer);
    }

    private static boolean nutztWriterDirekt(Path datei) {
        try {
            return WRITER_AN_DER_FABRIK_VORBEI.matcher(Files.readString(datei)).find();
        } catch (IOException e) {
            throw new IllegalStateException("Quelldatei nicht lesbar: " + datei, e);
        }
    }
}
