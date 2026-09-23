package org.example.kalkulationsprogramm.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import com.lowagie.text.Image;
import org.example.kalkulationsprogramm.domain.einkauf.Dokumentart;
import org.example.kalkulationsprogramm.domain.einkauf.Einheit;
import org.example.kalkulationsprogramm.domain.einkauf.Positionsart;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.Snapshot;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPdfDto.Beleg;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPdfDto.PdfHerkunft;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPdfDto.PdfPosition;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.DokumentSoll;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBedarf;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPdfDto.PdfKosten;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Liefergruppe;
import org.example.kalkulationsprogramm.repository.EinkaufBedarfRepository;
import org.example.kalkulationsprogramm.repository.SchnittbilderRepository;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufPdfPositionsRenderer;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufPdfService;
import org.example.kalkulationsprogramm.domain.Schnittbilder;
import org.springframework.core.io.ByteArrayResource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class EinkaufPdfServiceTest {
    @Test
    void erzeugtMehrseitigesAnfragePdfMitTechnikUndOhneLieferantenpreise() throws Exception {
        FirmeninformationService firma = mock(FirmeninformationService.class);
        when(firma.loadLogoImage()).thenReturn(null);
        SchnittbilderRepository schnittbilder = mock(SchnittbilderRepository.class);
        Schnittbilder schnittbild = new Schnittbilder();
        schnittbild.setForm("I");
        schnittbild.setBildUrlSchnittbild("/uploads/i.png");
        when(schnittbilder.findByForm("I")).thenReturn(schnittbild);
        DateiSpeicherService dateien = mock(DateiSpeicherService.class);
        when(dateien.ladeBildAlsResource("i.png")).thenReturn(new ByteArrayResource(png()));
        EinkaufPdfPositionsRenderer renderer = new EinkaufPdfPositionsRenderer(schnittbilder, dateien);
        EinkaufPdfService service = new EinkaufPdfService(renderer, firma, null);
        List<PdfPosition> positionen = java.util.stream.IntStream.rangeClosed(1, 60)
                .mapToObj(i -> position("POS-%02d".formatted(i))).toList();
        Beleg beleg = new Beleg("ANFRAGE", "PA-2026-0001", 2,
                new Snapshot(1L, 1L, "Lieferant Dummy", "test@example.com", "Testkontakt", null, "KD-01"),
                positionen, List.of(), List.of(), LocalDate.of(2026, 10, 1), null,
                "Bitte langes Maß prüfen – Grüße aus Köln. " + "Zusätzliche technische Rückfragen mit möglichst genauer Antwort. ".repeat(18), null, true);

        byte[] pdf = service.erzeugen(beleg);
        try (var document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(document.getNumberOfPages() > 1);
            assertTrue(document.getPages().get(0).getResources().getXObjectNames().iterator().hasNext(),
                    "Schnittbild muss als PDF-Bild eingebettet sein");
            assertTrue(text.contains("PA-2026-0001"));
            String normalized = text.replaceAll("\\s+", " ");
            assertTrue(normalized.contains("4 Stk à 6000 mm"), text);
            assertTrue(normalized.contains("links 45° / rechts 90°"));
            assertTrue(normalized.contains("S235JR"));
            assertTrue(normalized.contains("Zeugnis 3.1"));
            assertTrue(normalized.contains("Zeugnis 2.2"));
            assertTrue(normalized.contains("Köln"));
            assertFalse(normalized.contains("Fremdpreis"));
        }
        verify(schnittbilder).findByForm("I");
        verify(dateien).ladeBildAlsResource("i.png");
        verifyNoMoreInteractions(schnittbilder, dateien);
    }

    @Test
    void zeigtBestellPdfNurUebergebeneEingefroreneKonditionen() throws Exception {
        FirmeninformationService firma = mock(FirmeninformationService.class);
        when(firma.loadLogoImage()).thenReturn(Image.getInstance(png()));
        SchnittbilderRepository schnittbilder = mock(SchnittbilderRepository.class);
        Schnittbilder schnittbild = new Schnittbilder();
        schnittbild.setForm("I");
        schnittbild.setBildUrlSchnittbild("/uploads/i.png");
        when(schnittbilder.findByForm("I")).thenReturn(schnittbild);
        DateiSpeicherService dateien = mock(DateiSpeicherService.class);
        when(dateien.ladeBildAlsResource("i.png")).thenReturn(new ByteArrayResource(png()));
        EinkaufPdfService service = new EinkaufPdfService(new EinkaufPdfPositionsRenderer(schnittbilder, dateien), firma, null);
        PdfPosition bestellposition = new PdfPosition("10", position("10").technik(), List.of(), List.of(
                new PdfKosten("gewählte Positionskosten", new BigDecimal("493.80"), "freigegebenes Angebot",
                        new BigDecimal("4"), true, "4 Stück × 123,45 EUR"),
                new PdfKosten("nicht ausgewählte Kondition", new BigDecimal("99.00"), null, null, false, null)),
                new BigDecimal("493.80"));
        List<PdfPosition> positionen = java.util.stream.IntStream.rangeClosed(1, 60)
                .mapToObj(i -> i == 1 ? bestellposition : new PdfPosition("%02d".formatted(i), position("%02d".formatted(i)).technik(),
                        List.of(), List.of(new PdfKosten("gewählter Stückpreis", new BigDecimal("123.45"),
                                "freigegebenes Angebot", BigDecimal.ONE, true, "1 Stück × 123,45 EUR")), new BigDecimal("123.45")))
                .toList();
        Beleg beleg = new Beleg("BESTELLUNG", "B-2026-0001", 3,
                new Snapshot(1L, 1L, "Lieferant Dummy", "test@example.com", "Testkontakt", null, "KD-01"),
                positionen, List.of(), List.of(
                        new Liefergruppe("Werkstatt, Beispielstraße 1", LocalDate.of(2026, 10, 10), 12L, "Projekt"),
                        new Liefergruppe("Lager, Musterweg 2", LocalDate.of(2026, 10, 12), 12L, "Projekt")),
                null, LocalDate.of(2026, 10, 10), "Lieferung frei Haus. " + "Langes Beispiel mit Umlauten: Größe, Maße und Grüße. ".repeat(12),
                new BigDecimal("7777.35"), false);

        byte[] pdf = service.erzeugen(beleg);
        String artifact = System.getProperty("task14.pdfArtifact");
        if (artifact != null && !artifact.isBlank()) Files.write(Path.of(artifact), pdf);
        try (var document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() > 1);
            assertTrue(document.getPages().get(0).getResources().getXObjectNames().iterator().hasNext(),
                    "Firmenlogo und Schnittbild müssen als PDF-Bilder eingebettet werden");
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertTrue(text.contains("123,45 EUR"));
            assertTrue(text.contains("493,80 EUR"));
            assertTrue(text.contains("7777,35 EUR"));
            assertTrue(text.contains("freigegebenes Angebot"));
            assertTrue(text.contains("Größe, Maße und Grüße"));
            assertTrue(text.contains("Gruppe 2"));
            assertFalse(text.contains("99,00 EUR"));
            assertFalse(text.contains("nicht ausgewählte Kondition"));
        }
    }

    @Test
    void drucktEntnahmelisteReadOnlyMitHinweisUndIstmengenfeld() throws Exception {
        FirmeninformationService firma = mock(FirmeninformationService.class);
        when(firma.loadLogoImage()).thenReturn(null);
        EinkaufBedarfRepository repository = mock(EinkaufBedarfRepository.class);
        PositionSnapshot position = position("BEDARF").technik();
        EinkaufBedarf bedarf = new EinkaufBedarf(position, new Liefergruppe("Werkstatt", null, 12L, "Projekt"), 12L, null, false);
        bedarf.setId(42L);
        when(repository.findAllById(List.of(42L))).thenReturn(List.of(bedarf));
        EinkaufPdfService service = new EinkaufPdfService(new EinkaufPdfPositionsRenderer(null, null), firma, repository);

        try (var document = Loader.loadPDF(service.entnahmeliste(List.of(42L)).getInputStream().readAllBytes())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Ausdruck bestätigt keine Entnahme"));
            assertTrue(text.contains("Istmenge"));
            assertTrue(text.contains("42"));
            assertTrue(text.contains("S235JR"));
        }
        verify(repository).findAllById(List.of(42L));
        verifyNoMoreInteractions(repository);
    }

    private static PdfPosition position(String id) {
        PositionSnapshot snapshot = new PositionSnapshot(Positionsart.ZEICHNUNGSTEIL, null, "INT-42", "Z-42", "B",
                "Profil langes Bauteil für Prüfung und Umbruch", "S235JR", "100 x 50 x 5 mm",
                new Mengenbasis(new BigDecimal("24"), Einheit.METER, new BigDecimal("4"),
                        new BigDecimal("6000"), null, null), "I", "45°", "90°", "Bohrungen und Sägen",
                "feuerverzinkt", List.of(
                        new DokumentSoll(Dokumentart.ZEUGNIS_3_1, "Auftrag", "B", true),
                        new DokumentSoll(Dokumentart.ZEUGNIS_2_2, "Auftrag", "B", true)), List.of(7L));
        return new PdfPosition(id, snapshot, List.of(new PdfHerkunft(1L, "P-01", new BigDecimal("24"), Einheit.METER)),
                List.of(), null);
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(12, 12, BufferedImage.TYPE_INT_RGB);
        image.setRGB(3, 3, 0xff000000);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
