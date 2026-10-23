package org.example.kalkulationsprogramm.util;

import org.openpdf.text.Document;
import org.openpdf.text.pdf.PdfCopy;
import org.openpdf.text.pdf.PdfWriter;

import java.io.OutputStream;

/**
 * Der einzige Weg, mit OpenPDF ein PDF zu schreiben oder zusammenzufuegen. Jedes Dokument bekommt
 * PDF 1.7.
 * <p>
 * OpenPDF 3 schreibt sonst "%PDF-2.0" in den Datei-Kopf. Die E-Rechnung (ZUGFeRD, PDF/A-3) baut
 * auf PDF 1.7 auf und verlangt "%PDF-1.n" (ISO 19005-3, 6.1.2); mit 2.0 faellt sie durch die
 * Pruefung. Die uebrigen Dokumente bleiben einheitlich auf derselben Version.
 * {@code PdfWriterFabrikTest} laesst den Build scheitern, wenn jemand {@code PdfWriter.getInstance}
 * oder {@code new PdfCopy} an der Fabrik vorbei aufruft.
 */
public final class PdfWriterFabrik {

    /** PDF-Version aller erzeugten Dokumente. */
    private static final String PDF_VERSION = PdfWriter.VERSION_1_7;

    private PdfWriterFabrik() {
    }

    /** Writer fuer ein neues Dokument; vor {@code document.open()} aufrufen. */
    public static PdfWriter writer(Document document, OutputStream out) {
        PdfWriter writer = PdfWriter.getInstance(document, out);
        writer.setPdfVersion(PDF_VERSION);
        return writer;
    }

    /** Writer zum Zusammenfuegen fertiger PDFs; vor {@code document.open()} aufrufen. */
    public static PdfCopy kopie(Document document, OutputStream out) {
        PdfCopy kopie = new PdfCopy(document, out);
        kopie.setPdfVersion(PDF_VERSION);
        return kopie;
    }
}
