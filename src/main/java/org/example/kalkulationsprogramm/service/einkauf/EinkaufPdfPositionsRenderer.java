package org.example.kalkulationsprogramm.service.einkauf;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.example.kalkulationsprogramm.domain.einkauf.Dokumentart;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPdfDto.Beleg;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPdfDto.PdfHerkunft;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPdfDto.PdfKosten;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPdfDto.PdfPosition;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.DokumentSoll;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;
import org.example.kalkulationsprogramm.repository.SchnittbilderRepository;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Component
public class EinkaufPdfPositionsRenderer {
    private static final Color ROSE = new Color(190, 24, 93);
    private static final Color LIGHT = new Color(255, 241, 242);
    private static final int MAX_TEXT_PER_POSITION_ROW = 1200;
    private static final byte[] NO_IMAGE = new byte[0];
    private final SchnittbilderRepository schnittbilderRepository;
    private final DateiSpeicherService dateiSpeicherService;
    private final Map<String, byte[]> cutImageCache = new ConcurrentHashMap<>();

    public EinkaufPdfPositionsRenderer(SchnittbilderRepository schnittbilderRepository,
            DateiSpeicherService dateiSpeicherService) {
        this.schnittbilderRepository = schnittbilderRepository;
        this.dateiSpeicherService = dateiSpeicherService;
    }

