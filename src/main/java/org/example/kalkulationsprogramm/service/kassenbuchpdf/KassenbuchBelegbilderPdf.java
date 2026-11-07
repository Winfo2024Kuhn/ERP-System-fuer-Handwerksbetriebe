package org.example.kalkulationsprogramm.service.kassenbuchpdf;

import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.service.pdf.PdfDesign;
import org.openpdf.text.BadElementException;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Image;
import org.openpdf.text.Paragraph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.example.kalkulationsprogramm.service.kassenbuchpdf.KassenbuchTexte.formatEuro;
import static org.example.kalkulationsprogramm.service.kassenbuchpdf.KassenbuchTexte.kategorieLabel;
import static org.example.kalkulationsprogramm.service.kassenbuchpdf.KassenbuchTexte.nullSafe;
import static org.example.kalkulationsprogramm.service.kassenbuchpdf.KassenbuchTexte.nullToEmpty;
import static org.example.kalkulationsprogramm.service.kassenbuchpdf.KassenbuchTexte.verwendungszweck;

/**
 * Hängt die Belegbilder an das Kassenbuch-PDF an, ein Beleg pro Seite. Bisher behauptete
 * das PDF nur, die Fotos laegen "im selben Ordner" -- damit war der Ausdruck allein
 * wertlos, sobald der Ordner auseinanderfiel.
 *
 * <p>Ueber jedem Bild steht die laufende Nummer, das Datum, der Betrag und der
 * Fingerabdruck der Datei. Damit laesst sich jedes Foto genau einer Zeile im Journal
 * zuordnen und pruefen, dass es nicht ausgetauscht wurde. PDF-Belege lassen sich nicht
 * als Bild einbetten; fuer sie wird nur eine Referenzseite erzeugt. Die Datei selbst
 * liegt im ZIP-Paket.</p>
 */
@Slf4j
public final class KassenbuchBelegbilderPdf {

    /** Bildformate, die sich direkt ins PDF einbetten lassen. PDF-Belege werden nur referenziert. */
    private static final Set<String> EINBETTBARE_MIME_TYPES = Set.of("image/jpeg", "image/jpg", "image/png");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final Path belegOrdner;

    /** @param uploadPath Wurzel der Uploads ({@code upload.path}); die Bilder liegen darunter in {@code belege}. */
    public KassenbuchBelegbilderPdf(String uploadPath) {
        this.belegOrdner = Path.of(uploadPath, "belege").toAbsolutePath().normalize();
    }

    public void anhaengen(Document doc, List<Beleg> belege) throws DocumentException {
        List<Beleg> mitDatei = belege.stream()
                .filter(b -> b.getGespeicherterDateiname() != null && !b.getGespeicherterDateiname().isBlank())
                .toList();
        if (mitDatei.isEmpty()) return;

        Font ueberschrift = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, PdfDesign.TEXT_DUNKEL);
        Font zeile = FontFactory.getFont(FontFactory.HELVETICA, 9, PdfDesign.TEXT_ZELLE);

        doc.newPage();
        Paragraph titel = new Paragraph("Belege zu diesem Kassenbuch", ueberschrift);
        titel.setSpacingAfter(6f);
        doc.add(titel);
        doc.add(new Paragraph(
                mitDatei.size() + " Belege, ein Beleg je Seite. Die Zeichenfolge unter jedem Beleg ist "
                + "der Fingerabdruck der Bilddatei – stimmt er, wurde das Bild seit der Prüfung nicht "
                + "ausgetauscht.", zeile));

        for (Beleg b : mitDatei) {
            belegSeite(doc, b, zeile);
        }
    }

    private void belegSeite(Document doc, Beleg b, Font zeile) throws DocumentException {
        doc.newPage();
        Font hashFont = FontFactory.getFont(FontFactory.COURIER, 7, PdfDesign.TEXT_GEDAEMPFT);

        String kopf = "Beleg Nr. " + (b.getLaufendeNummer() != null ? b.getLaufendeNummer() : "ohne Nummer")
                + "  ·  " + (b.getBelegDatum() != null ? b.getBelegDatum().format(DATE_FMT) : "ohne Datum")
                + "  ·  " + formatEuro(nullSafe(b.getBetragBrutto())) + " €"
                + "  ·  " + kategorieLabel(b.getBelegKategorie())
                + (b.getLieferant() != null ? "  ·  " + b.getLieferant().getLieferantenname() : "");
        doc.add(new Paragraph(kopf, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, PdfDesign.TEXT_DUNKEL)));

        String zweck = verwendungszweck(b);
        if (!zweck.isBlank()) {
            doc.add(new Paragraph(zweck, zeile));
        }
        doc.add(new Paragraph("Datei: " + nullToEmpty(b.getOriginalDateiname())
                + (b.getDateiHash() != null ? "   ·   SHA-256: " + b.getDateiHash() : ""), hashFont));

        Image bild = ladeBelegbild(b);
        if (bild != null) {
            // Auf den verbleibenden Seitenbereich einpassen, damit auch
            // ein Hochformat-Scan vollstaendig sichtbar bleibt.
            bild.scaleToFit(doc.getPageSize().getWidth() - 100, doc.getPageSize().getHeight() - 160);
            bild.setAlignment(Element.ALIGN_CENTER);
            doc.add(bild);
        } else {
            Font hinweis = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, PdfDesign.TEXT_GEDAEMPFT);
            doc.add(new Paragraph(" ", hinweis));
            doc.add(new Paragraph(
                    istPdfBeleg(b)
                        ? "Dieser Beleg liegt als PDF vor und ist im Ordner \"belege\" des Export-Pakets "
                          + "unter dem oben genannten Dateinamen enthalten."
                        : "Das Belegbild konnte nicht eingebunden werden. Die Datei liegt im Ordner "
                          + "\"belege\" des Export-Pakets.", hinweis));
        }
    }

    private static boolean istPdfBeleg(Beleg b) {
        return b.getMimeType() != null && b.getMimeType().toLowerCase(Locale.ROOT).contains("pdf");
    }

    /**
     * Laedt ein Belegbild aus dem Upload-Verzeichnis. Der gespeicherte
     * Dateiname stammt aus einer UUID, wird hier aber trotzdem gegen
     * Pfad-Traversal geprueft -- Defense-in-Depth kostet hier nichts.
     */
    private Image ladeBelegbild(Beleg b) {
        String name = b.getGespeicherterDateiname();
        if (name == null || name.isBlank()) return null;
        String mime = b.getMimeType() != null ? b.getMimeType().toLowerCase(Locale.ROOT) : "";
        if (!EINBETTBARE_MIME_TYPES.contains(mime)) return null;
        if (name.contains("..") || name.contains("/") || name.contains("\\")) return null;

        Path datei = belegOrdner.resolve(name).normalize();
        if (!datei.startsWith(belegOrdner) || !Files.exists(datei)) return null;
        try {
            return Image.getInstance(datei.toString());
        } catch (IOException | BadElementException e) {
            log.warn("Belegbild {} konnte nicht ins PDF eingebettet werden: {}", name, e.getMessage());
            return null;
        }
    }
}
