package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.kalkulationsprogramm.repository.HiCadImportRepository;
import org.example.kalkulationsprogramm.domain.einkauf.HiCadImport;
import org.example.kalkulationsprogramm.domain.einkauf.HiCadImportZeile;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBedarfDto;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufDateiDto;
import org.example.kalkulationsprogramm.dto.Einkauf.HiCadImportDto;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBedarfService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufDateiService;
import org.example.kalkulationsprogramm.service.einkauf.HiCadImportService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class HiCadImportServiceTest {
    @Test
    void previewsGermanQuantitiesWithoutCreatingDemand() throws Exception {
        HiCadImportRepository imports = mock(HiCadImportRepository.class);
        when(imports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        EinkaufBedarfService bedarfe = mock(EinkaufBedarfService.class);
        HiCadImportService service = new HiCadImportService(imports, bedarfe, mock(EinkaufDateiService.class));
        byte[] workbook = workbook("12,5");

        var preview = service.vorschau(17L, new MockMultipartFile("file", "teile.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook),
                null, 4L);

        assertEquals(1, preview.zeilen().size());
        org.junit.jupiter.api.Assertions.assertTrue(preview.zeilen().get(0).rohtext().contains("12,5"));
        assertEquals(0, preview.zeilen().get(0).vorschlag().basis().menge().compareTo(new java.math.BigDecimal("12.5")));
        verify(bedarfe, never()).anlegen(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsWorkbooksOverTheTenMiBLimit() {
        HiCadImportService service = new HiCadImportService(mock(HiCadImportRepository.class),
                mock(EinkaufBedarfService.class), mock(EinkaufDateiService.class));
        byte[] tooLarge = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "parts.xlsx", "application/octet-stream", tooLarge);
        assertThrows(IllegalArgumentException.class, () -> service.vorschau(1L, file, null, 2L));
    }

    @Test
    void rejectsHighlyCompressedWorksheetZipBombs() throws Exception {
        HiCadImportRepository imports = mock(HiCadImportRepository.class);
        HiCadImportService service = new HiCadImportService(imports,
                mock(EinkaufBedarfService.class), mock(EinkaufDateiService.class));
        byte[] workbook = workbook("12,5");
        byte[] bomb = replaceWorksheetWithHighlyCompressedXml(workbook);
        org.junit.jupiter.api.Assertions.assertTrue(bomb.length < 10 * 1024 * 1024);

        var file = new MockMultipartFile("file", "parts.xlsx", "application/octet-stream", bomb);
        assertThrows(IllegalArgumentException.class, () -> service.vorschau(17L, file, null, 4L));
        verify(imports, never()).save(any());
    }

    @Test
    void rejectsFormulaCellsInsteadOfEvaluatingThem() throws Exception {
        HiCadImportService service = new HiCadImportService(mock(HiCadImportRepository.class),
                mock(EinkaufBedarfService.class), mock(EinkaufDateiService.class));
        byte[] formulaBook;
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(workbook("12,5")));
                var output = new ByteArrayOutputStream()) {
            workbook.getSheetAt(0).getRow(1).getCell(6).setCellFormula("1+2");
            workbook.write(output);
            formulaBook = output.toByteArray();
        }
        var file = new MockMultipartFile("file", "formula.xlsx", "application/octet-stream", formulaBook);
        var error = assertThrows(IllegalArgumentException.class, () -> service.vorschau(17L, file, null, 4L));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("Formeln"));
    }

    @Test
    void rejectsSheetsOverTheTwentyColumnLimit() throws Exception {
        HiCadImportService service = new HiCadImportService(mock(HiCadImportRepository.class),
                mock(EinkaufBedarfService.class), mock(EinkaufDateiService.class));
        byte[] wide;
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet();
            sheet.createRow(0).createCell(20).setCellValue("too wide");
            workbook.write(output);
            wide = output.toByteArray();
        }
        var file = new MockMultipartFile("file", "wide.xlsx", "application/octet-stream", wide);
        assertThrows(IllegalArgumentException.class, () -> service.vorschau(17L, file, null, 4L));
    }

    @Test
    void transfersOnlyTheSelectedImportRows() {
        HiCadImportRepository imports = mock(HiCadImportRepository.class);
        EinkaufBedarfService bedarfe = mock(EinkaufBedarfService.class);
        HiCadImportService service = new HiCadImportService(imports, bedarfe, mock(EinkaufDateiService.class));
        HiCadImport imported = mock(HiCadImport.class);
        HiCadImportZeile first = mock(HiCadImportZeile.class);
        HiCadImportZeile second = mock(HiCadImportZeile.class);
        when(imports.findById(5L)).thenReturn(Optional.of(imported));
        when(imported.getProjektId()).thenReturn(17L);
        when(imported.getImportInstanz()).thenReturn("12345678-1234-1234-1234-123456789abc");
        when(imported.isDuplikat()).thenReturn(false);
        when(imported.getZeilen()).thenReturn(List.of(first, second));
        when(first.getZeilennummer()).thenReturn(2);
        when(second.getZeilennummer()).thenReturn(3);
        when(second.getBildDateiIdsJson()).thenReturn("[7]");
        String snapshot = "{\"art\":\"ZEICHNUNGSTEIL\",\"interneReferenz\":\"P-17\",\"zeichnungsnummer\":\"Z-17\",\"zeichnungsrevision\":\"A\",\"bezeichnung\":\"Konsole\",\"basis\":{\"menge\":1,\"einheit\":\"STUECK\"},\"dokumente\":[],\"anlageVersionIds\":[7]}";
        when(first.getSnapshotJson()).thenReturn(snapshot);
        when(second.getSnapshotJson()).thenReturn(snapshot);
        var expected = new EinkaufBedarfDto.Response(99L, 1L, null, null, null, false, null);
        when(bedarfe.anlegen(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(4L))).thenReturn(expected);
        when(bedarfe.aktualisieren(org.mockito.ArgumentMatchers.eq(99L), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(4L))).thenReturn(expected);
        EinkaufDateiService fileService = mock(EinkaufDateiService.class);
        when(fileService.anhaengenImportBild(99L, 7L, "HiCAD-12345678-Z3-B1", 4L))
                .thenReturn(new EinkaufDateiDto.AnlageDto(7L, 70L, 99L, "r1", "image.png", "image/png", 9L,
                        "hash", false, false, null));
        service = new HiCadImportService(imports, bedarfe, fileService);
        var request = new HiCadImportDto.Uebernahme(0L,
                List.of(new HiCadImportDto.ZeilenAuswahl(3, null, null, List.of(7L))), false, UUID.randomUUID());

        var result = service.uebernehmen(5L, request, 4L);

        assertEquals(List.of(expected), result);
        verify(bedarfe, times(1)).anlegen(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(4L));
        verify(first, never()).setUebernommen(true);
        verify(second).setUebernommen(true);
    }

    @Test
    void exactArticleSuggestionRequiresInternalNumberAndMatchingSteelGrade() throws Exception {
        HiCadImportRepository imports = mock(HiCadImportRepository.class);
        ArtikelRepository articles = mock(ArtikelRepository.class);
        Artikel catalogArticle = mock(Artikel.class);
        when(imports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(articles.findHiCadByArtikelnummer("P-17")).thenReturn(Optional.of(catalogArticle));
        when(catalogArticle.getId()).thenReturn(41L);
        when(catalogArticle.getWerkstoffnorm()).thenReturn("S235");
        when(catalogArticle.getProduktname()).thenReturn("80x40");
        HiCadImportService service = new HiCadImportService(imports, mock(EinkaufBedarfService.class),
                mock(EinkaufDateiService.class), articles);

        var preview = service.vorschau(17L, new MockMultipartFile("file", "parts.xlsx", "application/octet-stream",
                workbook("12,5", "S355")), new HiCadImportService.SpaltenMapping(Map.ofEntries(
                        Map.entry("interneReferenz", 0), Map.entry("zeichnungsnummer", 1), Map.entry("zeichnungsrevision", 2),
                        Map.entry("bezeichnung", 3), Map.entry("werkstoff", 4), Map.entry("abmessung", 5),
                        Map.entry("menge", 6), Map.entry("einheit", 7), Map.entry("stueckzahl", 8), Map.entry("einzelLaengeMm", 9),
                        Map.entry("winkelLinks", 10), Map.entry("winkelRechts", 11))), 4L);

        assertEquals(List.of(), preview.zeilen().get(0).artikelKandidaten());

        var exact = service.vorschau(17L, new MockMultipartFile("file", "parts.xlsx", "application/octet-stream",
                workbook("12,5", "S235")), new HiCadImportService.SpaltenMapping(Map.ofEntries(
                        Map.entry("interneReferenz", 0), Map.entry("zeichnungsnummer", 1), Map.entry("zeichnungsrevision", 2),
                        Map.entry("bezeichnung", 3), Map.entry("werkstoff", 4), Map.entry("abmessung", 5),
                        Map.entry("menge", 6), Map.entry("einheit", 7), Map.entry("stueckzahl", 8), Map.entry("einzelLaengeMm", 9),
                        Map.entry("winkelLinks", 10), Map.entry("winkelRechts", 11))), 4L);
        assertEquals(List.of(41L), exact.zeilen().get(0).artikelKandidaten());
    }

    @Test
    void persistsEmbeddedRowImagesAndExposesAConfirmationPreview() throws Exception {
        HiCadImportRepository imports = mock(HiCadImportRepository.class);
        java.util.concurrent.atomic.AtomicReference<HiCadImport> persisted = new java.util.concurrent.atomic.AtomicReference<>();
        when(imports.save(any())).thenAnswer(invocation -> {
            HiCadImport saved = invocation.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 12L);
            persisted.set(saved);
            return saved;
        });
        EinkaufDateiService files = mock(EinkaufDateiService.class);
        byte[] image = png();
        when(files.speichereImportBild(anyString(), eq("image/png"), any()))
                .thenReturn(new EinkaufDateiService.ImportBildDto(88L, "HiCAD-Bild-2.png", "image/png", image.length));
        HiCadImportService service = new HiCadImportService(imports, mock(EinkaufBedarfService.class), files);

        var preview = service.vorschau(17L, new MockMultipartFile("file", "parts.xlsx", "application/octet-stream",
                workbook("12,5", "S355", image)), null, 4L);

        var shown = preview.zeilen().get(0).bilder().get(0);
        assertEquals(88L, shown.dateiId());
        assertEquals("image/png", shown.mimeTyp());
        assertEquals("/api/einkauf/hicad/12/bilder/88", shown.url());
        assertEquals("[88]", persisted.get().getZeilen().get(0).getBildDateiIdsJson());
        verify(files).speichereImportBild(anyString(), eq("image/png"), any());
    }

    @Test
    void retryWithSameIdempotencyKeyReturnsStoredResultBeforeVersionCheck() throws Exception {
        HiCadImportRepository imports = mock(HiCadImportRepository.class);
        EinkaufBedarfService bedarfe = mock(EinkaufBedarfService.class);
        HiCadImportService service = new HiCadImportService(imports, bedarfe, mock(EinkaufDateiService.class));
        UUID key = UUID.randomUUID();
        var request = new HiCadImportDto.Uebernahme(2L,
                List.of(new HiCadImportDto.ZeilenAuswahl(3, null, null)), false, key);
        HiCadImport imported = mock(HiCadImport.class);
        when(imports.findById(5L)).thenReturn(Optional.of(imported));
        when(imported.getVersion()).thenReturn(3L);
        when(imported.getIdempotenzKey()).thenReturn(key.toString());
        when(imported.getPayloadHash()).thenReturn(hash(request.zeilen().toString()));
        when(imported.getResultJson()).thenReturn("[]");

        var result = service.uebernehmen(5L, request, 4L);

        assertEquals(List.of(), result);
        verify(bedarfe, never()).anlegen(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static byte[] workbook(String quantity) throws Exception {
        return workbook(quantity, "S355");
    }

    private static byte[] replaceWorksheetWithHighlyCompressedXml(byte[] workbook) throws Exception {
        try (var input = new ZipInputStream(new ByteArrayInputStream(workbook));
                var output = new ByteArrayOutputStream(); var zip = new ZipOutputStream(output)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                byte[] content = input.readAllBytes();
                zip.putNextEntry(new ZipEntry(entry.getName()));
                if ("xl/worksheets/sheet1.xml".equals(entry.getName())) {
                    String xml = "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData><row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>"
                            + "A".repeat(11 * 1024 * 1024)
                            + "</t></is></c></row></sheetData></worksheet>";
                    zip.write(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } else {
                    zip.write(content);
                }
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        }
    }

    private static byte[] workbook(String quantity, String steel) throws Exception {
        return workbook(quantity, steel, null);
    }

    private static byte[] workbook(String quantity, String steel, byte[] embeddedImage) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Teile");
            var row = sheet.createRow(0);
            String[] header = {"Interne Nummer", "Zeichnungsnummer", "Revision", "Bezeichnung", "Werkstoff",
                    "Abmessung", "Menge", "Einheit", "Stückzahl", "Einzellänge mm", "Winkel links", "Winkel rechts"};
            for (int i = 0; i < header.length; i++) row.createCell(i).setCellValue(header[i]);
            row = sheet.createRow(1);
            String[] data = {"P-17", "Z-42", "B", "Konsole", steel, "80x40", quantity, "m", "2", "6250", "45", "45"};
            for (int i = 0; i < data.length; i++) row.createCell(i).setCellValue(data[i]);
            if (embeddedImage != null) {
                int picture = workbook.addPicture(embeddedImage, org.apache.poi.ss.usermodel.Workbook.PICTURE_TYPE_PNG);
                var drawing = sheet.createDrawingPatriarch();
                var anchor = workbook.getCreationHelper().createClientAnchor();
                anchor.setRow1(1);
                anchor.setCol1(12);
                drawing.createPicture(anchor, picture);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] png() throws Exception {
        var image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        try (var output = new ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    private static String hash(String text) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
