package org.example.kalkulationsprogramm.service;

import com.lowagie.text.*;
import com.lowagie.text.BadElementException;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.IOException;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.domain.KassenbuchMonatsabschluss;
import org.example.kalkulationsprogramm.domain.Kassenzaehlung;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.example.kalkulationsprogramm.repository.KassenbuchMonatsabschlussRepository;
import org.example.kalkulationsprogramm.repository.KassenzaehlungRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BelegeKasseExportPdfService {

    private final BelegRepository belegRepository;
    private final FirmeninformationRepository firmeninformationRepository;
    private final KassenbuchMonatsabschlussRepository abschlussRepository;
    private final KassenzaehlungRepository zaehlungRepository;
    private final BelegAuditChainVerifier verifier;

    @Value("${upload.path:uploads}")
    private String uploadPath;

    private static final Color HEADER_BG   = new Color(220, 38, 38);   // rose-600
    private static final Color ROW_ALT     = new Color(254, 242, 242); // rose-50
    private static final Color BORDER      = new Color(229, 231, 235); // slate-200
    private static final Color SUM_BG      = new Color(241, 245, 249); // slate-100
    private static final Color TEXT_DARK   = new Color(30, 41, 59);    // slate-800
    private static final Color TEXT_MUTED  = new Color(100, 116, 139); // slate-500
    private static final Color TEXT_CELL   = new Color(55, 65, 81);    // slate-700
    private static final Color FOOTER_GREY = new Color(148, 163, 184); // slate-400
    private static final Color KPI_ACCENT  = new Color(220, 38, 38);
    private static final Color STORNO_BG   = new Color(248, 250, 252); // slate-50

    private static final DateTimeFormatter DATE_FMT   = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_SHORT = DateTimeFormatter.ofPattern("dd.MM.yy");
    private static final DateTimeFormatter TS_FMT     = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /** Bildformate, die sich direkt ins PDF einbetten lassen. PDF-Belege werden nur referenziert. */
    private static final Set<String> EINBETTBARE_MIME_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png");

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
            Path dir = Paths.get(uploadPath);
            Files.createDirectories(dir);
            Path temp = Files.createTempFile(dir, "belege-export-", ".pdf");
            Document doc = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
            PdfWriter.getInstance(doc, Files.newOutputStream(temp));
            doc.open();

            Firmeninformation firma = firmeninformationRepository.findFirmeninformation().orElse(null);
            addBriefkopf(doc, firma);
            addTitle(doc, ym, ersteller);

            // Anfangsbestand: Stand am Vortag des Monatsanfangs. Der
            // Steuerberater erwartet eine durchgehende Fortschreibung; ohne
            // diesen Wert begaenne das Kassenbuch irrtuemlich bei 0,00 EUR
            // und passte nicht zum Vormonats-PDF.
            BigDecimal anfangsbestand = berechneAnfangsbestand(von);
            addKassenbuchJournal(doc, imMonat, anfangsbestand);
            addKassenstuerze(doc, von, bis);
            addFooter(doc);

            if (mitBelegbildern) {
                addBelegbilder(doc, imMonat);
            }

            doc.close();
            return temp;

        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Erzeugen des Belege-Monatsexports", e);
        }
    }

    // ===================== Sections =====================

    /**
     * Briefkopf mit Firmenlogo (aus {@code uploads/firma/logo/<logoDateiname>})
     * links und Firmenstammdaten rechts. Der Steuerberater sieht damit auf
     * einen Blick, von welcher Firma der Export stammt.
     *
     * Fallbacks:
     *  - Logo nicht gepflegt oder Datei fehlt: nur Firmen-Text rechts.
     *  - Keine Firmeninformation in der DB: nur das (eventuelle) Static-Logo.
     *  - Beides fehlt: stillschweigend ueberspringen — PDF bleibt valide.
     */
    private void addBriefkopf(Document doc, Firmeninformation firma) throws DocumentException {
        Image logo = ladeFirmenlogo(firma);
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

    private Image ladeFirmenlogo(Firmeninformation firma) {
        String dateiname = firma != null ? firma.getLogoDateiname() : null;
        if (dateiname != null && !dateiname.isBlank()) {
            // Pfad-Traversal blocken — Defense-in-Depth, der Upload-Pfad sollte
            // den Dateinamen ohnehin sanitisieren, aber wir prüfen hier nochmal.
            String safe = dateiname.trim();
            if (!safe.contains("..") && !safe.contains("/") && !safe.contains("\\")) {
                Path base = Paths.get(uploadPath, "firma", "logo").toAbsolutePath().normalize();
                Path logoPath = base.resolve(safe).normalize();
                if (logoPath.startsWith(base) && Files.exists(logoPath)) {
                    try {
                        return Image.getInstance(logoPath.toString());
                    } catch (IOException | BadElementException ex) {
                        // Datei kaputt / kein gueltiges Bild — Fallback unten greift
                    }
                }
            }
        }
        try {
            java.net.URL url = getClass().getResource("/static/firmenlogo_icon.png");
            if (url != null) return Image.getInstance(url);
        } catch (IOException | BadElementException ignored) {
            // Kein Logo verfuegbar — PDF wird ohne Logo gerendert.
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

    /**
     * Titelblock. Enthaelt bewusst auch Erstellungszeitpunkt und Ersteller:
     * ein Ausdruck ohne beides ist fuer einen Pruefer wertlos, weil sich
     * nicht sagen laesst, welchen Datenstand er zeigt.
     */
    private void addTitle(Document doc, YearMonth ym, Mitarbeiter ersteller) throws DocumentException {
        Font titleFont    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, TEXT_DARK);
        Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA, 10, TEXT_MUTED);
        Font kategorie    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, KPI_ACCENT);

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
            statusFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(21, 128, 61));
            statusText = "Monat abgeschlossen am " + m.getAbgeschlossenAm().format(TS_FMT)
                    + " · " + m.getAnzahlBelege() + " Belege festgeschrieben"
                    + (m.getErsteLaufendeNummer() != null
                        ? " · Nummern " + m.getErsteLaufendeNummer() + " bis " + m.getLetzteLaufendeNummer()
                        : "")
                    + (m.getEntryHash() != null ? " · Prüfsumme " + kurz(m.getEntryHash()) : "");
        } else {
            statusFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, KPI_ACCENT);
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
     * Das eigentliche Kassenbuch: eine Zeile pro Bewegung, chronologisch,
     * mit dem Bestand nach jeder Buchung.
     */
    private void addKassenbuchJournal(Document doc, List<Beleg> belege, BigDecimal anfangsbestand)
            throws DocumentException {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, TEXT_DARK);
        Paragraph section = new Paragraph("Kasse · Bargeldbewegungen", sectionFont);
        section.setSpacingBefore(6f);
        section.setSpacingAfter(6f);
        doc.add(section);

        List<Beleg> kasse = belege.stream()
                .filter(b -> b.getBelegKategorie() != null && b.getBelegKategorie().istKassenBewegung())
                .sorted(Comparator
                        .comparing(Beleg::getBelegDatum, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(b -> b.getLaufendeNummer() == null ? Long.MAX_VALUE : b.getLaufendeNummer())
                        .thenComparing(Beleg::getId))
                .toList();

        //           Nr   Datum Beleg-Nr Zweck Gegenkonto Zahlart MwSt% MwSt€ Ein   Aus   Bestand
        PdfPTable t = new PdfPTable(new float[]{
                0.7f, 1.0f, 1.2f, 4.2f, 1.9f, 1.2f, 0.8f, 1.1f, 1.2f, 1.2f, 1.3f });
        t.setWidthPercentage(100);
        t.setHeaderRows(1);

        String[] heads = { "Nr.", "Datum", "Beleg-Nr.", "Verwendungszweck", "Gegenkonto",
                "Zahlungsart", "MwSt", "MwSt €", "Einnahme €", "Ausgabe €", "Bestand €" };
        for (String h : heads) {
            t.addCell(headerCell(h));
        }

        // Erste Zeile: der Uebertrag aus dem Vormonat.
        PdfPCell uebertrag = new PdfPCell(new Phrase("Übertrag aus dem Vormonat",
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, TEXT_MUTED)));
        uebertrag.setColspan(10);
        uebertrag.setBackgroundColor(SUM_BG);
        uebertrag.setHorizontalAlignment(Element.ALIGN_RIGHT);
        setzePadding(uebertrag);
        uebertrag.setBorder(Rectangle.BOTTOM);
        uebertrag.setBorderColor(BORDER);
        t.addCell(uebertrag);
        t.addCell(betragZelle(formatEuro(anfangsbestand), SUM_BG, true));

        BigDecimal bestand = anfangsbestand;
        BigDecimal sumEin = BigDecimal.ZERO;
        BigDecimal sumAus = BigDecimal.ZERO;

        if (kasse.isEmpty()) {
            PdfPCell leer = new PdfPCell(new Phrase("Keine Bargeldbewegungen in diesem Monat",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, TEXT_MUTED)));
            leer.setColspan(11);
            leer.setHorizontalAlignment(Element.ALIGN_CENTER);
            leer.setPaddingTop(12f);
            leer.setPaddingBottom(12f);
            leer.setBorder(Rectangle.NO_BORDER);
            t.addCell(leer);
        }

        boolean alt = false;
        for (Beleg b : kasse) {
            BelegKategorie k = b.getBelegKategorie();
            BigDecimal brutto = nullSafe(b.getBetragBrutto());
            boolean ausgang = k.istAusgang();
            if (ausgang) {
                bestand = bestand.subtract(brutto);
                sumAus = sumAus.add(brutto);
            } else {
                bestand = bestand.add(brutto);
                sumEin = sumEin.add(brutto);
            }

            // Stornierte Zeilen und Gegenbuchungen faerben wir grau ein --
            // sie bleiben stehen (das verlangt die Aufbewahrung), sollen aber
            // beim Ueberfliegen nicht mit gueltigen Buchungen verwechselt werden.
            boolean stornoBezug = b.getStornoFuerBelegId() != null || b.getStorniertDurchBelegId() != null;
            Color bg = stornoBezug ? STORNO_BG : (alt ? ROW_ALT : Color.WHITE);

            t.addCell(zelle(b.getLaufendeNummer() != null ? b.getLaufendeNummer().toString() : "–",
                    bg, Element.ALIGN_RIGHT));
            t.addCell(zelle(b.getBelegDatum() != null ? b.getBelegDatum().format(DATE_SHORT) : "–",
                    bg, Element.ALIGN_LEFT));
            t.addCell(zelle(kuerze(b.getBelegNummer(), 14), bg, Element.ALIGN_LEFT));
            t.addCell(zelle(verwendungszweck(b), bg, Element.ALIGN_LEFT));
            t.addCell(zelle(gegenkonto(b), bg, Element.ALIGN_LEFT));
            t.addCell(zelle(kuerze(b.getZahlungsart(), 12), bg, Element.ALIGN_LEFT));
            t.addCell(zelle(b.getMwstSatz() != null
                    ? formatProzent(b.getMwstSatz()) : "–", bg, Element.ALIGN_RIGHT));
            t.addCell(zelle(mwstBetrag(b) != null ? formatEuro(mwstBetrag(b)) : "–", bg, Element.ALIGN_RIGHT));
            t.addCell(zelle(ausgang ? "" : formatEuro(brutto), bg, Element.ALIGN_RIGHT));
            t.addCell(zelle(ausgang ? formatEuro(brutto) : "", bg, Element.ALIGN_RIGHT));
            t.addCell(betragZelle(formatEuro(bestand), bg, false));
            alt = !alt;
        }

        doc.add(t);
        addSummenblock(doc, anfangsbestand, sumEin, sumAus, bestand);
        addNichtBarHinweis(doc, belege);
    }

    /** Anfangsbestand, Summen und Endbestand -- ersetzt das frueher gezeigte T-Konto. */
    private void addSummenblock(Document doc, BigDecimal anfang, BigDecimal ein,
                                BigDecimal aus, BigDecimal ende) throws DocumentException {
        PdfPTable s = new PdfPTable(new float[]{ 3f, 1.4f });
        s.setWidthPercentage(45);
        s.setHorizontalAlignment(Element.ALIGN_RIGHT);
        s.setSpacingBefore(10f);

        s.addCell(summeLabel("Bestand am Monatsanfang"));
        s.addCell(summeWert(formatEuro(anfang) + " €", false));
        s.addCell(summeLabel("+ Einnahmen und Einlagen"));
        s.addCell(summeWert(formatEuro(ein) + " €", false));
        s.addCell(summeLabel("− Ausgaben und Entnahmen"));
        s.addCell(summeWert(formatEuro(aus) + " €", false));
        s.addCell(summeLabel("= Bestand am Monatsende"));
        s.addCell(summeWert(formatEuro(ende) + " €", true));

        doc.add(s);
    }

    /**
     * Belege, die nicht bar bezahlt wurden (Bank, Kreditkarte, Sonstiges),
     * gehoeren nicht ins Kassenbuch, liegen aber mit im Ordner. Ein Hinweis
     * erspart dem Steuerberater die Rueckfrage, warum die Belegnummern im
     * Journal Luecken haben.
     */
    private void addNichtBarHinweis(Document doc, List<Beleg> alle) throws DocumentException {
        List<Beleg> nichtBar = alle.stream()
                .filter(b -> b.getBelegKategorie() == null || !b.getBelegKategorie().istKassenBewegung())
                .toList();
        if (nichtBar.isEmpty()) return;

        BigDecimal summe = BigDecimal.ZERO;
        for (Beleg b : nichtBar) summe = summe.add(nullSafe(b.getBetragBrutto()));

        Font f = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_MUTED);
        Paragraph p = new Paragraph(
                "Hinweis: " + nichtBar.size() + " Belege dieses Monats über zusammen "
                + formatEuro(summe) + " € wurden nicht bar bezahlt (Bank, Kreditkarte, Sonstiges). "
                + "Sie stehen deshalb nicht im Kassenbuch, liegen aber mit im Belegteil dieses Pakets. "
                + "Daher können im Journal Nummern fehlen.", f);
        p.setSpacingBefore(10f);
        doc.add(p);
    }

    /** Kassenstuerze des Monats: gezaehltes Bargeld gegen den rechnerischen Bestand. */
    private void addKassenstuerze(Document doc, LocalDate von, LocalDate bis) throws DocumentException {
        List<Kassenzaehlung> zaehlungen =
                zaehlungRepository.findByStichtagBetweenOrderByStichtagAscIdAsc(von, bis);
        if (zaehlungen.isEmpty()) return;

        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, TEXT_DARK);
        Paragraph section = new Paragraph("Kassenstürze in diesem Monat", sectionFont);
        section.setSpacingBefore(16f);
        section.setSpacingAfter(6f);
        doc.add(section);

        PdfPTable t = new PdfPTable(new float[]{ 1.3f, 1.6f, 1.6f, 1.4f, 5f });
        t.setWidthPercentage(100);
        for (String h : new String[]{ "Stichtag", "Gezählt €", "Laut Kassenbuch €", "Differenz €", "Bemerkung" }) {
            t.addCell(headerCell(h));
        }
        boolean alt = false;
        for (Kassenzaehlung z : zaehlungen) {
            Color bg = alt ? ROW_ALT : Color.WHITE;
            t.addCell(zelle(z.getStichtag().format(DATE_FMT), bg, Element.ALIGN_LEFT));
            t.addCell(zelle(formatEuro(z.getGezaehlterBestand()), bg, Element.ALIGN_RIGHT));
            t.addCell(zelle(formatEuro(z.getRechnerischerBestand()), bg, Element.ALIGN_RIGHT));
            t.addCell(zelle(formatEuro(z.getDifferenz()), bg, Element.ALIGN_RIGHT));
            String bem = z.getBemerkung() != null ? z.getBemerkung() : "";
            if (z.getAusgleichBelegId() != null) {
                bem = (bem.isBlank() ? "" : bem + " · ") + "Differenz wurde ausgebucht";
            }
            t.addCell(zelle(bem, bg, Element.ALIGN_LEFT));
            alt = !alt;
        }
        doc.add(t);
    }

    /**
     * Die Belegbilder, ein Beleg pro Seite. Bisher behauptete das PDF nur,
     * die Fotos laegen "im selben Ordner" -- damit war der Ausdruck allein
     * wertlos, sobald der Ordner auseinanderfiel.
     *
     * <p>Ueber jedem Bild steht die laufende Nummer, das Datum, der Betrag
     * und der Fingerabdruck der Datei. Damit laesst sich jedes Foto genau
     * einer Zeile im Journal zuordnen und pruefen, dass es nicht
     * ausgetauscht wurde.</p>
     *
     * <p>PDF-Belege lassen sich nicht als Bild einbetten; fuer sie wird nur
     * eine Referenzseite erzeugt. Die Datei selbst liegt im ZIP-Paket.</p>
     */
    private void addBelegbilder(Document doc, List<Beleg> belege) throws DocumentException {
        List<Beleg> mitDatei = belege.stream()
                .filter(b -> b.getGespeicherterDateiname() != null && !b.getGespeicherterDateiname().isBlank())
                .toList();
        if (mitDatei.isEmpty()) return;

        Font ueberschrift = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, TEXT_DARK);
        Font zeile        = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_CELL);
        Font hashFont     = FontFactory.getFont(FontFactory.COURIER, 7, TEXT_MUTED);

        doc.newPage();
        Paragraph titel = new Paragraph("Belege zu diesem Kassenbuch", ueberschrift);
        titel.setSpacingAfter(6f);
        doc.add(titel);
        doc.add(new Paragraph(
                mitDatei.size() + " Belege, ein Beleg je Seite. Die Zeichenfolge unter jedem Beleg ist "
                + "der Fingerabdruck der Bilddatei – stimmt er, wurde das Bild seit der Prüfung nicht "
                + "ausgetauscht.", zeile));

        for (Beleg b : mitDatei) {
            doc.newPage();

            String kopf = "Beleg Nr. " + (b.getLaufendeNummer() != null ? b.getLaufendeNummer() : "ohne Nummer")
                    + "  ·  " + (b.getBelegDatum() != null ? b.getBelegDatum().format(DATE_FMT) : "ohne Datum")
                    + "  ·  " + formatEuro(nullSafe(b.getBetragBrutto())) + " €"
                    + "  ·  " + kategorieLabel(b.getBelegKategorie())
                    + (b.getLieferant() != null ? "  ·  " + b.getLieferant().getLieferantenname() : "");
            Paragraph kopfP = new Paragraph(kopf,
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, TEXT_DARK));
            doc.add(kopfP);

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
                Font hinweis = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, TEXT_MUTED);
                doc.add(new Paragraph(" ", hinweis));
                doc.add(new Paragraph(
                        istPdfBeleg(b)
                            ? "Dieser Beleg liegt als PDF vor und ist im Ordner \"belege\" des Export-Pakets "
                              + "unter dem oben genannten Dateinamen enthalten."
                            : "Das Belegbild konnte nicht eingebunden werden. Die Datei liegt im Ordner "
                              + "\"belege\" des Export-Pakets.", hinweis));
            }
        }
    }

    private boolean istPdfBeleg(Beleg b) {
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

        Path base = Paths.get(uploadPath, "belege").toAbsolutePath().normalize();
        Path datei = base.resolve(name).normalize();
        if (!datei.startsWith(base) || !Files.exists(datei)) return null;
        try {
            return Image.getInstance(datei.toString());
        } catch (IOException | BadElementException e) {
            log.warn("Belegbild {} konnte nicht ins PDF eingebettet werden: {}", name, e.getMessage());
            return null;
        }
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

    // ===================== Zellen & Helfer =====================

    private PdfPCell headerCell(String text) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBackgroundColor(HEADER_BG);
        c.setHorizontalAlignment(Element.ALIGN_LEFT);
        setzePadding(c);
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private PdfPCell zelle(String text, Color bg, int alignment) {
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8, TEXT_CELL);
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, cellFont));
        c.setBackgroundColor(bg);
        c.setHorizontalAlignment(alignment);
        setzePadding(c);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(BORDER);
        c.setBorderWidth(0.5f);
        return c;
    }

    /** Bestand-Spalte: fett, damit man die Fortschreibung mit dem Auge verfolgen kann. */
    private PdfPCell betragZelle(String text, Color bg, boolean hervorheben) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8,
                hervorheben ? TEXT_DARK : TEXT_CELL);
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBackgroundColor(bg);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        setzePadding(c);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(BORDER);
        c.setBorderWidth(0.5f);
        return c;
    }

    private PdfPCell summeLabel(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text,
                FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_CELL)));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingTop(3f);
        c.setPaddingBottom(3f);
        return c;
    }

    private PdfPCell summeWert(String text, boolean hervorheben) {
        PdfPCell c = new PdfPCell(new Phrase(text, FontFactory.getFont(
                hervorheben ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA,
                hervorheben ? 11 : 9,
                hervorheben ? KPI_ACCENT : TEXT_CELL)));
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c.setBorder(hervorheben ? Rectangle.TOP : Rectangle.NO_BORDER);
        c.setBorderColor(TEXT_DARK);
        c.setPaddingTop(3f);
        c.setPaddingBottom(3f);
        return c;
    }

    private void setzePadding(PdfPCell c) {
        c.setPaddingTop(5f);
        c.setPaddingBottom(5f);
        c.setPaddingLeft(5f);
        c.setPaddingRight(5f);
    }

    private String verwendungszweck(Beleg b) {
        if (b.getBeschreibung() != null && !b.getBeschreibung().isBlank()) {
            return b.getBeschreibung().trim();
        }
        if (b.getLieferant() != null && b.getLieferant().getLieferantenname() != null) {
            return b.getLieferant().getLieferantenname();
        }
        return kategorieLabel(b.getBelegKategorie());
    }

    /**
     * Gegenkonto in der Form "4930 Bürobedarf". Ohne Kontierung steht dort
     * ein deutlicher Platzhalter -- der Steuerberater sieht dann sofort,
     * wo er noch nacharbeiten muss.
     */
    private String gegenkonto(Beleg b) {
        if (b.getSachkonto() == null) return "noch offen";
        String nummer = b.getSachkonto().getNummer();
        String bez = b.getSachkonto().getBezeichnung();
        return joinNonEmpty(" ", nummer, kuerze(bez, 18));
    }

    /**
     * Steuerbetrag der Buchung. Bevorzugt aus der Differenz brutto minus
     * netto -- das ist der Wert, den der Buchhalter tatsaechlich geprueft
     * hat. Fehlt der Nettobetrag, wird aus dem Steuersatz herausgerechnet.
     */
    private BigDecimal mwstBetrag(Beleg b) {
        BigDecimal brutto = b.getBetragBrutto();
        if (brutto == null) return null;
        BigDecimal netto = b.getBetragNetto();
        if (netto != null) {
            return brutto.subtract(netto).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal satz = b.getMwstSatz();
        if (satz == null || satz.signum() <= 0) return null;
        BigDecimal faktor = BigDecimal.ONE.add(satz.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        BigDecimal errechnetesNetto = brutto.divide(faktor, 2, RoundingMode.HALF_UP);
        return brutto.subtract(errechnetesNetto).setScale(2, RoundingMode.HALF_UP);
    }

    private String kuerze(String s, int max) {
        if (s == null || s.isBlank()) return "–";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    /** Erste und letzte Stellen eines Hashes -- lang genug zum Wiedererkennen, kurz genug fuers Auge. */
    private String kurz(String hash) {
        if (hash == null || hash.length() < 16) return nullToEmpty(hash);
        return hash.substring(0, 8) + "…" + hash.substring(hash.length() - 8);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private BigDecimal nullSafe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String formatEuro(BigDecimal v) {
        BigDecimal x = v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
        // Deutsche Formatierung: 1.234,56
        return String.format(Locale.GERMAN, "%,.2f", x);
    }

    private String formatProzent(BigDecimal satz) {
        return satz.stripTrailingZeros().toPlainString().replace('.', ',') + " %";
    }

    private String kategorieLabel(BelegKategorie k) {
        if (k == null) return "–";
        return switch (k) {
            case UNZUGEORDNET    -> "Unzugeordnet";
            case KASSE_EINNAHME  -> "Kasse · Einnahme";
            case KASSE_AUSGABE   -> "Kasse · Ausgabe";
            case PRIVATENTNAHME  -> "Privatentnahme";
            case PRIVATEINLAGE   -> "Privateinlage";
            case BANK            -> "Bank";
            case KREDITKARTE     -> "Kreditkarte";
            case SONSTIGER_BELEG -> "Sonstiger Beleg";
        };
    }

    private String monatLabel(YearMonth ym) {
        String[] monate = { "Januar", "Februar", "März", "April", "Mai", "Juni",
                "Juli", "August", "September", "Oktober", "November", "Dezember" };
        return monate[ym.getMonthValue() - 1] + " " + ym.getYear();
    }
}