    public byte[] render(Beleg beleg, Image logo) {
        if (beleg == null || beleg.typ() == null || beleg.nummer() == null || beleg.nummer().isBlank()) {
            throw new IllegalArgumentException("Der Beleg für das PDF ist unvollständig.");
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 38, 38, 34, 38);
            PdfWriter.getInstance(document, output);
            document.open();
            addLogo(document, logo);
            addHeading(document, beleg);
            addRecipient(document, beleg);
            addDates(document, beleg);
            addPositions(document, beleg);
            addDeliveryGroups(document, beleg.liefergruppen());
            addCosts(document, beleg);
            addConditions(document, beleg);
            document.close();
            return output.toByteArray();
        } catch (DocumentException | IOException exception) {
            throw new IllegalStateException("Das Einkaufs-PDF konnte nicht erstellt werden.", exception);
        }
    }

    public byte[] renderEntnahmeliste(List<EntnahmeZeile> zeilen, Image logo) {
        return renderArbeitsblatt(zeilen, logo, false);
    }

    public byte[] renderBedarfsliste(List<EntnahmeZeile> zeilen, Image logo) {
        return renderArbeitsblatt(zeilen, logo, true);
    }

    private byte[] renderArbeitsblatt(List<EntnahmeZeile> zeilen, Image logo, boolean werkstatt) {
        if (zeilen == null || zeilen.isEmpty()) {
            throw new IllegalArgumentException("Bitte wählen Sie mindestens einen Bedarf für die Entnahmeliste aus.");
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 38, 38, 34, 38);
            PdfWriter.getInstance(document, output);
            document.open();
            addLogo(document, logo);
            Paragraph title = new Paragraph(werkstatt ? "Bedarfsliste – Werkstattprüfung" : "Lagerentnahme – Arbeitsblatt", font(Font.BOLD, 16, ROSE));
            title.setSpacingAfter(8);
            document.add(title);
            document.add(new Paragraph(werkstatt ? "Bitte vorhandene Mengen prüfen und fehlende Mengen eintragen. Die Rückmeldung anschließend in der Anwendung speichern." : "Ausdruck bestätigt keine Entnahme. Bitte Istmenge prüfen und Entnahme anschließend in der Anwendung bestätigen.", font(Font.NORMAL, 9, Color.DARK_GRAY)));
            document.add(new Paragraph(" "));
            PdfPTable table = new PdfPTable(new float[] { 1.4f, 3.5f, 1.4f, 1.1f, 1f, 1.5f });
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (String label : List.of("Bedarf", "Material", "Benötigt", "Einheit", werkstatt ? "Vorhanden" : "Erledigt", werkstatt ? "Zu bestellen" : "Istmenge")) {
                PdfPCell cell = cell(label, Font.BOLD, Color.WHITE, ROSE);
                table.addCell(cell);
            }
            for (EntnahmeZeile zeile : zeilen) {
                PositionSnapshot p = zeile.position();
                String material = p == null ? zeile.bezeichnung() : text(p.bezeichnung());
                if (p != null) material += "\n" + join(p.interneReferenz(), p.werkstoff(), p.abmessung());
                if (werkstatt) material += "\n" + text(zeile.kontext()) + "\n" + technik(p, List.of())
                        + (p == null ? "" : "\n" + join(p.zeichnungsnummer(), p.zeichnungsrevision(), p.schnittForm()));
                if (werkstatt && p != null && p.basis() != null) {
                    var basis = p.basis();
                    material += "\n" + profilmenge(basis.stueckzahl() == null ? 0 : basis.stueckzahl().intValue(),
                            basis.einzelLaengeMm(), basis.menge(), zeile.einheit());
                }
                table.addCell(cell(String.valueOf(zeile.bedarfId()), Font.NORMAL, Color.BLACK, Color.WHITE));
                table.addCell(cell(material, Font.NORMAL, Color.BLACK, Color.WHITE));
                table.addCell(cell(format(zeile.menge()), Font.NORMAL, Color.BLACK, Color.WHITE));
                table.addCell(cell(zeile.einheit(), Font.NORMAL, Color.BLACK, Color.WHITE));
                table.addCell(cell(werkstatt ? "________" : "[ ]", Font.NORMAL, Color.BLACK, Color.WHITE));
                table.addCell(cell("________________", Font.NORMAL, Color.BLACK, Color.WHITE));
            }
            document.add(table);
            document.close();
            return output.toByteArray();
        } catch (DocumentException | IOException exception) {
            throw new IllegalStateException("Die Entnahmeliste konnte nicht erstellt werden.", exception);
        }
    }

    public record EntnahmeZeile(Long bedarfId, String bezeichnung, PositionSnapshot position,
            BigDecimal menge, String einheit, String kontext) {
        public EntnahmeZeile(Long bedarfId, String bezeichnung, PositionSnapshot position, BigDecimal menge, String einheit) {
            this(bedarfId, bezeichnung, position, menge, einheit, null);
        }
    }

    public static PdfPCell gemeinsameZelle(String text, Font font, Color hintergrund) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setBackgroundColor(hintergrund);
        cell.setPadding(4);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        return cell;
    }

    public static PdfPCell schnittZelle(String form, Font font, Color hintergrund, byte[] iconBytes) {
        if (form == null || form.isBlank()) return gemeinsameZelle("", font, hintergrund);
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(hintergrund);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(2);
        try {
            if (iconBytes != null && iconBytes.length > 0) {
                Image icon = Image.getInstance(iconBytes);
                icon.scaleToFit(26, 26);
                cell.addElement(icon);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Das Schnittbild konnte nicht ins PDF übernommen werden.", exception);
        }
        cell.addElement(new Paragraph("Form " + form, FontFactory.getFont(FontFactory.HELVETICA, 6)));
        return cell;
    }

    public static String profilmenge(int stueckzahl, BigDecimal laengeMm, BigDecimal menge, String einheit) {
        String unit = einheit == null ? "" : switch (einheit.toUpperCase(Locale.ROOT)) {
            case "STUECK" -> "Stk";
            case "METER" -> "m";
            case "KILOGRAMM" -> "kg";
            case "QUADRATMETER" -> "m²";
            default -> einheit;
        };
        String quantity = format(menge) + (unit.isBlank() ? "" : " " + unit);
        if (laengeMm != null) {
            String length = (stueckzahl > 0 ? stueckzahl + " Stk à " : "Einzellänge: ") + format(laengeMm) + " mm";
            return length + (menge == null ? "" : " (gesamt: " + quantity + ")");
        }
        return quantity;
    }

    private void addPositions(Document document, Beleg beleg) throws DocumentException {
        boolean anfrage = beleg.typ().equalsIgnoreCase("ANFRAGE");
        PdfPTable table = new PdfPTable(anfrage
                ? new float[] { 1.05f, 2.7f, 1.05f, 3.8f }
                : new float[] { 1.05f, 2.7f, 1.05f, 3.8f, 1.6f });
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        table.setSplitLate(true);
        table.setSplitRows(true);
        for (String label : List.of("Pos.", "Material / interne Nr.", "Menge", "Technische Angaben", "Kosten")) {
            if (anfrage && label.equals("Kosten")) continue;
            table.addCell(cell(label, Font.BOLD, Color.WHITE, ROSE));
        }
        for (int positionIndex = 0; positionIndex < beleg.positionen().size(); positionIndex++) {
            PdfPosition position = beleg.positionen().get(positionIndex);
            PositionSnapshot p = position.technik();
            String material = p == null ? "" : join(p.bezeichnung(), "Interne Nr.: " + text(p.interneReferenz()),
                    p.zeichnungsnummer() == null ? null : "Zeichnung: " + p.zeichnungsnummer() + " " + text(p.zeichnungsrevision()));
            String menge = p == null || p.basis() == null ? "" : profilmenge(
                    p.basis().stueckzahl() == null ? 0 : p.basis().stueckzahl().intValue(),
                    p.basis().einzelLaengeMm(), p.basis().menge(), p.basis().einheit() == null ? null : p.basis().einheit().name());
            List<String> materialRows = textRows(material);
            List<String> technicalRows = textRows(technik(p, position.herkuenfte()));
            List<String> costRows = anfrage ? List.of("") : textRows(position.nettoSumme() == null && position.kosten().isEmpty() ? "Preis offen"
                    : position.kosten().isEmpty() ? geld(position.nettoSumme()) + " EUR" : kosten(position.kosten()));
            int rowCount = Math.max(materialRows.size(), Math.max(technicalRows.size(), costRows.size()));
            Color background = positionIndex % 2 == 0 ? Color.WHITE : LIGHT;
            for (int row = 0; row < rowCount; row++) {
                table.addCell(cell(row == 0 ? text(position.positionsnummer())
                        : text(position.positionsnummer()) + " (Fortsetzung)", Font.NORMAL, Color.BLACK, background));
                table.addCell(cell(rowValue(materialRows, row), Font.NORMAL, Color.BLACK, background));
                table.addCell(row == 0 ? quantityCell(position, p, menge, background)
                        : cell("", Font.NORMAL, Color.BLACK, background));
                table.addCell(cell(rowValue(technicalRows, row), Font.NORMAL, Color.BLACK, background));
                if (!anfrage) table.addCell(cell(rowValue(costRows, row), Font.NORMAL, Color.BLACK, background));
            }
        }
        document.add(table);
    }

    private PdfPCell quantityCell(PdfPosition position, PositionSnapshot p, String menge, Color background) {
        PdfPCell quantity = new PdfPCell();
        quantity.setBackgroundColor(background);
        quantity.setPadding(4);
        quantity.addElement(new Paragraph(menge, font(Font.NORMAL, 8, Color.BLACK)));
        if (p != null && p.schnittForm() != null) {
            quantity.addElement(new Paragraph("Pos. " + text(position.positionsnummer()) + " · Form " + p.schnittForm(),
                    font(Font.NORMAL, 7, Color.DARK_GRAY)));
            Image icon = schnittbild(p.schnittForm());
            if (icon != null) {
                icon.scaleToFit(32, 32);
                quantity.addElement(icon);
            }
        }
        return quantity;
    }

    private List<String> textRows(String value) {
        if (value == null || value.isBlank()) return List.of("");
        List<String> rows = new java.util.ArrayList<>();
        int start = 0;
        while (start < value.length()) {
            int end = Math.min(value.length(), start + MAX_TEXT_PER_POSITION_ROW);
            if (end < value.length()) {
                int boundary = value.lastIndexOf(' ', end);
                if (boundary > start + MAX_TEXT_PER_POSITION_ROW / 2) end = boundary + 1;
            }
            rows.add(value.substring(start, end));
            start = end;
        }
        return rows;
    }

    private String rowValue(List<String> rows, int index) {
        return index < rows.size() ? rows.get(index) : "";
    }

    private void addHeading(Document document, Beleg b) throws DocumentException {
        String titleText = switch (b.typ().toUpperCase(Locale.ROOT)) {
            case "ANFRAGE" -> "Anfrage";
            case "BESTELLUNG" -> "Bestellung";
            default -> "Einkaufsbeleg";
        };
        Paragraph title = new Paragraph(titleText + "  " + b.nummer() + " · Revision " + b.revision(), font(Font.BOLD, 16, ROSE));
        title.setSpacingAfter(8);
        document.add(title);
        if (b.entwurf()) document.add(new Paragraph("ENTWURF", font(Font.BOLD, 11, ROSE)));
    }

    private void addRecipient(Document document, Beleg b) throws DocumentException {
        if (b.empfaenger() == null) return;
        document.add(new Paragraph(text(b.empfaenger().lieferantenname()), font(Font.BOLD, 11, Color.BLACK)));
        if (b.empfaenger().name() != null) document.add(new Paragraph(text(b.empfaenger().name()), font(Font.NORMAL, 9, Color.BLACK)));
        if (b.empfaenger().email() != null) document.add(new Paragraph(text(b.empfaenger().email()), font(Font.NORMAL, 9, Color.BLACK)));
        document.add(new Paragraph(" "));
    }

    private void addDates(Document document, Beleg b) throws DocumentException {
        if (b.antwortfrist() != null) document.add(new Paragraph("Antwort erbeten bis: " + b.antwortfrist(), font(Font.NORMAL, 9, Color.BLACK)));
        if (b.liefertermin() != null) document.add(new Paragraph("Gewünschter Liefertermin: " + b.liefertermin(), font(Font.NORMAL, 9, Color.BLACK)));
    }

    private void addDeliveryGroups(Document document, List<org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Liefergruppe> groups) throws DocumentException {
        if (groups == null || groups.isEmpty()) return;
        document.add(new Paragraph("Lieferung", font(Font.BOLD, 11, ROSE)));
        for (int i = 0; i < groups.size(); i++) {
            var group = groups.get(i);
            document.add(new Paragraph("Gruppe " + (i + 1) + ": " + join(group.lieferadresse(),
                    group.bedarfstermin() == null ? null : "Bedarfstermin " + group.bedarfstermin(), group.lagerzweck()), font(Font.NORMAL, 9, Color.BLACK)));
        }
    }

    private void addCosts(Document document, Beleg b) throws DocumentException {
        if (b.typ().equalsIgnoreCase("ANFRAGE")) return;
        String cost = kosten(b.kopfkosten());
        if (!cost.isBlank()) document.add(new Paragraph("Zusätzliche Kosten: " + cost, font(Font.NORMAL, 9, Color.BLACK)));
        if (b.nettoSumme() != null) document.add(new Paragraph("Nettosumme: " + geld(b.nettoSumme()) + " EUR", font(Font.BOLD, 11, Color.BLACK)));
    }

    private void addConditions(Document document, Beleg b) throws DocumentException {
        if (b.bedingungen() == null || b.bedingungen().isBlank()) return;
        Paragraph conditions = new Paragraph("Bedingungen\n" + b.bedingungen(), font(Font.NORMAL, 9, Color.BLACK));
        conditions.setSpacingBefore(10);
        document.add(conditions);
    }

    private void addLogo(Document document, Image logo) throws DocumentException {
        if (logo == null) return;
        logo.scaleToFit(150, 70);
        document.add(logo);
    }

    private PdfPCell cell(String text, int style, Color foreground, Color background) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font(style, 8, foreground)));
        cell.setBackgroundColor(background);
        cell.setPadding(4);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        return cell;
    }

    private String technik(PositionSnapshot p, List<PdfHerkunft> herkuenfte) {
        if (p == null) return "";
        return join("Werkstoff: " + text(p.werkstoff()), "Abmessung: " + text(p.abmessung()),
                p.winkelLinks() == null && p.winkelRechts() == null ? null : "links " + text(p.winkelLinks()) + " / rechts " + text(p.winkelRechts()),
                p.bearbeitung() == null ? null : "Bearbeitung: " + p.bearbeitung(),
                p.oberflaeche() == null ? null : "Oberfläche: " + p.oberflaeche(), dokumente(p.dokumente()), herkunft(herkuenfte));
    }

    private String herkunft(List<PdfHerkunft> herkuenfte) {
        if (herkuenfte == null || herkuenfte.isEmpty()) return null;
        return "Projektbedarf: " + herkuenfte.stream()
                .map(h -> text(h.projektNummer()) + " · " + format(h.menge()) + " " + text(h.einheit() == null ? null : h.einheit().name()))
                .reduce((a, b) -> a + "; " + b).orElse("");
    }

    private String dokumente(List<DokumentSoll> documents) {
        if (documents == null || documents.isEmpty()) return null;
        return "Dokumente: " + documents.stream().map(d -> join(
                d.art() == null ? "Dokument" : dokumentName(d.art()),
                d.grundlage() == null || d.grundlage().isBlank() ? null : "Grundlage " + d.grundlage(),
                d.grundlageVersion() == null || d.grundlageVersion().isBlank() ? null : "Version " + d.grundlageVersion(),
                d.fachlichBestaetigt() ? "fachlich bestätigt" : "fachlich nicht bestätigt"))
                .reduce((a, b) -> a + "; " + b).orElse("");
    }

    private String dokumentName(Dokumentart d) {
        return switch (d) {
            case ZEUGNIS_2_1 -> "Zeugnis 2.1";
            case ZEUGNIS_2_2 -> "Zeugnis 2.2";
            case ZEUGNIS_3_1 -> "Zeugnis 3.1";
            case ZEUGNIS_3_2 -> "Zeugnis 3.2";
            case LEISTUNGSERKLAERUNG -> "Leistungserklärung";
            case CE_NACHWEIS -> "CE-Nachweis";
        };
    }

    private String kosten(List<PdfKosten> costs) {
        if (costs == null) return "";
        return costs.stream().filter(PdfKosten::enthalten).map(c -> text(c.bezeichnung()) + ": " + geld(c.betrag()) + " EUR"
                        + (c.basis() == null || c.basis().isBlank() ? "" : " (" + c.basis()
                                + (c.basisMenge() == null ? "" : " · " + format(c.basisMenge())) + ")")
                        + (c.rechenweg() == null || c.rechenweg().isBlank() ? "" : "\n" + c.rechenweg()))
                .reduce((a, b) -> a + "\n" + b).orElse("");
    }

    private Image schnittbild(String form) {
        if (schnittbilderRepository == null || dateiSpeicherService == null) return null;
        byte[] cached = cutImageCache.get(form);
        if (cached != null) return cached == NO_IMAGE ? null : image(cached);
        try {
            var record = schnittbilderRepository.findByForm(form);
            if (record == null || record.getBildUrlSchnittbild() == null) {
                cutImageCache.put(form, NO_IMAGE);
                return null;
            }
            String url = record.getBildUrlSchnittbild().split("\\?", 2)[0];
            String name = url.substring(url.lastIndexOf('/') + 1);
            if (name.isBlank()) {
                cutImageCache.put(form, NO_IMAGE);
                return null;
            }
            try (var stream = dateiSpeicherService.ladeBildAlsResource(name).getInputStream()) {
                byte[] bytes = stream.readAllBytes();
                cutImageCache.put(form, bytes);
                return image(bytes);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Das Schnittbild für die Form " + form + " konnte nicht gelesen werden.", exception);
        }
    }

    private Image image(byte[] bytes) {
        try {
            return Image.getInstance(bytes);
        } catch (Exception exception) {
            throw new IllegalStateException("Das zwischengespeicherte Schnittbild ist ungültig.", exception);
        }
    }

    private static Font font(int style, float size, Color color) { return FontFactory.getFont(FontFactory.HELVETICA, size, style, color); }
    private static String text(String value) { return value == null ? "" : value; }
    private static String join(String... values) { return java.util.Arrays.stream(values).filter(v -> v != null && !v.isBlank()).reduce((a, b) -> a + "\n" + b).orElse(""); }
    private static String format(BigDecimal value) { return value == null ? "" : value.stripTrailingZeros().toPlainString().replace('.', ','); }
    private static String geld(BigDecimal value) { return value == null ? "" : value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ','); }
}
