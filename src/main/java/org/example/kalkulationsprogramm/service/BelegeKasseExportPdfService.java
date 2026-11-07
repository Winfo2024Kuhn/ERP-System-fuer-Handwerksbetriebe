package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.util.PdfWriterFabrik;
import org.openpdf.text.*;
import org.openpdf.text.Font;
import org.openpdf.text.Image;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.domain.KassenbuchMonatsabschluss;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.example.kalkulationsprogramm.repository.KassenbuchMonatsabschlussRepository;
import org.example.kalkulationsprogramm.repository.KassenzaehlungRepository;
import org.example.kalkulationsprogramm.service.kassenbuchpdf.KassenbuchBelegbilderPdf;
import org.example.kalkulationsprogramm.service.kassenbuchpdf.KassenbuchJournalPdf;
import org.example.kalkulationsprogramm.service.pdf.PdfDesign;
import org.example.kalkulationsprogramm.service.pdf.PdfFirmenDesign;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.example.kalkulationsprogramm.service.kassenbuchpdf.KassenbuchTexte.joinNonEmpty;
import static org.example.kalkulationsprogramm.service.kassenbuchpdf.KassenbuchTexte.kurz;
import static org.example.kalkulationsprogramm.service.kassenbuchpdf.KassenbuchTexte.nullSafe;
import static org.example.kalkulationsprogramm.service.kassenbuchpdf.KassenbuchTexte.nullToEmpty;

/**
 * Erzeugt das Monats-Kassenbuch als PDF -- den Papierteil des klassischen
 * Wanderordners, den der Steuerberater bekommt.
 *
 * <p>Aufbau:</p>
 * <ol>
 *   <li>Briefkopf mit Logo und Firmenstammdaten.</li>
 *   <li>Kopfzeile mit Zeitraum, Erstellungszeitpunkt, Ersteller und dem
 *       Festschreibungsstand des Monats.</li>
 *   <li>Das Kassenbuch als fortlaufendes Journal: eine Zeile pro Bewegung
 *       mit laufender Nummer, Gegenkonto, Zahlungsart, Steuersatz,
 *       Steuerbetrag und dem Kassenbestand <em>nach</em> jeder Buchung.</li>
 *   <li>Kassenstuerze des Monats, falls gezaehlt wurde.</li>
 *   <li>Die Belegbilder selbst, ein Beleg pro Seite, jeweils mit Nummer und
 *       Fingerabdruck der Datei ueberschrieben.</li>
 * </ol>
 *
 * <p>Frueher stand hier ein T-Konto mit Soll- und Habenspalte. Das sieht
 * zwar nach Buchhaltung aus, kann aber prinzipbedingt keinen laufenden
 * Bestand pro Zeile zeigen -- und genau den braucht man, um an einem
 * beliebigen Tag zu sehen, was in der Kasse liegen musste. Die
 * Soll-/Haben-Summen stehen jetzt kompakt im Summenblock darunter.</p>
 *
 * <p>Dieser Service setzt das PDF nur zusammen: Journal, Summenblock und
 * Kassenstürze baut {@link KassenbuchJournalPdf}, den Belegbilder-Anhang
 * {@link KassenbuchBelegbilderPdf}. Logo und Firmenfarbe kommen aus
 * {@link PdfFirmenDesign}; die Statusfarben (abgeschlossen grün, vorläufig rot)
 * bleiben fest ({@link PdfDesign#ERFOLG}, {@link PdfDesign#WARNUNG}).</p>
 */
@Service
@RequiredArgsConstructor
public class BelegeKasseExportPdfService {

    private final BelegRepository belegRepository;
    private final FirmeninformationRepository firmeninformationRepository;
    private final KassenbuchMonatsabschlussRepository abschlussRepository;
    private final KassenzaehlungRepository zaehlungRepository;
    private final BelegAuditChainVerifier verifier;
    private final PdfFirmenDesign pdfFirmenDesign;

    @Value("${upload.path:uploads}")
    private String uploadPath;

