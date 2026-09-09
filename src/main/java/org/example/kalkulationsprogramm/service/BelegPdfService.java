package org.example.kalkulationsprogramm.service;

import com.lowagie.text.BadElementException;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Erzeugt die beiden Belegarten, die das Programm selbst ausstellt statt sie
 * einzuscannen: die Quittung an einen bar zahlenden Kunden (§ 33 UStDV
 * Kleinbetragsrechnung bzw. § 14 UStG) und den Eigenbeleg (Ersatzbeleg) fuer
 * Ausgaben ohne Fremdbeleg (kein Vorsteuerabzug).
 *
 * <p>Statt einer handschriftlichen Unterschrift tragen beide PDFs unten den
 * Block "Erstellt von &lt;Name&gt; am &lt;Zeitpunkt&gt; Uhr" -- GoBD verlangt
 * nur, dass Ersteller und Zeitpunkt nachvollziehbar und unveraenderbar sind.
 * Der SHA-256-Fingerabdruck der fertigen Datei wird berechnet und im Record
 * {@link ErzeugtesPdf#sha256()} zurueckgegeben, aber bewusst nicht ins PDF
 * gedruckt: den Hash ueber den Inhalt ohne die Hash-Zeile zu berechnen und
 * dann eine Zeile mit genau diesem Hash anzuhaengen waere ein Zirkelschluss.
 * Der Hash landet stattdessen ueber {@code Beleg.dateiHash} an der Buchung,
 * genau dort, wo ihn auch ein gescannter Beleg hat.</p>
 *
 * <p>Layout und Farbpalette sind bewusst an {@link BelegeKasseExportPdfService}
 * angelehnt (gleicher Briefkopf, gleiche OpenPDF-Bibliothek), aber als eigene
 * private Kopien gehalten -- dieser Service kennt {@code Beleg} nicht und
 * arbeitet ausschliesslich mit den beiden Daten-Records.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BelegPdfService {

    private final FirmeninformationRepository firmeninformationRepository;

    @Value("${upload.path:uploads}")
    private String uploadPath;

    // Dieselbe Farbpalette wie BelegeKasseExportPdfService (Zeile 79-88 dort) --
    // eigene Kopie, damit dieser Service unabhaengig bleibt.
    private static final Color HEADER_BG   = new Color(220, 38, 38);   // rose-600
    private static final Color BORDER      = new Color(229, 231, 235); // slate-200
    private static final Color TEXT_DARK   = new Color(30, 41, 59);    // slate-800
    private static final Color TEXT_MUTED  = new Color(100, 116, 139); // slate-500
    private static final Color TEXT_CELL   = new Color(55, 65, 81);    // slate-700
    private static final Color FOOTER_GREY = new Color(148, 163, 184); // slate-400

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TS_FMT   = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private static final BigDecimal UST_DV_GRENZE = new BigDecimal("250");

    /**
     * Kundenquittung zu einer Bareinnahme. Datei liegt unter
     * {@code {upload.path}/belege/<UUID>_Quittung-<datum>.pdf}.
     */
    public ErzeugtesPdf erzeugeQuittung(QuittungDaten daten) {
        List<String[]> zeilen = new ArrayList<>();
        zeilen.add(new String[]{"Betrag", formatEuro(daten.bruttoBetrag()) + " €"});
        zeilen.add(new String[]{"davon MwSt (" + formatProzent(daten.mwstSatz()) + ")",
                formatEuro(mwstBetragAusSatz(daten.bruttoBetrag(), daten.mwstSatz())) + " €"});
        zeilen.add(new String[]{"Datum", daten.datum().format(DATE_FMT)});
        zeilen.add(new String[]{"Von wem", nullToEmpty(daten.vonWem())});
        zeilen.add(new String[]{"Wofür", nullToEmpty(daten.wofuer())});
        if (istGesetzt(daten.sachkontoLabel())) {
            zeilen.add(new String[]{"Konto", daten.sachkontoLabel()});
        }
        if (istGesetzt(daten.rechnungsNummer())) {
            zeilen.add(new String[]{"Zu Rechnung", daten.rechnungsNummer()});
        }

        StringBuilder fusstext = new StringBuilder(
                "Diese Quittung ist der Beleg zur Buchung im Kassenbuch. "
                + "Sie ist kein Kassenbon einer Registrierkasse.");
        if (daten.bruttoBetrag() != null && daten.bruttoBetrag().compareTo(UST_DV_GRENZE) > 0) {
            fusstext.append("\nÜber 250 € braucht der Kunde eine Rechnung mit seinen vollständigen Angaben.");
        }

        return schreibeUndHashe("Quittung", "Quittung", zeilen, fusstext.toString(),
                daten.ersteller(), daten.datum());
    }

    /** Ersatzbeleg (Eigenbeleg) ohne Vorsteuer. */
    public ErzeugtesPdf erzeugeEigenbeleg(EigenbelegDaten daten) {
        List<String[]> zeilen = new ArrayList<>();
        zeilen.add(new String[]{"Betrag", formatEuro(daten.bruttoBetrag()) + " €"});
        zeilen.add(new String[]{"Datum", daten.datum().format(DATE_FMT)});
        zeilen.add(new String[]{"An wen", istGesetzt(daten.anWen()) ? daten.anWen() : "–"});
        zeilen.add(new String[]{"Wofür", nullToEmpty(daten.zweck())});
        zeilen.add(new String[]{"Grund", nullToEmpty(daten.grund())});
        zeilen.add(new String[]{"Bezahlt mit", nullToEmpty(daten.zahlungsart())});
        zeilen.add(new String[]{"Konto", istGesetzt(daten.sachkontoLabel()) ? daten.sachkontoLabel() : "–"});

        String fusstext = "Ohne Fremdbeleg gibt es keine Vorsteuer.";

        return schreibeUndHashe("Ersatzbeleg (Eigenbeleg)", "Ersatzbeleg", zeilen, fusstext,
                daten.ersteller(), daten.datum());
    }

    // ===================== Gemeinsames Layout =====================

    /**
     * Baut das PDF im Speicher, schreibt es unter {@code {upload.path}/belege}
     * weg und berechnet erst danach den Fingerabdruck der fertigen Datei.
     */
    private ErzeugtesPdf schreibeUndHashe(String anzeigeTitel, String dateiTitel, List<String[]> zeilen,
                                           String fusstext, Mitarbeiter ersteller, LocalDate datum) {
        try {
            byte[] pdf = erzeugePdfBytes(anzeigeTitel, zeilen, fusstext, ersteller);

            Path ordner = Paths.get(uploadPath, "belege");
            Files.createDirectories(ordner);

            String originalDateiname = dateiTitel + "-" + datum + ".pdf";
            String gespeicherterDateiname = UUID.randomUUID() + "_" + originalDateiname;
            Path datei = ordner.resolve(gespeicherterDateiname);
            Files.write(datei, pdf);

            String hash = berechneDateiHash(datei);
            return new ErzeugtesPdf(gespeicherterDateiname, originalDateiname, "application/pdf", hash);
        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Erzeugen des Belege-PDFs", e);
        }
    }

    private byte[] erzeugePdfBytes(String titel, List<String[]> zeilen, String fusstext, Mitarbeiter ersteller)
            throws DocumentException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // A4 hochkant -- anders als das Kassenbuch-PDF (BelegeKasseExportPdfService), das quer liegt.
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();

        Firmeninformation firma = firmeninformationRepository.findFirmeninformation().orElse(null);
        addBriefkopf(doc, firma);
        addTitel(doc, titel);
        addWerteTabelle(doc, zeilen);
        addFusstext(doc, fusstext);
        addErstellerZeile(doc, ersteller);

        doc.close();
        return out.toByteArray();
    }

    /**
     * Briefkopf mit Firmenlogo links und Firmenstammdaten rechts -- eigene
     * Kopie von {@code BelegeKasseExportPdfService.addBriefkopf}. Fallbacks:
     * Logo nicht gepflegt/Datei fehlt: nur Firmen-Text rechts. Keine
     * Firmeninformation in der DB: nur das (eventuelle) Static-Logo. Beides
     * fehlt: stillschweigend ueberspringen -- PDF bleibt valide.
     */
    private void addBriefkopf(Document doc, Firmeninformation firma) throws DocumentException {
        Image logo = ladeFirmenlogo(firma);
        if (logo == null && firma == null) {
            return;
        }
        PdfPTable kopf = new PdfPTable(new float[]{2f, 5f});
        kopf.setWidthPercentage(100);
        kopf.setSpacingAfter(8f);

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (logo != null) {
            logo.scaleToFit(140, 70);
            logoCell.addElement(logo);
        }
        kopf.addCell(logoCell);

        PdfPCell infoCell = new PdfPCell();
        infoCell.setBorder(Rectangle.NO_BORDER);
        infoCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        infoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        if (firma != null) {
            Font firmenname = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, TEXT_DARK);
            Font line       = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_CELL);
            Font lineMuted  = FontFactory.getFont(FontFactory.HELVETICA, 8, TEXT_MUTED);

            addRightLine(infoCell, firma.getFirmenname(), firmenname);
            addRightLine(infoCell, joinNonEmpty(" ", firma.getStrasse()), line);
            addRightLine(infoCell, joinNonEmpty(" ", firma.getPlz(), firma.getOrt()), line);
            String kontakt = joinNonEmpty(" · ",
                    prefix("Tel. ", firma.getTelefon()),
                    prefix("", firma.getEmail()),
                    prefix("", firma.getWebsite()));
            addRightLine(infoCell, kontakt, lineMuted);
            String steuer = joinNonEmpty(" · ",
                    prefix("St.-Nr. ", firma.getSteuernummer()),
                    prefix("USt-IdNr. ", firma.getUstIdNr()));
            addRightLine(infoCell, steuer, lineMuted);
        }
        kopf.addCell(infoCell);

        doc.add(kopf);
    }

    private Image ladeFirmenlogo(Firmeninformation firma) {
        String dateiname = firma != null ? firma.getLogoDateiname() : null;
        if (dateiname != null && !dateiname.isBlank()) {
            // Pfad-Traversal blocken -- Defense-in-Depth, der Upload-Pfad sollte
            // den Dateinamen ohnehin sanitisieren, aber wir pruefen hier nochmal.
            String safe = dateiname.trim();
            if (!safe.contains("..") && !safe.contains("/") && !safe.contains("\\")) {
                Path base = Paths.get(uploadPath, "firma", "logo").toAbsolutePath().normalize();
                Path logoPath = base.resolve(safe).normalize();
                if (logoPath.startsWith(base) && Files.exists(logoPath)) {
                    try {
                        return Image.getInstance(logoPath.toString());
                    } catch (IOException | BadElementException ex) {
                        // Datei kaputt / kein gueltiges Bild -- Fallback unten greift
                    }
                }
            }
        }
        try {
            java.net.URL url = getClass().getResource("/static/firmenlogo_icon.png");
            if (url != null) return Image.getInstance(url);
        } catch (IOException | BadElementException ignored) {
            // Kein Logo verfuegbar -- PDF wird ohne Logo gerendert.
        }
        return null;
    }

    private void addRightLine(PdfPCell cell, String text, Font font) {
        if (text == null || text.isBlank()) return;
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_RIGHT);
        cell.addElement(p);
    }

    private String joinNonEmpty(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(p.trim());
        }
        return sb.toString();
    }

    private String prefix(String prefix, String value) {
        if (value == null || value.isBlank()) return null;
        return prefix + value.trim();
    }

    private void addTitel(Document doc, String titel) throws DocumentException {
        Font titelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, TEXT_DARK);
        Paragraph p = new Paragraph(titel, titelFont);
        p.setSpacingBefore(4f);
        p.setSpacingAfter(12f);
        doc.add(p);
    }

    /** Zweispaltige Werte-Tabelle: links das Label, rechts der Wert. */
    private void addWerteTabelle(Document doc, List<String[]> zeilen) throws DocumentException {
        PdfPTable t = new PdfPTable(new float[]{2f, 3f});
        t.setWidthPercentage(100);
        t.setSpacingAfter(16f);
        for (String[] zeile : zeilen) {
            t.addCell(labelZelle(zeile[0]));
            t.addCell(wertZelle(zeile[1]));
        }
        doc.add(t);
    }

    private PdfPCell labelZelle(String text) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, TEXT_CELL);
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, f));
        c.setHorizontalAlignment(Element.ALIGN_LEFT);
        setzePadding(c);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(BORDER);
        c.setBorderWidth(0.5f);
        return c;
    }

    private PdfPCell wertZelle(String text) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA, 10, TEXT_DARK);
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "–" : text, f));
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        setzePadding(c);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(BORDER);
        c.setBorderWidth(0.5f);
        return c;
    }

    private void setzePadding(PdfPCell c) {
        c.setPaddingTop(5f);
        c.setPaddingBottom(5f);
        c.setPaddingLeft(5f);
        c.setPaddingRight(5f);
    }

    private void addFusstext(Document doc, String fusstext) throws DocumentException {
        if (fusstext == null || fusstext.isBlank()) return;
        Font f = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_MUTED);
        for (String zeile : fusstext.split("\n")) {
            Paragraph p = new Paragraph(zeile, f);
            p.setSpacingBefore(3f);
            doc.add(p);
        }
    }

    /**
     * Ersetzt die handschriftliche Unterschrift: Ersteller und Zeitpunkt,
     * nachvollziehbar und unveraenderbar (GoBD, Entscheidung 3 des
     * Orchestrators). Der SHA-256 der Datei wird bewusst NICHT hier gedruckt
     * -- er landet nur im Record {@link ErzeugtesPdf#sha256()}, siehe
     * Klassen-Javadoc.
     */
    private void addErstellerZeile(Document doc, Mitarbeiter ersteller) throws DocumentException {
        Font f = FontFactory.getFont(FontFactory.HELVETICA, 8, FOOTER_GREY);
        String name = ersteller != null
                ? (nullToEmpty(ersteller.getVorname()) + " " + nullToEmpty(ersteller.getNachname())).trim()
                : "";
        String zeile = "Erstellt von " + (name.isEmpty() ? "unbekannt" : name)
                + " am " + LocalDateTime.now().format(TS_FMT) + " Uhr";
        Paragraph p = new Paragraph(zeile, f);
        p.setSpacingBefore(18f);
        doc.add(p);
    }

    // ===================== Zahlen & Hash =====================

    private BigDecimal mwstBetragAusSatz(BigDecimal brutto, BigDecimal satz) {
        if (brutto == null || satz == null || satz.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal faktor = BigDecimal.ONE.add(satz.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        BigDecimal netto = brutto.divide(faktor, 2, RoundingMode.HALF_UP);
        return brutto.subtract(netto).setScale(2, RoundingMode.HALF_UP);
    }

    private String formatEuro(BigDecimal v) {
        BigDecimal x = v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
        return String.format(java.util.Locale.GERMAN, "%,.2f", x);
    }

    private String formatProzent(BigDecimal satz) {
        if (satz == null) return "0 %";
        return satz.stripTrailingZeros().toPlainString().replace('.', ',') + " %";
    }

    private boolean istGesetzt(String s) {
        return s != null && !s.isBlank();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * SHA-256 der gespeicherten PDF-Datei, blockweise gelesen -- eigene Kopie
     * von {@code BelegService.berechneDateiHash} (dort privat, kein Umbau an
     * BelegService).
     */
    private String berechneDateiHash(Path datei) {
        try (InputStream in = Files.newInputStream(datei)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] puffer = new byte[8192];
            int gelesen;
            while ((gelesen = in.read(puffer)) > 0) {
                md.update(puffer, 0, gelesen);
            }
            StringBuilder sb = new StringBuilder(64);
            for (byte b : md.digest()) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("Fingerabdruck fuer PDF {} konnte nicht berechnet werden: {}",
                    datei.getFileName(), e.getMessage());
            return null;
        }
    }

    // ===================== Records =====================

    public record ErzeugtesPdf(String gespeicherterDateiname,
                                String originalDateiname,
                                String mimeType,      // immer "application/pdf"
                                String sha256) {}

    public record QuittungDaten(BigDecimal bruttoBetrag,
                                 BigDecimal mwstSatz,
                                 LocalDate datum,
                                 String vonWem,            // Gegenpartei
                                 String wofuer,            // Beschreibung
                                 String sachkontoLabel,    // "8400 Erlöse 19 %", darf null sein
                                 String rechnungsNummer,   // zugeordnete Ausgangsrechnung, darf null sein
                                 Mitarbeiter ersteller) {}

    public record EigenbelegDaten(BigDecimal bruttoBetrag,
                                   LocalDate datum,
                                   String anWen,           // Gegenpartei, darf null sein
                                   String zweck,
                                   String grund,            // warum es keinen Fremdbeleg gibt
                                   String zahlungsart,      // "Bar"
                                   String sachkontoLabel,
                                   Mitarbeiter ersteller) {}
}
