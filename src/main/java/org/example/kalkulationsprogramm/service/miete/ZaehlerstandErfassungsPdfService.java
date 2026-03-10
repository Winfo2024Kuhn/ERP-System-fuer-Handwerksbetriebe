package org.example.kalkulationsprogramm.service.miete;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.miete.Mietobjekt;
import org.example.kalkulationsprogramm.domain.miete.Raum;
import org.example.kalkulationsprogramm.domain.miete.Verbrauchsgegenstand;
import org.example.kalkulationsprogramm.domain.miete.Zaehlerstand;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.miete.MietobjektRepository;
import org.example.kalkulationsprogramm.repository.miete.RaumRepository;
import org.example.kalkulationsprogramm.repository.miete.VerbrauchsgegenstandRepository;
import org.example.kalkulationsprogramm.repository.miete.ZaehlerstandRepository;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ZaehlerstandErfassungsPdfService {

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 15, Font.BOLD);
    private static final Font SECTION_FONT = new Font(Font.HELVETICA, 12, Font.BOLD);
    private static final Font TEXT_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 10, Font.BOLD);

    private final MietobjektRepository mietobjektRepository;
    private final RaumRepository raumRepository;
    private final VerbrauchsgegenstandRepository verbrauchsgegenstandRepository;
    private final ZaehlerstandRepository zaehlerstandRepository;

    public byte[] generatePdf(Long mietobjektId, Integer zielJahr) {
        Mietobjekt mietobjekt = mietobjektRepository.findById(mietobjektId)
                .orElseThrow(() -> new NotFoundException("Mietobjekt " + mietobjektId + " nicht gefunden"));

        int jahr = zielJahr != null ? zielJahr : LocalDate.now().getYear();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36f, 36f, 40f, 40f);
            PdfWriter.getInstance(document, baos);
            document.open();

            addDocumentHeader(document, mietobjekt, jahr);

            NumberFormat decimalFormat = NumberFormat.getNumberInstance(Locale.GERMANY);
            if (decimalFormat instanceof DecimalFormat df) {
                df.setMaximumFractionDigits(4);
                df.setMinimumFractionDigits(0);
            }
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

            List<Raum> raeume = raumRepository.findByMietobjektOrderByNameAsc(mietobjekt);
            if (raeume.isEmpty()) {
                document.add(new Paragraph("Für dieses Mietobjekt sind keine Räume angelegt.", TEXT_FONT));
            } else {
                for (Raum raum : raeume) {
                    addRoomSection(document, raum, jahr, decimalFormat, dateFormatter);
                }
            }

            document.close();
            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("PDF-Erstellung für Zählerstands-Erfassung fehlgeschlagen", e);
        }
    }

    private void addDocumentHeader(Document document, Mietobjekt mietobjekt, int jahr) throws DocumentException {
        Paragraph title = new Paragraph("Ablesebogen Zählerstände", TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        document.add(new Paragraph("Objekt: " + mietobjekt.getName(), TEXT_FONT));

        StringBuilder address = new StringBuilder();
        if (hasText(mietobjekt.getStrasse())) {
            address.append(mietobjekt.getStrasse().trim());
        }
        if (hasText(mietobjekt.getPlz()) || hasText(mietobjekt.getOrt())) {
            if (address.length() > 0) {
                address.append(", ");
            }
            if (hasText(mietobjekt.getPlz())) {
                address.append(mietobjekt.getPlz().trim());
                address.append(' ');
            }
            if (hasText(mietobjekt.getOrt())) {
                address.append(mietobjekt.getOrt().trim());
            }
        }
        if (address.length() > 0) {
            document.add(new Paragraph("Adresse: " + address, TEXT_FONT));
        }

        document.add(new Paragraph("Ablesejahr: " + jahr, TEXT_FONT));
        document.add(new Paragraph("Erstellt am: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), TEXT_FONT));
        document.add(new Paragraph(" "));
    }

    private void addRoomSection(Document document,
                                Raum raum,
                                int zielJahr,
                                NumberFormat decimalFormat,
                                DateTimeFormatter dateFormatter) throws DocumentException {
        Paragraph header = new Paragraph("Raum: " + raum.getName(), SECTION_FONT);
        document.add(header);
        if (hasText(raum.getBeschreibung())) {
            Paragraph description = new Paragraph("Beschreibung: " + raum.getBeschreibung().trim(), TEXT_FONT);
            description.setSpacingAfter(4f);
            document.add(description);
        }

        List<Verbrauchsgegenstand> gegenstaende = verbrauchsgegenstandRepository.findByRaumOrderByNameAsc(raum);
        if (gegenstaende.isEmpty()) {
            document.add(new Paragraph("Keine Verbrauchszähler in diesem Raum.", TEXT_FONT));
            document.add(new Paragraph(" "));
            return;
        }

        PdfPTable table = new PdfPTable(new float[]{2.2f, 1.3f, 1.3f, 1.1f, 1.3f, 1.3f, 1.8f});
        table.setWidthPercentage(100);

        addHeaderCell(table, "Zähler");
        addHeaderCell(table, "Einheit");
        addHeaderCell(table, (zielJahr - 1) + " Stand");
        addHeaderCell(table, "Datum");
        addHeaderCell(table, "Verbrauch");
        addHeaderCell(table, "Neuer Stand");
        addHeaderCell(table, "Notizen / Besonderheiten");

        gegenstaende.stream()
                .sorted(Comparator.comparing(Verbrauchsgegenstand::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(gegenstand -> addMeterRow(table, gegenstand, zielJahr, decimalFormat, dateFormatter));

        document.add(table);
        document.add(new Paragraph(" "));
    }

    private void addMeterRow(PdfPTable table,
                             Verbrauchsgegenstand gegenstand,
                             int zielJahr,
                             NumberFormat decimalFormat,
                             DateTimeFormatter dateFormatter) {
        Zaehlerstand referenz = findReferenceReading(gegenstand, zielJahr);

        table.addCell(createValueCell(buildMeterLabel(gegenstand)));
        table.addCell(createValueCell(formatText(gegenstand.getEinheit())));
        table.addCell(createValueCell(formatDecimal(referenz != null ? referenz.getStand() : null, decimalFormat)));
        table.addCell(createValueCell(referenz != null && referenz.getStichtag() != null
                ? referenz.getStichtag().format(dateFormatter)
                : "-"));
        table.addCell(createValueCell(formatDecimal(referenz != null ? referenz.getVerbrauch() : null, decimalFormat)));
        table.addCell(createEmptyInputCell());
        table.addCell(createEmptyInputCell());
    }

    private Zaehlerstand findReferenceReading(Verbrauchsgegenstand gegenstand, int zielJahr) {
        Optional<Zaehlerstand> exaktVorjahr = zaehlerstandRepository
                .findByVerbrauchsgegenstandAndAbrechnungsJahr(gegenstand, zielJahr - 1);
        if (exaktVorjahr.isPresent()) {
            return exaktVorjahr.get();
        }
        return zaehlerstandRepository.findByVerbrauchsgegenstandOrderByAbrechnungsJahrDesc(gegenstand)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private PdfPCell createValueCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", TEXT_FONT));
        cell.setPadding(6f);
        return cell;
    }

    private PdfPCell createEmptyInputCell() {
        PdfPCell cell = new PdfPCell(new Phrase(" ", TEXT_FONT));
        cell.setPadding(12f);
        return cell;
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, HEADER_FONT));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(6f);
        table.addCell(cell);
    }

    private String buildMeterLabel(Verbrauchsgegenstand gegenstand) {
        StringBuilder label = new StringBuilder();
        if (hasText(gegenstand.getName())) {
            label.append(gegenstand.getName().trim());
        }
        if (gegenstand.getVerbrauchsart() != null) {
            if (label.length() > 0) {
                label.append(" - ");
            }
            label.append(gegenstand.getVerbrauchsart().name());
        }
        if (hasText(gegenstand.getSeriennummer())) {
            if (label.length() > 0) {
                label.append(" - ");
            }
            label.append("SN ").append(gegenstand.getSeriennummer().trim());
        }
        return label.length() > 0 ? label.toString() : "-";
    }

    private String formatDecimal(BigDecimal value, NumberFormat decimalFormat) {
        if (value == null) {
            return "-";
        }
        return decimalFormat.format(value);
    }

    private String formatText(String value) {
        return hasText(value) ? value.trim() : "-";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