    private static final Color TEXT_DARK   = PdfDesign.TEXT_DUNKEL;
    private static final Color TEXT_MUTED  = PdfDesign.TEXT_GEDAEMPFT;
    private static final Color TEXT_CELL   = PdfDesign.TEXT_ZELLE;
    private static final Color FOOTER_GREY = new Color(148, 163, 184); // slate-400

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /**
     * @param jahr             vierstellig (z.B. 2026)
     * @param monat            1..12
     * @param ersteller        wer den Export ausgeloest hat; erscheint im Kopf
     * @param mitBelegbildern  true = die Belegfotos werden hinten angehaengt
     */
    public Path generatePdf(int jahr, int monat, Mitarbeiter ersteller, boolean mitBelegbildern) {
        YearMonth ym = YearMonth.of(jahr, monat);
        LocalDate von = ym.atDay(1);
        LocalDate bis = ym.atEndOfMonth();

        List<Beleg> imMonat = belegRepository.findGeprueftImZeitraumNachNummer(von, bis);

        try {
            // Temp-PDF unter dem konfigurierten upload.path ablegen — sonst
            // driftet die Temp-Location, wenn das Upload-Verzeichnis (z.B. in
            // application-local.properties) umgebogen wurde.
            Path dir = Path.of(uploadPath);
            Files.createDirectories(dir);
            Path temp = Files.createTempFile(dir, "belege-export-", ".pdf");
            Document doc = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
            PdfWriterFabrik.writer(doc, Files.newOutputStream(temp));
            doc.open();

            PdfDesign design = pdfFirmenDesign.laden();
            Firmeninformation firma = firmeninformationRepository.findFirmeninformation().orElse(null);
            addBriefkopf(doc, firma, design.logo());
            addTitle(doc, ym, ersteller, design);

            // Anfangsbestand: Stand am Vortag des Monatsanfangs. Der
            // Steuerberater erwartet eine durchgehende Fortschreibung; ohne
            // diesen Wert begaenne das Kassenbuch irrtuemlich bei 0,00 EUR
            // und passte nicht zum Vormonats-PDF.
            BigDecimal anfangsbestand = berechneAnfangsbestand(von);
            KassenbuchJournalPdf journal = new KassenbuchJournalPdf(design);
            journal.journal(doc, imMonat, anfangsbestand);
            journal.kassenstuerze(doc, zaehlungRepository.findByStichtagBetweenOrderByStichtagAscIdAsc(von, bis));
            addFooter(doc);

            if (mitBelegbildern) {
                new KassenbuchBelegbilderPdf(uploadPath).anhaengen(doc, imMonat);
            }

            doc.close();
            return temp;

        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Erzeugen des Belege-Monatsexports", e);
        }
    }

    // ===================== Abschnitte =====================

