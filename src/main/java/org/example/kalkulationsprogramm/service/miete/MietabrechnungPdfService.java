package org.example.kalkulationsprogramm.service.miete;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.miete.*;
import org.example.kalkulationsprogramm.service.miete.model.AnnualAccountingResult;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MietabrechnungPdfService {

    // ── Farbpalette ──────────────────────────────────────────────────────────
    private static final java.awt.Color PRIMARY      = new java.awt.Color(0xDC, 0x26, 0x26); // Rose-600
    private static final java.awt.Color PRIMARY_DARK = new java.awt.Color(0xB9, 0x1C, 0x1C); // Rose-800
    private static final java.awt.Color HEADER_BG    = new java.awt.Color(0xFE, 0xF2, 0xF2); // Rose-50
    private static final java.awt.Color ROW_ALT      = new java.awt.Color(0xFB, 0xFB, 0xFB); // Gray-50
    private static final java.awt.Color BORDER_LIGHT = new java.awt.Color(0xE5, 0xE7, 0xEB); // Gray-200
    private static final java.awt.Color TEXT_PRIMARY  = new java.awt.Color(0x1F, 0x29, 0x37); // Slate-800
    private static final java.awt.Color TEXT_MUTED    = new java.awt.Color(0x64, 0x74, 0x8B); // Slate-500
    private static final java.awt.Color WHITE         = java.awt.Color.WHITE;
    private static final java.awt.Color POSITIVE      = new java.awt.Color(0x16, 0xA3, 0x4A); // Green-600
    private static final java.awt.Color NEGATIVE      = new java.awt.Color(0xDC, 0x26, 0x26); // Red-600
    private static final java.awt.Color CARD_BG       = new java.awt.Color(0xF8, 0xFA, 0xFC); // Slate-50

    // ── Schriften ────────────────────────────────────────────────────────────
    private static final Font TITLE_FONT       = new Font(Font.HELVETICA, 22, Font.BOLD, PRIMARY_DARK);
    private static final Font SUBTITLE_FONT    = new Font(Font.HELVETICA, 11, Font.NORMAL, TEXT_MUTED);
    private static final Font SECTION_FONT     = new Font(Font.HELVETICA, 13, Font.BOLD, TEXT_PRIMARY);
    private static final Font SUBSECTION_FONT  = new Font(Font.HELVETICA, 11, Font.BOLD, TEXT_PRIMARY);
    private static final Font TABLE_HEADER     = new Font(Font.HELVETICA, 9, Font.BOLD, PRIMARY_DARK);
    private static final Font TEXT_FONT        = new Font(Font.HELVETICA, 9, Font.NORMAL, TEXT_PRIMARY);
    private static final Font TEXT_SMALL        = new Font(Font.HELVETICA, 8, Font.NORMAL, TEXT_MUTED);
    private static final Font TEXT_BOLD         = new Font(Font.HELVETICA, 9, Font.BOLD, TEXT_PRIMARY);
    private static final Font LABEL_FONT       = new Font(Font.HELVETICA, 8, Font.NORMAL, TEXT_MUTED);
    private static final Font VALUE_FONT       = new Font(Font.HELVETICA, 14, Font.BOLD, TEXT_PRIMARY);
    private static final Font VALUE_SMALL_FONT = new Font(Font.HELVETICA, 11, Font.BOLD, TEXT_PRIMARY);
    private static final Font FOOTER_FONT      = new Font(Font.HELVETICA, 8, Font.ITALIC, TEXT_MUTED);

    private static final float CELL_PADDING = 6f;

    private final MietabrechnungService mietabrechnungService;

    // ── Oeffentliche API ─────────────────────────────────────────────────────

    public byte[] generatePdf(Long mietobjektId, int jahr) {
        AnnualAccountingResult result = mietabrechnungService.berechneJahresabrechnung(mietobjektId, jahr);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40f, 40f, 50f, 50f);
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new FooterPageEvent(result));
            document.open();

            addHeader(document, result);
            addSummaryCards(document, result);
            addKostenstellenOverview(document, result);
            addKostenstellenDetails(document, result);
            addParteiAbrechnung(document, result);

            document.close();
            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("PDF-Erstellung fehlgeschlagen", e);
        }
    }

    // ── Header ───────────────────────────────────────────────────────────────

    private void addHeader(Document document, AnnualAccountingResult result) throws DocumentException {
        // Farbiger Headerbalken
        PdfPTable headerBar = new PdfPTable(1);
        headerBar.setWidthPercentage(100);
        PdfPCell barCell = new PdfPCell();
        barCell.setBackgroundColor(PRIMARY);
        barCell.setFixedHeight(4f);
        barCell.setBorder(Rectangle.NO_BORDER);
        headerBar.addCell(barCell);
        document.add(headerBar);
        addSpace(document, 12f);

        // Titel + Adresse in einer Zeile
        PdfPTable headerTable = new PdfPTable(new float[]{3f, 2f});
        headerTable.setWidthPercentage(100);

        // Links: Titel
        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setPaddingLeft(0);
        Paragraph title = new Paragraph("Jahresabrechnung " + result.getAbrechnungsJahr(), TITLE_FONT);
        titleCell.addElement(title);
        Paragraph objName = new Paragraph(safeStr(result.getMietobjektName()), SUBTITLE_FONT);
        titleCell.addElement(objName);
        headerTable.addCell(titleCell);

        // Rechts: Adresse
        PdfPCell addrCell = new PdfPCell();
        addrCell.setBorder(Rectangle.NO_BORDER);
        addrCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        addrCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        StringBuilder addr = new StringBuilder();
        if (hasText(result.getMietobjektStrasse())) {
            addr.append(result.getMietobjektStrasse().trim());
        }
        if (hasText(result.getMietobjektPlz()) || hasText(result.getMietobjektOrt())) {
            if (addr.length() > 0) addr.append("\n");
            if (hasText(result.getMietobjektPlz())) addr.append(result.getMietobjektPlz().trim());
            if (hasText(result.getMietobjektOrt())) {
                if (hasText(result.getMietobjektPlz())) addr.append(" ");
                addr.append(result.getMietobjektOrt().trim());
            }
        }
        Paragraph addrPara = new Paragraph(addr.toString(), SUBTITLE_FONT);
        addrPara.setAlignment(Element.ALIGN_RIGHT);
        addrCell.addElement(addrPara);
        headerTable.addCell(addrCell);

        document.add(headerTable);
        addDivider(document);
    }

    // ── Zusammenfassungs-Karten ──────────────────────────────────────────────

    private void addSummaryCards(Document document, AnnualAccountingResult result) throws DocumentException {
        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.GERMANY);

        PdfPTable cards = new PdfPTable(3);
        cards.setWidthPercentage(100);
        cards.setSpacingBefore(4f);

        BigDecimal gesamt = safe(result.getGesamtkosten());
        BigDecimal vorjahr = safe(result.getGesamtkostenVorjahr());
        BigDecimal diff = safe(result.getGesamtkostenDifferenz());

        addSummaryCard(cards, "Gesamtkosten " + result.getAbrechnungsJahr(),
                currency.format(gesamt), null);
        addSummaryCard(cards, "Vorjahr " + (result.getAbrechnungsJahr() - 1),
                currency.format(vorjahr), null);

        String diffStr = formatSignedCurrency(diff, currency);
        java.awt.Color diffColor = diff.compareTo(BigDecimal.ZERO) > 0 ? NEGATIVE : POSITIVE;
        addSummaryCard(cards, "Veraenderung", diffStr, diffColor);

        document.add(cards);
        addSpace(document, 10f);
    }

    private void addSummaryCard(PdfPTable table, String label, String value, java.awt.Color valueColor) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(BORDER_LIGHT);
        cell.setBorderWidth(1f);
        cell.setBackgroundColor(CARD_BG);
        cell.setPadding(12f);
        cell.setPaddingBottom(10f);

        Paragraph lbl = new Paragraph(label, LABEL_FONT);
        cell.addElement(lbl);

        Font valFont = valueColor != null
                ? new Font(Font.HELVETICA, 14, Font.BOLD, valueColor)
                : VALUE_FONT;
        Paragraph val = new Paragraph(value, valFont);
        cell.addElement(val);

        table.addCell(cell);
    }

    // ── Kostenstellen-Uebersicht ─────────────────────────────────────────────

    private void addKostenstellenOverview(Document document, AnnualAccountingResult result)
            throws DocumentException {
        addSectionHeader(document, "Kostenuebersicht");

        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.GERMANY);

        PdfPTable table = new PdfPTable(new float[]{4f, 2f, 2f, 3f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(4f);

        addStyledHeaderCell(table, "Kostenstelle", Element.ALIGN_LEFT);
        addStyledHeaderCell(table, "Betrag", Element.ALIGN_RIGHT);
        addStyledHeaderCell(table, "Vorjahr", Element.ALIGN_RIGHT);
        addStyledHeaderCell(table, "Verteilungsschluessel", Element.ALIGN_LEFT);

        int row = 0;
        BigDecimal totalAktuell = BigDecimal.ZERO;
        BigDecimal totalVorjahr = BigDecimal.ZERO;

        for (AnnualAccountingResult.KostenstellenResult ks : result.getKostenstellen()) {
            BigDecimal summe = safe(ks.getGesamtkosten());
            BigDecimal summeVj = safe(ks.getGesamtkostenVorjahr());
            totalAktuell = totalAktuell.add(summe);
            totalVorjahr = totalVorjahr.add(summeVj);
            java.awt.Color bg = row % 2 == 1 ? ROW_ALT : WHITE;

            addBodyCell(table, ks.getKostenstelle().getName(), TEXT_BOLD, Element.ALIGN_LEFT, bg);
            addBodyCell(table, currency.format(summe), TEXT_FONT, Element.ALIGN_RIGHT, bg);
            addBodyCell(table, currency.format(summeVj), TEXT_SMALL, Element.ALIGN_RIGHT, bg);
            addBodyCell(table, formatStandardSchluessel(ks.getKostenstelle()), TEXT_SMALL, Element.ALIGN_LEFT, bg);
            row++;
        }

        // Summenzeile
        addFooterCell(table, "Gesamt", Element.ALIGN_LEFT);
        addFooterCell(table, currency.format(totalAktuell), Element.ALIGN_RIGHT);
        addFooterCell(table, currency.format(totalVorjahr), Element.ALIGN_RIGHT);
        addFooterCell(table, "", Element.ALIGN_LEFT);

        document.add(table);
        addSpace(document, 12f);
    }

    // ── Kostenstellen-Detailseiten ───────────────────────────────────────────

    private void addKostenstellenDetails(Document document, AnnualAccountingResult result)
            throws DocumentException {
        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.GERMANY);
        DecimalFormat numberFormat = (DecimalFormat) NumberFormat.getNumberInstance(Locale.GERMANY);
        numberFormat.setMinimumFractionDigits(0);
        numberFormat.setMaximumFractionDigits(2);
        DecimalFormat faktorFormat = (DecimalFormat) NumberFormat.getNumberInstance(Locale.GERMANY);
        faktorFormat.setMinimumFractionDigits(0);
        faktorFormat.setMaximumFractionDigits(5);

        Map<Long, AnnualAccountingResult.Verbrauchsvergleich> verbrauchMap = new LinkedHashMap<>();
        result.getVerbrauchsvergleiche().forEach(v -> {
            if (v.getVerbrauchsgegenstand() != null && v.getVerbrauchsgegenstand().getId() != null) {
                verbrauchMap.put(v.getVerbrauchsgegenstand().getId(), v);
            }
        });

        for (AnnualAccountingResult.KostenstellenResult ks : result.getKostenstellen()) {
            addDivider(document);

            // Kostenstelle-Header mit Betrag rechts
            PdfPTable ksHeader = new PdfPTable(new float[]{1f, 1f});
            ksHeader.setWidthPercentage(100);
            ksHeader.setSpacingBefore(4f);

            PdfPCell nameCell = new PdfPCell();
            nameCell.setBorder(Rectangle.NO_BORDER);
            Paragraph ksTitle = new Paragraph(ks.getKostenstelle().getName(), SECTION_FONT);
            nameCell.addElement(ksTitle);
            ksHeader.addCell(nameCell);

            PdfPCell amountCell = new PdfPCell();
            amountCell.setBorder(Rectangle.NO_BORDER);
            amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            BigDecimal ksSumme = safe(ks.getGesamtkosten());
            Paragraph ksAmount = new Paragraph(currency.format(ksSumme), VALUE_SMALL_FONT);
            ksAmount.setAlignment(Element.ALIGN_RIGHT);
            amountCell.addElement(ksAmount);
            ksHeader.addCell(amountCell);

            document.add(ksHeader);
            addSpace(document, 4f);

            // Positionen
            for (Kostenposition position : ks.getPositionen()) {
                addKostenpositionDetail(document, position, verbrauchMap,
                        currency, numberFormat, faktorFormat);
            }

            // Verteilung auf Parteien
            if (ks.getParteianteile() != null && !ks.getParteianteile().isEmpty()) {
                addSpace(document, 4f);
                Paragraph parteiLabel = new Paragraph("Verteilung auf Parteien", SUBSECTION_FONT);
                parteiLabel.setSpacingBefore(2f);
                document.add(parteiLabel);

                PdfPTable parteiTable = new PdfPTable(new float[]{4f, 2f});
                parteiTable.setWidthPercentage(60);
                parteiTable.setSpacingBefore(4f);

                addStyledHeaderCell(parteiTable, "Partei", Element.ALIGN_LEFT);
                addStyledHeaderCell(parteiTable, "Betrag", Element.ALIGN_RIGHT);

                int parteiRow = 0;
                for (AnnualAccountingResult.Parteianteil anteil : ks.getParteianteile()) {
                    java.awt.Color bg = parteiRow % 2 == 1 ? ROW_ALT : WHITE;
                    addBodyCell(parteiTable, anteil.getMietpartei().getName(), TEXT_FONT, Element.ALIGN_LEFT, bg);
                    addBodyCell(parteiTable, currency.format(safe(anteil.getBetrag())),
                            TEXT_BOLD, Element.ALIGN_RIGHT, bg);
                    parteiRow++;
                }
                document.add(parteiTable);
            }
            addSpace(document, 8f);
        }
    }

    private void addKostenpositionDetail(Document document, Kostenposition position,
            Map<Long, AnnualAccountingResult.Verbrauchsvergleich> verbrauchMap,
            NumberFormat currency, DecimalFormat numberFormat, DecimalFormat faktorFormat)
            throws DocumentException {

        String beschreibung = position.getBeschreibung();
        if (beschreibung == null || beschreibung.isBlank()) {
            beschreibung = position.getBelegNummer() != null
                    ? "Beleg " + position.getBelegNummer()
                    : "Kostenposition";
        }
        BigDecimal betrag = safe(position.getBetrag());

        Verteilungsschluessel schluessel = position.getVerteilungsschluesselOverride();
        if (schluessel == null) {
            schluessel = position.getKostenstelle() != null
                    ? position.getKostenstelle().getStandardSchluessel()
                    : null;
        }

        // Relevante Verbrauchsgegenstaende ermitteln
        Map<Long, AnnualAccountingResult.Verbrauchsvergleich> positionVerbrauch = new LinkedHashMap<>();
        boolean istVerbrauchsfaktor = position.getBerechnung() == KostenpositionBerechnung.VERBRAUCHSFAKTOR;
        if (istVerbrauchsfaktor && schluessel != null && schluessel.getEintraege() != null) {
            for (VerteilungsschluesselEintrag eintrag : schluessel.getEintraege()) {
                Verbrauchsgegenstand gegenstand = eintrag.getVerbrauchsgegenstand();
                if (gegenstand != null && gegenstand.getId() != null) {
                    AnnualAccountingResult.Verbrauchsvergleich vv = verbrauchMap.get(gegenstand.getId());
                    if (vv != null) positionVerbrauch.putIfAbsent(gegenstand.getId(), vv);
                }
            }
        } else if (istVerbrauchsfaktor) {
            positionVerbrauch.putAll(verbrauchMap);
        } else if (schluessel != null && schluessel.getEintraege() != null) {
            for (VerteilungsschluesselEintrag eintrag : schluessel.getEintraege()) {
                Verbrauchsgegenstand gegenstand = eintrag.getVerbrauchsgegenstand();
                if (gegenstand != null && gegenstand.getId() != null) {
                    AnnualAccountingResult.Verbrauchsvergleich vv = verbrauchMap.get(gegenstand.getId());
                    if (vv != null) positionVerbrauch.putIfAbsent(gegenstand.getId(), vv);
                }
            }
        }

        // ──── Positions-Box (2-spaltig: links Info, rechts Berechnung) ────
        PdfPTable posBox = new PdfPTable(new float[]{3f, 2f});
        posBox.setWidthPercentage(100);
        posBox.setSpacingBefore(4f);

        // --- Linke Spalte: Beschreibung + Schluessel ---
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorderColor(BORDER_LIGHT);
        leftCell.setBorderWidth(0.5f);
        leftCell.setPadding(8f);
        leftCell.setBackgroundColor(WHITE);

        // Position + Betrag
        Paragraph posHeader = new Paragraph();
        posHeader.add(new Chunk(beschreibung, TEXT_BOLD));
        posHeader.add(new Chunk("    ", TEXT_FONT));
        posHeader.add(new Chunk(currency.format(betrag),
                new Font(Font.HELVETICA, 10, Font.BOLD, PRIMARY)));
        leftCell.addElement(posHeader);

        // Verteilungsschluessel-Info
        if (schluessel != null) {
            addSpaceToCell(leftCell, 4f);
            Paragraph schluesselInfo = new Paragraph();
            schluesselInfo.add(new Chunk("Verteilung: ", LABEL_FONT));
            schluesselInfo.add(new Chunk(safeStr(schluessel.getName()) + " ("
                    + formatSchluesselTyp(schluessel.getTyp()) + ")", TEXT_SMALL));
            leftCell.addElement(schluesselInfo);

            // Anteile
            if (schluessel.getEintraege() != null && !schluessel.getEintraege().isEmpty()) {
                for (VerteilungsschluesselEintrag eintrag : schluessel.getEintraege()) {
                    String parteiName = eintrag.getMietpartei() != null
                            && hasText(eintrag.getMietpartei().getName())
                            ? eintrag.getMietpartei().getName().trim() : "-";
                    Paragraph anteilPara = new Paragraph();
                    anteilPara.add(new Chunk("  \u2022 " + parteiName + ": ", TEXT_SMALL));
                    if (schluessel.getTyp() == VerteilungsschluesselTyp.VERBRAUCH) {
                        Verbrauchsgegenstand gegenstand = eintrag.getVerbrauchsgegenstand();
                        if (gegenstand != null) {
                            AnnualAccountingResult.Verbrauchsvergleich vv = gegenstand.getId() != null
                                    ? positionVerbrauch.get(gegenstand.getId()) : null;
                            anteilPara.add(new Chunk(
                                    buildVerbrauchsgegenstandName(vv, gegenstand), TEXT_SMALL));
                        } else {
                            anteilPara.add(new Chunk("Zuordnung fehlt", TEXT_SMALL));
                        }
                    } else if (eintrag.getAnteil() != null) {
                        String formatted = numberFormat.format(eintrag.getAnteil());
                        if (schluessel.getTyp() == VerteilungsschluesselTyp.PROZENTUAL) {
                            formatted += " %";
                        }
                        anteilPara.add(new Chunk(formatted, TEXT_SMALL));
                    }
                    leftCell.addElement(anteilPara);
                }
            }
        }
        posBox.addCell(leftCell);

        // --- Rechte Spalte: Berechnung + Verbrauch ---
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorderColor(BORDER_LIGHT);
        rightCell.setBorderWidth(0.5f);
        rightCell.setPadding(8f);
        rightCell.setBackgroundColor(CARD_BG);

        if (istVerbrauchsfaktor && position.getVerbrauchsfaktor() != null) {
            Paragraph calcTitle = new Paragraph("Berechnung", LABEL_FONT);
            rightCell.addElement(calcTitle);
            addSpaceToCell(rightCell, 2f);

            BigDecimal faktor = position.getVerbrauchsfaktor();
            Paragraph faktorPara = new Paragraph();
            faktorPara.add(new Chunk("Faktor: ", TEXT_SMALL));
            faktorPara.add(new Chunk(faktorFormat.format(faktor), TEXT_BOLD));
            rightCell.addElement(faktorPara);

            // Verbrauchssummen
            BigDecimal verbrauchSumme = BigDecimal.ZERO;
            BigDecimal verbrauchVorjahrSumme = BigDecimal.ZERO;
            for (AnnualAccountingResult.Verbrauchsvergleich vv : positionVerbrauch.values()) {
                if (vv.getVerbrauchJahr() != null) {
                    verbrauchSumme = verbrauchSumme.add(vv.getVerbrauchJahr());
                }
                if (vv.getVerbrauchVorjahr() != null) {
                    verbrauchVorjahrSumme = verbrauchVorjahrSumme.add(vv.getVerbrauchVorjahr());
                }
            }
            BigDecimal berechnet = verbrauchSumme.multiply(faktor).setScale(2, RoundingMode.HALF_UP);
            BigDecimal verbrauchDiff = verbrauchSumme.subtract(verbrauchVorjahrSumme);

            Paragraph summePara = new Paragraph();
            summePara.add(new Chunk("Verbrauch: ", TEXT_SMALL));
            summePara.add(new Chunk(numberFormat.format(verbrauchSumme), TEXT_BOLD));
            rightCell.addElement(summePara);

            Paragraph vjPara = new Paragraph();
            vjPara.add(new Chunk("Vorjahr: ", TEXT_SMALL));
            vjPara.add(new Chunk(numberFormat.format(verbrauchVorjahrSumme), TEXT_SMALL));
            vjPara.add(new Chunk("  (", TEXT_SMALL));
            java.awt.Color diffC = verbrauchDiff.compareTo(BigDecimal.ZERO) > 0 ? NEGATIVE : POSITIVE;
            vjPara.add(new Chunk(formatSignedDecimal(verbrauchDiff, numberFormat),
                    new Font(Font.HELVETICA, 8, Font.BOLD, diffC)));
            vjPara.add(new Chunk(")", TEXT_SMALL));
            rightCell.addElement(vjPara);

            addSpaceToCell(rightCell, 4f);
            Paragraph ergebnis = new Paragraph();
            ergebnis.add(new Chunk("Ergebnis: ", TEXT_SMALL));
            ergebnis.add(new Chunk(currency.format(berechnet),
                    new Font(Font.HELVETICA, 10, Font.BOLD, PRIMARY)));
            rightCell.addElement(ergebnis);
        } else {
            Paragraph calcTitle = new Paragraph("Berechnung", LABEL_FONT);
            rightCell.addElement(calcTitle);
            addSpaceToCell(rightCell, 2f);
            rightCell.addElement(new Paragraph("Fester Betrag laut Erfassung", TEXT_SMALL));
        }
        posBox.addCell(rightCell);
        document.add(posBox);

        // ──── Verbrauchstabelle (falls Verbrauchsdaten vorhanden) ────
        if (!positionVerbrauch.isEmpty()) {
            PdfPTable vTable = new PdfPTable(new float[]{4f, 2f, 2f, 2f});
            vTable.setWidthPercentage(100);

            addSmallHeaderCell(vTable, "Zaehler", Element.ALIGN_LEFT);
            addSmallHeaderCell(vTable, "Aktuell", Element.ALIGN_RIGHT);
            addSmallHeaderCell(vTable, "Vorjahr", Element.ALIGN_RIGHT);
            addSmallHeaderCell(vTable, "Differenz", Element.ALIGN_RIGHT);

            int vRow = 0;
            for (AnnualAccountingResult.Verbrauchsvergleich vv : positionVerbrauch.values()) {
                java.awt.Color bg = vRow % 2 == 1 ? ROW_ALT : WHITE;
                Verbrauchsgegenstand gegenstand = vv.getVerbrauchsgegenstand();
                String name = buildVerbrauchsgegenstandName(vv, gegenstand);
                String einheit = gegenstand != null && hasText(gegenstand.getEinheit())
                        ? " " + gegenstand.getEinheit().trim() : "";

                addBodyCell(vTable, name, TEXT_SMALL, Element.ALIGN_LEFT, bg);
                addBodyCell(vTable, formatNumber(vv.getVerbrauchJahr(), numberFormat) + einheit,
                        TEXT_FONT, Element.ALIGN_RIGHT, bg);
                addBodyCell(vTable, formatNumber(vv.getVerbrauchVorjahr(), numberFormat) + einheit,
                        TEXT_SMALL, Element.ALIGN_RIGHT, bg);

                BigDecimal diff = vv.getDifferenz();
                String diffText = diff != null
                        ? formatSignedDecimal(diff, numberFormat) + einheit : "-";
                java.awt.Color diffColor = TEXT_MUTED;
                if (diff != null && diff.compareTo(BigDecimal.ZERO) > 0) {
                    diffColor = NEGATIVE;
                } else if (diff != null && diff.compareTo(BigDecimal.ZERO) < 0) {
                    diffColor = POSITIVE;
                }
                Font diffFont = new Font(Font.HELVETICA, 8, Font.BOLD, diffColor);
                addBodyCell(vTable, diffText, diffFont, Element.ALIGN_RIGHT, bg);
                vRow++;
            }
            document.add(vTable);
        }
    }

    // ── Partei-Endabrechnung ─────────────────────────────────────────────────

    private void addParteiAbrechnung(Document document, AnnualAccountingResult result) throws DocumentException {
        document.newPage();
        addSectionHeader(document, "Endabrechnung nach Parteien");

        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.GERMANY);

        for (AnnualAccountingResult.ParteiErgebnis p : result.getParteien()) {
            BigDecimal summe = safe(p.getBetrag());
            BigDecimal vorauszahlungJahr = safe(p.getVorauszahlungJahr());
            BigDecimal vorauszahlungMonat = safe(p.getVorauszahlungMonatlich());
            BigDecimal saldo = safe(p.getSaldo());
            String rolle = p.getMietpartei().getRolle() == MietparteiRolle.EIGENTUEMER
                    ? "Eigentuemer" : "Mieter";

            // Partei-Karte
            PdfPTable parteiCard = new PdfPTable(1);
            parteiCard.setWidthPercentage(100);
            parteiCard.setSpacingBefore(8f);

            PdfPCell cardCell = new PdfPCell();
            cardCell.setBorderColor(BORDER_LIGHT);
            cardCell.setBorderWidth(1f);
            cardCell.setPadding(12f);
            cardCell.setBackgroundColor(WHITE);

            // Name + Rolle
            Paragraph namePara = new Paragraph();
            namePara.add(new Chunk(p.getMietpartei().getName(), SECTION_FONT));
            namePara.add(new Chunk("  ", TEXT_FONT));

            Font rolleFont = new Font(Font.HELVETICA, 8, Font.BOLD,
                    p.getMietpartei().getRolle() == MietparteiRolle.EIGENTUEMER
                            ? TEXT_MUTED : PRIMARY);
            Chunk rolleBadge = new Chunk(" " + rolle + " ", rolleFont);
            namePara.add(rolleBadge);
            cardCell.addElement(namePara);
            addSpaceToCell(cardCell, 8f);

            // Detail-Tabelle innerhalb der Karte
            PdfPTable detailTable = new PdfPTable(new float[]{3f, 2f});
            detailTable.setWidthPercentage(100);

            addLabelValueRow(detailTable, "Nebenkosten " + result.getAbrechnungsJahr(),
                    currency.format(summe));

            if (vorauszahlungJahr.compareTo(BigDecimal.ZERO) > 0) {
                String vzText = currency.format(vorauszahlungJahr);
                if (vorauszahlungMonat.compareTo(BigDecimal.ZERO) > 0) {
                    vzText += "  (" + currency.format(vorauszahlungMonat) + "/Monat)";
                }
                addLabelValueRow(detailTable, "Geleistete Vorauszahlungen", "\u2013 " + vzText);
            }

            cardCell.addElement(detailTable);

            // Trennlinie
            addSpaceToCell(cardCell, 6f);
            PdfPTable divLine = new PdfPTable(1);
            divLine.setWidthPercentage(100);
            PdfPCell lineCell = new PdfPCell();
            lineCell.setBorder(Rectangle.NO_BORDER);
            lineCell.setBorderWidthTop(1f);
            lineCell.setBorderColorTop(BORDER_LIGHT);
            lineCell.setFixedHeight(1f);
            divLine.addCell(lineCell);
            cardCell.addElement(divLine);
            addSpaceToCell(cardCell, 6f);

            // Saldo (Ergebnis)
            boolean isNachzahlung = saldo.compareTo(BigDecimal.ZERO) > 0;
            String saldoLabel = isNachzahlung ? "Nachzahlung" : "Guthaben";
            java.awt.Color saldoColor = isNachzahlung ? NEGATIVE : POSITIVE;

            Paragraph saldoPara = new Paragraph();
            saldoPara.add(new Chunk(saldoLabel + ":  ", SUBSECTION_FONT));
            saldoPara.add(new Chunk(currency.format(saldo.abs()),
                    new Font(Font.HELVETICA, 14, Font.BOLD, saldoColor)));
            cardCell.addElement(saldoPara);

            parteiCard.addCell(cardCell);
            document.add(parteiCard);
        }
    }

    // ── Hilfs-Methoden: Zellen-Styling ───────────────────────────────────────

    private void addStyledHeaderCell(PdfPTable table, String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, TABLE_HEADER));
        cell.setBackgroundColor(HEADER_BG);
        cell.setBorderColor(BORDER_LIGHT);
        cell.setBorderWidth(0.5f);
        cell.setBorderWidthBottom(1.5f);
        cell.setBorderColorBottom(PRIMARY);
        cell.setHorizontalAlignment(align);
        cell.setPadding(CELL_PADDING);
        cell.setPaddingTop(CELL_PADDING + 1f);
        cell.setPaddingBottom(CELL_PADDING + 1f);
        table.addCell(cell);
    }

    private void addSmallHeaderCell(PdfPTable table, String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, TABLE_HEADER));
        cell.setBackgroundColor(HEADER_BG);
        cell.setBorderColor(BORDER_LIGHT);
        cell.setBorderWidth(0.5f);
        cell.setHorizontalAlignment(align);
        cell.setPadding(4f);
        table.addCell(cell);
    }

    private void addBodyCell(PdfPTable table, String text, Font font, int align, java.awt.Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setBorderColor(BORDER_LIGHT);
        cell.setBorderWidth(0.5f);
        cell.setHorizontalAlignment(align);
        cell.setPadding(CELL_PADDING);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private void addFooterCell(PdfPTable table, String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, TEXT_BOLD));
        cell.setBackgroundColor(HEADER_BG);
        cell.setBorderColor(BORDER_LIGHT);
        cell.setBorderWidth(0.5f);
        cell.setBorderWidthTop(1.5f);
        cell.setBorderColorTop(PRIMARY);
        cell.setHorizontalAlignment(align);
        cell.setPadding(CELL_PADDING);
        table.addCell(cell);
    }

    private void addLabelValueRow(PdfPTable table, String label, String value) {
        PdfPCell lbl = new PdfPCell(new Phrase(label, TEXT_FONT));
        lbl.setBorder(Rectangle.NO_BORDER);
        lbl.setPadding(3f);
        table.addCell(lbl);

        PdfPCell val = new PdfPCell(new Phrase(value, TEXT_BOLD));
        val.setBorder(Rectangle.NO_BORDER);
        val.setHorizontalAlignment(Element.ALIGN_RIGHT);
        val.setPadding(3f);
        table.addCell(val);
    }

    // ── Hilfs-Methoden: Layout ───────────────────────────────────────────────

    private void addSectionHeader(Document document, String text) throws DocumentException {
        Paragraph section = new Paragraph(text, SECTION_FONT);
        section.setSpacingBefore(8f);
        section.setSpacingAfter(2f);
        document.add(section);
    }

    private void addDivider(Document document) throws DocumentException {
        PdfPTable divider = new PdfPTable(1);
        divider.setWidthPercentage(100);
        divider.setSpacingBefore(8f);
        divider.setSpacingAfter(4f);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBorderWidthBottom(0.5f);
        cell.setBorderColorBottom(BORDER_LIGHT);
        cell.setFixedHeight(1f);
        divider.addCell(cell);
        document.add(divider);
    }

    private void addSpace(Document document, float height) throws DocumentException {
        Paragraph space = new Paragraph(" ");
        space.setSpacingBefore(height);
        document.add(space);
    }

    private void addSpaceToCell(PdfPCell cell, float height) {
        Paragraph space = new Paragraph(" ");
        space.setSpacingBefore(height);
        cell.addElement(space);
    }

    // ── Hilfs-Methoden: Formatierung ─────────────────────────────────────────

    private String formatStandardSchluessel(Kostenstelle kostenstelle) {
        if (kostenstelle == null) return "-";
        Verteilungsschluessel standard = kostenstelle.getStandardSchluessel();
        if (standard == null) return "Nicht hinterlegt";
        StringBuilder text = new StringBuilder();
        if (hasText(standard.getName())) {
            text.append(standard.getName().trim());
        } else if (standard.getId() != null) {
            text.append("Schluessel #").append(standard.getId());
        } else {
            text.append("Verteilungsschluessel");
        }
        text.append(" (").append(formatSchluesselTyp(standard.getTyp())).append(")");
        return text.toString();
    }

    private String formatSchluesselTyp(VerteilungsschluesselTyp typ) {
        if (typ == null) return "Unbekannt";
        return switch (typ) {
            case PROZENTUAL -> "Prozentual";
            case VERBRAUCH  -> "Verbrauch";
            case FLAECHE    -> "Flaeche";
        };
    }

    private String formatSignedCurrency(BigDecimal value, NumberFormat currency) {
        if (value == null) return currency.format(BigDecimal.ZERO);
        int cmp = value.compareTo(BigDecimal.ZERO);
        if (cmp > 0) return "+" + currency.format(value);
        if (cmp < 0) return "\u2013" + currency.format(value.abs());
        return currency.format(value);
    }

    private String formatSignedDecimal(BigDecimal value, DecimalFormat format) {
        if (value == null) return "0";
        int cmp = value.compareTo(BigDecimal.ZERO);
        String formatted = format.format(value.abs());
        if (cmp > 0) return "+" + formatted;
        if (cmp < 0) return "\u2013" + formatted;
        return "0";
    }

    private String formatNumber(BigDecimal value, DecimalFormat format) {
        return format.format(value != null ? value : BigDecimal.ZERO);
    }

    private String buildVerbrauchsgegenstandName(AnnualAccountingResult.Verbrauchsvergleich vv,
            Verbrauchsgegenstand gegenstand) {
        if (gegenstand == null) return "-";
        StringBuilder name = new StringBuilder();
        if (hasText(gegenstand.getName())) {
            name.append(gegenstand.getName().trim());
        } else if (gegenstand.getId() != null) {
            name.append("Gegenstand ").append(gegenstand.getId());
        }
        if (vv != null && hasText(vv.getRaumName())) {
            if (name.length() > 0) name.append(" \u2013 ");
            name.append(vv.getRaumName().trim());
        }
        return name.length() == 0 ? "-" : name.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safeStr(String value) {
        return hasText(value) ? value.trim() : "";
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    // ── Footer mit Seitenzahlen ──────────────────────────────────────────────

    private static class FooterPageEvent extends PdfPageEventHelper {
        private final AnnualAccountingResult result;

        FooterPageEvent(AnnualAccountingResult result) {
            this.result = result;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfPTable footer = new PdfPTable(new float[]{2f, 1f});
            footer.setTotalWidth(document.right() - document.left());

            String name = result.getMietobjektName() != null
                    && !result.getMietobjektName().trim().isEmpty()
                    ? result.getMietobjektName().trim() : "";

            PdfPCell left = new PdfPCell(new Phrase(
                    "Jahresabrechnung " + result.getAbrechnungsJahr()
                            + (name.isEmpty() ? "" : " \u2013 " + name),
                    FOOTER_FONT));
            left.setBorder(Rectangle.TOP);
            left.setBorderColorTop(BORDER_LIGHT);
            left.setBorderWidthTop(0.5f);
            left.setPaddingTop(6f);
            left.setHorizontalAlignment(Element.ALIGN_LEFT);
            footer.addCell(left);

            PdfPCell right = new PdfPCell(new Phrase(
                    "Seite " + writer.getPageNumber(), FOOTER_FONT));
            right.setBorder(Rectangle.TOP);
            right.setBorderColorTop(BORDER_LIGHT);
            right.setBorderWidthTop(0.5f);
            right.setPaddingTop(6f);
            right.setHorizontalAlignment(Element.ALIGN_RIGHT);
            footer.addCell(right);

            footer.writeSelectedRows(0, -1,
                    document.left(),
                    document.bottom() - 10f,
                    writer.getDirectContent());
        }
    }
}
