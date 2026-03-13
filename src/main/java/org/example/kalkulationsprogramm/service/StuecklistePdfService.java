package org.example.kalkulationsprogramm.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCellEvent;
import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.SchnittbilderRepository;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class StuecklistePdfService {

    private final ProjektRepository projektRepository;
    private final ArtikelInProjektRepository artikelInProjektRepository;
    private final SchnittbilderRepository schnittbilderRepository;
    private final DateiSpeicherService dateiSpeicherService;

    private static final byte[] NO_IMAGE = new byte[0];
    private final Map<String, byte[]> schnittbildIconCache = new ConcurrentHashMap<>();

    public byte[] generateForProjekt(Long projektId) {
        Projekt projekt = projektRepository.findById(projektId)
                .orElseThrow(() -> new RuntimeException("Projekt konnte nicht gefunden werden."));
        List<ArtikelInProjekt> items = artikelInProjektRepository.findByProjekt_Id(projektId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            Document doc = new Document(PageSize.A4.rotate());
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setCompressionLevel(0);
            doc.open();

            try {
                Image logo = Image.getInstance(StuecklistePdfService.class.getResource("/static/firmenlogo.png"));
                logo.scaleToFit(150, 70);
                doc.add(logo);
            } catch (Exception ignored) {}

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(204, 0, 0));
            Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
            Color headerBg = new Color(204, 0, 0);
            Color altBg = new Color(245, 245, 245);

            Paragraph title = new Paragraph("St\u00FCckliste", titleFont);
            title.setSpacingAfter(10f);
            doc.add(title);

            Paragraph meta = new Paragraph(
                    "Bauvorhaben: " + nvl(projekt.getBauvorhaben()) + "\n" +
                            "Auftragsnummer: " + nvl(projekt.getAuftragsnummer()) + "\n" +
                            "Kunde: " + nvl(projekt.getKunde()),
                    FontFactory.getFont(FontFactory.HELVETICA, 10)
            );
            meta.setSpacingAfter(12f);
            doc.add(meta);

            Map<Integer, Map<String, List<ArtikelInProjekt>>> grouped = items.stream()
                    .filter(aip -> aip.getArtikel() != null)
                    .collect(Collectors.groupingBy(
                            aip -> rootKategorieId(aip.getArtikel()),
                            Collectors.groupingBy(aip -> subKategorieName(aip.getArtikel()))
                    ));

            List<Integer> rootOrder = List.of(1, 2, 3);
            for (Integer root : rootOrder) {
                Map<String, List<ArtikelInProjekt>> bySub = grouped.get(root);
                if (bySub == null || bySub.isEmpty()) continue;

                String rootName = rootKategorieName(bySub.values().stream().flatMap(List::stream).findFirst().orElse(null));
                Paragraph rootHeader = new Paragraph(nvl(rootName), subTitleFont);
                rootHeader.setSpacingBefore(8f);
                rootHeader.setSpacingAfter(6f);
                doc.add(rootHeader);

                for (String sub : bySub.keySet().stream().sorted(Comparator.nullsLast(String::compareToIgnoreCase)).toList()) {
                    List<ArtikelInProjekt> list = bySub.get(sub);
                    if (list == null || list.isEmpty()) continue;
                    if (sub != null && !sub.isBlank()) {
                        Paragraph subHeader = new Paragraph(sub, FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10));
                        subHeader.setSpacingBefore(4f);
                        subHeader.setSpacingAfter(4f);
                        doc.add(subHeader);
                    }

                    boolean werkstoff = Objects.equals(root, 1);
                    PdfPTable table;
                    if (werkstoff) {
                        table = new PdfPTable(new float[]{2f, 3f, 2f, 1f, 1.5f, 1.5f, 1.2f, 1.2f, 1.2f, 2f, 1.3f, 1.3f, 1.8f});
                        table.setWidthPercentage(100);
                        String[] headers = {"Art.-Nr.", "Prod.", "Werkst.", "Stk", "L/Stk [mm]", "Ges. (m)", "Form", "WL", "WR", "Komm.", "Vorh.", "Best.", "R. gel."};
                        for (int i = 0; i < headers.length; i++) {
                            String h = headers[i];
                            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                            cell.setBackgroundColor(headerBg);
                            if (i >= headers.length - 3) { cell.setNoWrap(true); cell.setPaddingLeft(2f); cell.setPaddingRight(2f); }
                            table.addCell(cell);
                        }
                        boolean alternate = false;
                        BigDecimal totalM = BigDecimal.ZERO;
                        BigDecimal totalKg = BigDecimal.ZERO;
                        BigDecimal totalMantel = BigDecimal.ZERO;
                        for (ArtikelInProjekt aip : list) {
                            Color bg = alternate ? altBg : Color.WHITE;
                            Artikel a = aip.getArtikel();
                            table.addCell(makeCell(nvl(a.getExterneArtikelnummer()), cellFont, bg));
                            table.addCell(makeCell(nvl(a.getProduktname()), cellFont, bg));
                            table.addCell(makeCell(a.getWerkstoff() != null ? nvl(a.getWerkstoff().getName()) : "", cellFont, bg));
                            table.addCell(makeCell(aip.getStueckzahl() != null ? String.valueOf(aip.getStueckzahl()) : "", cellFont, bg));
                            table.addCell(makeCell(calcMmPerStueck(aip), cellFont, bg));
                            String gesamtM = aip.getMeter() != null ? stripZeros(aip.getMeter()) : "";
                            table.addCell(makeCell(gesamtM, cellFont, bg));
                            table.addCell(makeCutCell(aip.getSchnittForm(), cellFont, bg));
                            table.addCell(makeCell(nvl(aip.getAnschnittWinkelLinks()), cellFont, bg));
                            table.addCell(makeCell(nvl(aip.getAnschnittWinkelRechts()), cellFont, bg));
                            table.addCell(makeCell(nvl(aip.getKommentar()), cellFont, bg));
                            table.addCell(makeCheckboxCell(cellFont, bg));
                            table.addCell(makeCheckboxCell(cellFont, bg));
                            table.addCell(makeCheckboxCell(cellFont, bg));
                            alternate = !alternate;
                            if (aip.getMeter() != null) totalM = totalM.add(aip.getMeter());
                            if (aip.getKilogramm() != null) {
                                totalKg = totalKg.add(aip.getKilogramm());
                            } else if (a instanceof ArtikelWerkstoffe aw && aw.getMasse() != null && aip.getMeter() != null) {
                                totalKg = totalKg.add(aw.getMasse().multiply(aip.getMeter()));
                            }
                            if (a instanceof ArtikelWerkstoffe aw2 && aw2.getMantelflaeche() != null && aip.getMeter() != null) {
                                totalMantel = totalMantel.add(aw2.getMantelflaeche().multiply(aip.getMeter()));
                            }
                            if (isKategorie15(a)) {
                                PdfPTable info = new PdfPTable(new float[]{2.8f, 4.2f});
                                info.setWidthPercentage(100);
                                PdfPCell chk = makeCheckboxLabelCell("Anzugsmoment gepr\u00FCft", cellFont, Color.WHITE);
                                PdfPCell sig = new PdfPCell(new Phrase("Unterschrift/Datum:", cellFont));
                                sig.setBorderWidthTop(0);
                                sig.setBorderWidthLeft(0);
                                sig.setBorderWidthRight(0);
                                sig.setBorderWidthBottom(0.8f);
                                sig.setPaddingTop(6f);
                                sig.setPaddingBottom(2f);
                                info.addCell(chk);
                                info.addCell(sig);

                                PdfPCell wrap = new PdfPCell(info);
                                wrap.setColspan(table.getNumberOfColumns());
                                wrap.setBackgroundColor(Color.WHITE);
                                table.addCell(wrap);
                            }
                        }
                        // Summenzeile in der Haupttabelle
                        PdfPCell c0 = makeCell("", cellFont, altBg);
                        PdfPCell c1 = makeCell("Summe", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8), altBg);
                        PdfPCell c2 = makeCell("", cellFont, altBg);
                        PdfPCell c3 = makeCell("", cellFont, altBg);
                        PdfPCell c4 = makeCell("", cellFont, altBg);
                        PdfPCell c5 = makeCell(stripZeros(totalM), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8), altBg);
                        String sumComment = "Σ kg: " + (totalKg.compareTo(BigDecimal.ZERO) == 0 ? "0" : stripZeros(totalKg))
                                + " | Σ Mant.: " + (totalMantel.compareTo(BigDecimal.ZERO) == 0 ? "0" : stripZeros(totalMantel)) + " m²";
                        PdfPCell c6 = makeCell(sumComment, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8), altBg);
                        PdfPCell c7 = makeCell("", cellFont, altBg);
                        PdfPCell c8 = makeCell("", cellFont, altBg);
                        PdfPCell c9 = makeCell("", cellFont, altBg);
                        table.addCell(c0); table.addCell(c1); table.addCell(c2); table.addCell(c3); table.addCell(c4);
                        table.addCell(c5); table.addCell(c6); table.addCell(c7); table.addCell(c8); table.addCell(c9);
                        doc.add(table);
                    } else {
                        table = new PdfPTable(new float[]{2f, 4f, 2f, 1.5f, 1.2f, 1.2f, 1.2f, 2f, 1.3f, 1.3f, 1.8f});
                        table.setWidthPercentage(100);
                        String[] headers = {"Art.-Nr.", "Prod.", "Kat.", "Menge", "Form", "WL", "WR", "Komm.", "Vorh.", "Best.", "R. gel."};
                        for (int i = 0; i < headers.length; i++) {
                            String h = headers[i];
                            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                            cell.setBackgroundColor(headerBg);
                            if (i >= headers.length - 3) { cell.setNoWrap(true); cell.setPaddingLeft(2f); cell.setPaddingRight(2f); }
                            table.addCell(cell);
                        }
                        boolean alternate = false;
                        for (ArtikelInProjekt aip : list) {
                            Color bg = alternate ? altBg : Color.WHITE;
                            Artikel a = aip.getArtikel();
                            table.addCell(makeCell(nvl(a.getExterneArtikelnummer()), cellFont, bg));
                            table.addCell(makeCell(nvl(a.getProduktname()), cellFont, bg));
                            String katName = a.getKategorie() != null ? nvl(a.getKategorie().getBeschreibung()) : "";
                            table.addCell(makeCell(katName, cellFont, bg));
                            table.addCell(makeCell(formatMengeAllgemein(aip), cellFont, bg));
                            table.addCell(makeCutCell(aip.getSchnittForm(), cellFont, bg));
                            table.addCell(makeCell(nvl(aip.getAnschnittWinkelLinks()), cellFont, bg));
                            table.addCell(makeCell(nvl(aip.getAnschnittWinkelRechts()), cellFont, bg));
                            table.addCell(makeCell(nvl(aip.getKommentar()), cellFont, bg));
                            table.addCell(makeCheckboxCell(cellFont, bg));
                            table.addCell(makeCheckboxCell(cellFont, bg));
                            table.addCell(makeCheckboxCell(cellFont, bg));
                            alternate = !alternate;
                            if (isKategorie15(a)) {
                                PdfPTable info = new PdfPTable(new float[]{2.8f, 4.2f});
                                info.setWidthPercentage(100);
                                PdfPCell chk = makeCheckboxLabelCell("Anzugsmoment gepr\u00FCft", cellFont, Color.WHITE);
                                PdfPCell sig = new PdfPCell(new Phrase("Unterschrift/Datum:", cellFont));
                                sig.setBorderWidthTop(0);
                                sig.setBorderWidthLeft(0);
                                sig.setBorderWidthRight(0);
                                sig.setBorderWidthBottom(0.8f);
                                sig.setPaddingTop(6f);
                                sig.setPaddingBottom(2f);
                                info.addCell(chk);
                                info.addCell(sig);

                                PdfPCell wrap = new PdfPCell(info);
                                wrap.setColspan(table.getNumberOfColumns());
                                wrap.setBackgroundColor(Color.WHITE);
                                table.addCell(wrap);
                            }
                        }
                        doc.add(table);
                    }
                    doc.add(new Paragraph(" ", cellFont));
                }
            }

            doc.close();
        } catch (Exception e) {
            throw new RuntimeException("PDF generation failed", e);
        }
        try {
            java.io.InputStream refIs = StuecklistePdfService.class.getResourceAsStream("/static/Schnittbilder_Formstahl_Kuhn Copy.pdf");
            if (refIs != null) {
                com.lowagie.text.pdf.PdfReader mainReader = new com.lowagie.text.pdf.PdfReader(baos.toByteArray());
                com.lowagie.text.pdf.PdfReader refReader = new com.lowagie.text.pdf.PdfReader(refIs);
                java.io.ByteArrayOutputStream mergedOut = new java.io.ByteArrayOutputStream();
                com.lowagie.text.Document merged = new com.lowagie.text.Document();
                com.lowagie.text.pdf.PdfCopy copy = new com.lowagie.text.pdf.PdfCopy(merged, mergedOut);
                merged.open();
                for (int i = 1; i <= mainReader.getNumberOfPages(); i++) {
                    copy.addPage(copy.getImportedPage(mainReader, i));
                }
                for (int i = 1; i <= refReader.getNumberOfPages(); i++) {
                    copy.addPage(copy.getImportedPage(refReader, i));
                }
                merged.close();
                mainReader.close();
                refReader.close();
                return mergedOut.toByteArray();
            }
        } catch (Exception ignored) {}
        return baos.toByteArray();
    }

    private String nvl(String s) { return s == null ? "" : s; }

    private String stripZeros(BigDecimal v) {
        if (v == null) return "";
        return v.stripTrailingZeros().toPlainString();
    }

    private PdfPCell makeCutCell(String form, Font font, Color bg) {
        if (form == null || form.isBlank()) {
            return makeCell("", font, bg);
        }
        byte[] imageBytes = loadSchnittbildIcon(form);
        if (imageBytes == null) {
            return makeCell("Form " + form, font, bg);
        }
        try {
            Image icon = Image.getInstance(imageBytes);
            icon.scaleToFit(26f, 26f);
            icon.setAlignment(Element.ALIGN_CENTER);
            PdfPCell cell = new PdfPCell();
            cell.setBackgroundColor(bg);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(2f);
            cell.addElement(icon);
            Paragraph label = new Paragraph("Form " + form, FontFactory.getFont(FontFactory.HELVETICA, 6));
            label.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(label);
            return cell;
        } catch (Exception e) {
            return makeCell("Form " + form, font, bg);
        }
    }

    private byte[] loadSchnittbildIcon(String form) {
        byte[] cached = schnittbildIconCache.get(form);
        if (cached != null) {
            return cached == NO_IMAGE ? null : cached;
        }
        byte[] loaded = fetchSchnittbildIcon(form);
        schnittbildIconCache.put(form, loaded != null ? loaded : NO_IMAGE);
        return loaded;
    }

    private byte[] fetchSchnittbildIcon(String form) {
        try {
            var entity = schnittbilderRepository.findByForm(form);
            if (entity == null) {
                return null;
            }
            String bildUrl = entity.getBildUrlSchnittbild();
            String dateiname = extractFilename(bildUrl);
            if (dateiname == null) {
                return null;
            }
            var resource = dateiSpeicherService.ladeBildAlsResource(dateiname);
            try (var in = resource.getInputStream()) {
                return in.readAllBytes();
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractFilename(String bildUrl) {
        if (bildUrl == null || bildUrl.isBlank()) {
            return null;
        }
        String cleaned = bildUrl;
        int query = cleaned.indexOf('?');
        if (query >= 0) {
            cleaned = cleaned.substring(0, query);
        }
        int idx = cleaned.lastIndexOf('/');
        String name = idx >= 0 ? cleaned.substring(idx + 1) : cleaned;
        return name.isBlank() ? null : name;
    }

    private PdfPCell makeCell(String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setBackgroundColor(bg);
        return cell;
    }

    private PdfPCell makeCheckboxCell(Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase("", font));
        cell.setBackgroundColor(bg);
        cell.setFixedHeight(14f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setCellEvent(new CheckboxCellEvent());
        return cell;
    }

    private PdfPCell makeCheckboxLabelCell(String label, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(label, font));
        cell.setBackgroundColor(bg);
        cell.setFixedHeight(16f);
        cell.setPaddingLeft(14f);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setCellEvent(new LeftCheckboxCellEvent());
        return cell;
    }

    private static class CheckboxCellEvent implements PdfPCellEvent {
        @Override
        public void cellLayout(PdfPCell cell, Rectangle rect, PdfContentByte[] canvas) {
            PdfContentByte cb = canvas[PdfPTable.LINECANVAS];
            float size = Math.min(rect.getHeight(), rect.getWidth()) * 0.6f;
            float x = rect.getLeft() + (rect.getWidth() - size) / 2f;
            float y = rect.getBottom() + (rect.getHeight() - size) / 2f;
            cb.rectangle(x, y, size, size);
            cb.stroke();
        }
    }

    private static class LeftCheckboxCellEvent implements PdfPCellEvent {
        @Override
        public void cellLayout(PdfPCell cell, Rectangle rect, PdfContentByte[] canvas) {
            PdfContentByte cb = canvas[PdfPTable.LINECANVAS];
            float size = Math.min(rect.getHeight(), 10f) * 0.6f;
            float x = rect.getLeft() + 2f;
            float y = rect.getBottom() + (rect.getHeight() - size) / 2f;
            cb.rectangle(x, y, size, size);
            cb.stroke();
        }
    }

    private boolean isKategorie15(Artikel a) {
        Kategorie k = a != null ? a.getKategorie() : null;
        while (k != null) {
            if (k.getId() != null && k.getId() == 15) return true;
            k = k.getParentKategorie();
        }
        return false;
    }

    private Integer rootKategorieId(Artikel a) {
        Kategorie k = a.getKategorie();
        if (k == null) return null;
        while (k.getParentKategorie() != null) k = k.getParentKategorie();
        return k.getId();
    }

    private String rootKategorieName(ArtikelInProjekt sample) {
        if (sample == null || sample.getArtikel() == null || sample.getArtikel().getKategorie() == null) return "";
        Kategorie k = sample.getArtikel().getKategorie();
        while (k.getParentKategorie() != null) k = k.getParentKategorie();
        return k.getBeschreibung();
    }

    private String subKategorieName(Artikel a) {
        Kategorie k = a.getKategorie();
        if (k == null) return null;
        if (k.getParentKategorie() == null) return null;
        return k.getBeschreibung();
    }

    private String calcMmPerStueck(ArtikelInProjekt aip) {
        try {
            if (aip == null) return "";
            if (aip.getStueckzahl() != null && aip.getStueckzahl() > 0 && aip.getMeter() != null
                    && aip.getMeter().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal st = BigDecimal.valueOf(aip.getStueckzahl());
                BigDecimal perPieceM = aip.getMeter().divide(st, 6, RoundingMode.HALF_UP);
                BigDecimal perPieceMm = perPieceM.multiply(new BigDecimal("1000"));
                return perPieceMm.setScale(0, RoundingMode.HALF_UP).toPlainString();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String formatMengeAllgemein(ArtikelInProjekt aip) {
        if (aip == null || aip.getArtikel() == null || aip.getArtikel().getVerrechnungseinheit() == null) return "";
        Verrechnungseinheit ve = aip.getArtikel().getVerrechnungseinheit();
        return switch (ve) {
            case KILOGRAMM -> aip.getKilogramm() != null ? stripZeros(aip.getKilogramm()) + " kg" : "";
            case LAUFENDE_METER, QUADRATMETER -> aip.getMeter() != null ? stripZeros(aip.getMeter()) + " m" : "";
            case STUECK -> aip.getStueckzahl() != null ? aip.getStueckzahl() + " Stk" : "";
        };
    }
}