    /**
     * Briefkopf mit Firmenlogo (aus dem Firmendesign) links und Firmenstammdaten
     * rechts. Der Steuerberater sieht damit auf einen Blick, von welcher Firma der
     * Export stammt.
     *
     * Fallbacks:
     *  - Kein Logo hinterlegt: nur Firmen-Text rechts (bewusst kein Software-Logo als Ersatz).
     *  - Keine Firmeninformation in der DB: nur das Logo.
     *  - Beides fehlt: stillschweigend ueberspringen — PDF bleibt valide.
     */
    private void addBriefkopf(Document doc, Firmeninformation firma, Image firmenlogo) throws DocumentException {
        Image logo = firmenlogo == null ? null : Image.getInstance(firmenlogo);
        if (logo == null && firma == null) {
            return; // nichts zu zeigen, Title-Section folgt direkt
        }
        PdfPTable kopf = new PdfPTable(new float[]{ 2f, 5f });
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
                    prefix("Tel. ",     firma.getTelefon()),
                    prefix("",          firma.getEmail()),
                    prefix("",          firma.getWebsite()));
            addRightLine(infoCell, kontakt, lineMuted);
            String steuer = joinNonEmpty(" · ",
                    prefix("St.-Nr. ",  firma.getSteuernummer()),
                    prefix("USt-IdNr. ", firma.getUstIdNr()));
            addRightLine(infoCell, steuer, lineMuted);
        }
        kopf.addCell(infoCell);

        doc.add(kopf);
    }

    private void addRightLine(PdfPCell cell, String text, Font font) {
        if (text == null || text.isBlank()) return;
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_RIGHT);
        cell.addElement(p);
    }

    private String prefix(String prefix, String value) {
        if (value == null || value.isBlank()) return null;
        return prefix + value.trim();
    }

    /**
     * Titelblock. Enthaelt bewusst auch Erstellungszeitpunkt und Ersteller:
     * ein Ausdruck ohne beides ist fuer einen Pruefer wertlos, weil sich
     * nicht sagen laesst, welchen Datenstand er zeigt.
     */
    private void addTitle(Document doc, YearMonth ym, Mitarbeiter ersteller, PdfDesign design)
            throws DocumentException {
        Font titleFont    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, TEXT_DARK);
        Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA, 10, TEXT_MUTED);
        Font kategorie    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, design.akzent());

        doc.add(new Paragraph("BUCHHALTUNG", kategorie));
        doc.add(new Paragraph("KASSENBUCH – " + monatLabel(ym).toUpperCase(Locale.GERMAN), titleFont));

        String erstellerName = ersteller != null
                ? ((nullToEmpty(ersteller.getVorname()) + " " + nullToEmpty(ersteller.getNachname())).trim())
                : "";
        String zeile = "Erstellt am " + LocalDateTime.now().format(TS_FMT) + " Uhr"
                + (erstellerName.isEmpty() ? "" : " von " + erstellerName);
        doc.add(new Paragraph(zeile, subTitleFont));

        // Festschreibungsstand: der wichtigste Satz auf dem ganzen Blatt.
        // Ein nicht abgeschlossener Monat ist noch aenderbar, und das muss
        // auf dem Ausdruck stehen, damit ihn niemand fuer endgueltig haelt.
        Optional<KassenbuchMonatsabschluss> abschluss =
                abschlussRepository.findByJahrAndMonat(ym.getYear(), ym.getMonthValue());
        Font statusFont;
        String statusText;
        if (abschluss.isPresent()) {
            KassenbuchMonatsabschluss m = abschluss.get();
            statusFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, PdfDesign.ERFOLG);
            statusText = "Monat abgeschlossen am " + m.getAbgeschlossenAm().format(TS_FMT)
                    + " · " + m.getAnzahlBelege() + " Belege festgeschrieben"
                    + (m.getErsteLaufendeNummer() != null
                        ? " · Nummern " + m.getErsteLaufendeNummer() + " bis " + m.getLetzteLaufendeNummer()
                        : "")
                    + (m.getEntryHash() != null ? " · Prüfsumme " + kurz(m.getEntryHash()) : "");
        } else {
            // Warnzustand, keine Hausfarbe: bleibt immer rot, auch bei anderer Firmenfarbe.
            statusFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, PdfDesign.WARNUNG);
            statusText = "VORLÄUFIG – dieser Monat ist noch nicht abgeschlossen. "
                    + "Die Buchungen können sich noch ändern und haben teilweise noch keine feste Nummer.";
        }
        Paragraph status = new Paragraph(statusText, statusFont);
        status.setSpacingBefore(4f);
        doc.add(status);
        doc.add(new Paragraph(" "));
    }

    /**
     * Kassenbestand am Vortag des Stichtags: alle geprueften Bar-Bewegungen
     * davor, Eingaenge addiert, Ausgaenge abgezogen.
     */
    private BigDecimal berechneAnfangsbestand(LocalDate monatsAnfang) {
        List<Beleg> davor = belegRepository.findGeprueftImZeitraumNachNummer(
                LocalDate.of(1900, 1, 1), monatsAnfang.minusDays(1));
        BigDecimal saldo = BigDecimal.ZERO;
        for (Beleg b : davor) {
            BelegKategorie k = b.getBelegKategorie();
            if (k == null || !k.istKassenBewegung()) continue;
            BigDecimal brutto = nullSafe(b.getBetragBrutto());
            saldo = k.istAusgang() ? saldo.subtract(brutto) : saldo.add(brutto);
        }
        return saldo;
    }

    /**
     * Fusszeile mit dem Zustand des Protokolls. Ein Pruefer kann damit
     * ohne Systemzugriff erkennen, ob die Aufzeichnungen seit ihrer
     * Entstehung unveraendert sind.
     */
    private void addFooter(Document doc) throws DocumentException {
        BelegAuditChainVerifier.Bericht bericht = verifier.verify();
        Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, FOOTER_GREY);

        String kette = bericht.isIntakt()
                ? "Das Änderungsprotokoll ist unversehrt (" + bericht.getGesamtAnzahl()
                  + " Einträge geprüft"
                  + (bericht.getLetzterEntryHash() != null
                        ? ", Prüfsumme " + kurz(bericht.getLetzterEntryHash()) : "") + ")."
                : "ACHTUNG: Das Änderungsprotokoll weist eine Lücke oder Veränderung auf.";

        Paragraph footer = new Paragraph(
                "Maschinell erstellt aus dem geführten Kassenbuch. Enthalten sind ausschließlich geprüfte Belege. "
                + kette + " Jede Änderung an einer Buchung ist im Protokoll festgehalten; "
                + "festgeschriebene Buchungen werden nicht überschrieben, sondern storniert und neu gebucht.",
                footerFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(16f);
        doc.add(footer);
    }

    private String monatLabel(YearMonth ym) {
        String[] monate = { "Januar", "Februar", "März", "April", "Mai", "Juni",
                "Juli", "August", "September", "Oktober", "November", "Dezember" };
        return monate[ym.getMonthValue() - 1] + " " + ym.getYear();
    }
}
