package org.example.kalkulationsprogramm.service.vorschau;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Rendert die erste Seite eines PDFs als kleines JPEG (Kachel-Vorschau), auf weißem Hintergrund.
 *
 * <p>Speicherschonend: Das PDF wird direkt aus der Datei gelesen (PDFBox puffert es nicht komplett im Heap); was
 * PDFBox beim Lesen zwischenspeichern muss, landet in Temp-Dateien statt im Heap
 * ({@link IOUtils#createTempFileOnlyStreamCache()}, im Temp-Ordner der JVM, beim Schließen des Dokuments wieder
 * gelöscht). Nur Seite 1 wird angefasst, und zwar gleich in der Zielgröße – der Maßstab ergibt sich aus der längeren Seitenkante,
 * es wird nie erst groß gerendert und dann verkleinert. Eingebettete Fotos (Scans) darf PDFBox dabei ausgedünnt
 * dekodieren ({@link PDFRenderer#setSubsamplingAllowed}). Vektorinhalt wird immer auf genau {@code maxKante} Pixel
 * längste Kante gerendert, auch bei kleinen Seiten (das ist kein Hochskalieren eines Rasterbildes).</p>
 *
 * <p>Alles, was sich nicht darstellen lässt – beschädigte Datei, Öffnen-Passwort, keine Seiten, Seite ohne Fläche,
 * Fehler im Renderer – ergibt {@code null}, keine Ausnahme. Geloggt wird nur die Fehlerart, kein Dateiname. Ein
 * {@link StackOverflowError} (bösartig tief verschachteltes PDF) geht an den Aufrufer, der ihn als „nicht darstellbar“
 * behandelt; {@link OutOfMemoryError} wird nirgends abgefangen.</p>
 *
 * <p>Wie viele Seiten gleichzeitig gerendert werden und wie groß die Datei höchstens sein darf, begrenzt der Aufrufer
 * ({@link Umrechnungsplaetze}).</p>
 */
@Component
public class PdfErsteSeiteAlsBild {

    private static final Logger log = LoggerFactory.getLogger(PdfErsteSeiteAlsBild.class);

    /**
     * Erste Seite als JPEG mit längster Kante {@code maxKante} Pixel.
     *
     * @return das JPEG oder {@code null}, wenn das PDF nicht darstellbar ist
     */
    public byte[] alsJpeg(Path pdf, int maxKante) {
        try (PDDocument dokument = Loader.loadPDF(pdf.toFile(), IOUtils.createTempFileOnlyStreamCache())) {
            BufferedImage seite = rendereErsteSeite(dokument, maxKante);
            return seite == null ? null : jpeg(seite);
        } catch (IOException | RuntimeException e) {
            // InvalidPasswordException ist eine IOException; Renderer-Fehler in kaputten Inhalten oft Laufzeitfehler
            log.debug("PDF-Vorschau nicht möglich: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private static BufferedImage rendereErsteSeite(PDDocument dokument, int maxKante) throws IOException {
        if (dokument.getNumberOfPages() == 0) {
            return null;
        }
        // Gerendert wird die CropBox; die längere Kante bleibt auch bei /Rotate 90 die längere
        PDRectangle flaeche = dokument.getPage(0).getCropBox();
        float laengsteKante = Math.max(flaeche.getWidth(), flaeche.getHeight());
        if (!(laengsteKante > 0) || Float.isInfinite(laengsteKante)) {
            return null;
        }
        PDFRenderer renderer = new PDFRenderer(dokument);
        renderer.setSubsamplingAllowed(true);
        // RGB statt ARGB: PDFBox füllt den Hintergrund dann weiß, wie auf Papier
        return renderer.renderImage(0, maxKante / laengsteKante, ImageType.RGB);
    }

    private static byte[] jpeg(BufferedImage bild) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(bild, "jpg", out);
        return out.toByteArray();
    }
}
