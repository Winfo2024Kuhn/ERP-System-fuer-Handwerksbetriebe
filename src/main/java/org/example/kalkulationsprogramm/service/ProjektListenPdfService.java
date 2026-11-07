package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.util.PdfWriterFabrik;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.service.pdf.PdfBerichtBausteine;
import org.example.kalkulationsprogramm.service.pdf.PdfDesign;
import org.example.kalkulationsprogramm.service.pdf.PdfFirmenDesign;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Erzeugt eine übersichtliche Liste aller Projekte, die noch nicht auf "Beendet" gesetzt wurden.
 * Kopf mit Logo, Tabellenkopf und Wechselzeilen kommen aus dem Firmendesign ({@link PdfFirmenDesign}).
 */
@Service
@RequiredArgsConstructor
public class ProjektListenPdfService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.GERMANY);

    private final ProjektRepository projektRepository;
    private final PdfFirmenDesign pdfFirmenDesign;

    /**
     * Listet genau die Projekte auf, bei denen der Haken "Beendet" nicht gesetzt ist –
     * ohne weitere Filter, damit die Liste immer 1:1 zur Projektübersicht passt.
     */
    @Transactional(readOnly = true)
    public byte[] generatePdf() {
        List<Projekt> projekte = projektRepository.findByAbgeschlossenFalseOrderByAnlegedatumDesc();
        // Erst-/Folgeauftrag für alle Zeilen in einer Abfrage klären statt pro Zeile.
        Set<Long> folgeauftraege = projekte.isEmpty()
                ? Set.of()
                : Set.copyOf(projektRepository.findIdsMitVorherigemProjektFuerKunde(
                        projekte.stream().map(Projekt::getId).toList()));

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            // Hochformat: Die Liste wird meist ausgedruckt und abgeheftet – Querformat passt dort nicht.
            Document document = new Document(PageSize.A4, 36, 36, 42, 36);
            PdfWriterFabrik.writer(document, output);
            document.open();

            PdfDesign design = pdfFirmenDesign.laden();
            PdfBerichtBausteine.kopf(document, design, "Projekte in Arbeit",
                    "Erstellt am " + LocalDate.now().format(DATE_FORMAT) + " · " + projekte.size() + " Projekte");

            // Spaltenbreiten sind auf das schmalere Hochformat abgestimmt: Auftragsnummer,
            // Datum und Betrag brauchen genug Platz, damit sie nicht umbrechen – der Rest
            // geht an Kunde und Bauvorhaben, die ohnehin mehrzeilig laufen dürfen.
            PdfPTable table = new PdfPTable(new float[] { 0.7f, 1.75f, 2.7f, 3.05f, 1.5f, 1.8f });
            table.setWidthPercentage(100);
            // Auf Folgeseiten die Kopfzeile wiederholen – im Hochformat passen weniger Zeilen auf eine Seite.
            table.setHeaderRows(1);
            String[] headers = { "Art", "Auftragsnr.", "Kunde", "Bauvorhaben", "Angelegt", "Betrag" };
            for (String header : headers) {
                table.addCell(PdfBerichtBausteine.kopfZelle(header, design, Element.ALIGN_LEFT));
            }

            BigDecimal summe = BigDecimal.ZERO;
            for (int i = 0; i < projekte.size(); i++) {
                Projekt projekt = projekte.get(i);
                Color hintergrund = i % 2 == 0 ? Color.WHITE : design.akzentHell();
                table.addCell(zelle(ermittleAuftragsart(projekt, folgeauftraege), hintergrund, Element.ALIGN_CENTER));
                table.addCell(zelle(projekt.getAuftragsnummer(), hintergrund, Element.ALIGN_LEFT));
                table.addCell(zelle(projekt.getKunde(), hintergrund, Element.ALIGN_LEFT));
                table.addCell(zelle(projekt.getBauvorhaben(), hintergrund, Element.ALIGN_LEFT));
                table.addCell(zelle(projekt.getAnlegedatum() == null ? "" : projekt.getAnlegedatum().format(DATE_FORMAT),
                        hintergrund, Element.ALIGN_LEFT));
                BigDecimal betrag = projekt.getBruttoPreis() == null ? BigDecimal.ZERO : projekt.getBruttoPreis();
                table.addCell(zelle(CURRENCY_FORMAT.format(betrag), hintergrund, Element.ALIGN_RIGHT));
                summe = summe.add(betrag);
            }

            Font summenSchrift = PdfBerichtBausteine.schrift(9, Font.BOLD, PdfDesign.TEXT_DUNKEL);
            PdfPCell sumLabel = PdfBerichtBausteine.zelle("Gesamtsumme", PdfDesign.SUMME_HINTERGRUND,
                    Element.ALIGN_RIGHT, summenSchrift);
            sumLabel.setColspan(5);
            table.addCell(sumLabel);
            table.addCell(PdfBerichtBausteine.zelle(CURRENCY_FORMAT.format(summe), PdfDesign.SUMME_HINTERGRUND,
                    Element.ALIGN_RIGHT, summenSchrift));

            document.add(table);
            document.close();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Projektliste konnte nicht als PDF erstellt werden.", exception);
        }
    }

    private static PdfPCell zelle(String wert, Color hintergrund, int ausrichtung) {
        return PdfBerichtBausteine.zelle(wert, hintergrund, ausrichtung,
                PdfBerichtBausteine.schrift(9, Font.NORMAL, PdfDesign.TEXT_ZELLE));
    }

    /** "F" = Folgeauftrag (Kunde hatte schon ein Projekt), "E" = Erstauftrag. */
    private String ermittleAuftragsart(Projekt projekt, Set<Long> folgeauftraege) {
        if (projekt.getKundenId() == null || projekt.getAnlegedatum() == null || projekt.getId() == null) {
            return "–";
        }
        return folgeauftraege.contains(projekt.getId()) ? "F" : "E";
    }
}
