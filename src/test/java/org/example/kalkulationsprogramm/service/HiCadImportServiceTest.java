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
        when(imports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
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
    void rejectsExternalRelationshipsStoredInsideCompressedXlsxEntries() throws Exception {
        HiCadImportRepository imports = mock(HiCadImportRepository.class);
        when(imports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        HiCadImportService service = new HiCadImportService(imports,
                mock(EinkaufBedarfService.class), mock(EinkaufDateiService.class));
        String relationship = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"external1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink\" Target=\"https://example.invalid/parts.xlsx\" TargetMode=\"External\"/></Relationships>";
        byte[] withExternalLink = zipEntry(workbook("12,5"), "xl/worksheets/_rels/sheet1.xml.rels",
                relationship.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        var file = new MockMultipartFile("file", "parts.xlsx", "application/octet-stream", withExternalLink);
        assertThrows(IllegalArgumentException.class, () -> service.vorschau(17L, file, null, 4L));
        verify(imports, never()).save(any());
    }

    @Test
    void rejectsLegacyXlsContainingVbaProjectStream() throws Exception {
        HiCadImportRepository imports = mock(HiCadImportRepository.class);
        when(imports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        HiCadImportService service = new HiCadImportService(imports,
                mock(EinkaufBedarfService.class), mock(EinkaufDateiService.class));
        byte[] macroWorkbook;
        try (var workbook = new org.apache.poi.hssf.usermodel.HSSFWorkbook();
                var workbookBytes = new ByteArrayOutputStream()) {
            workbook.createSheet("Teile").createRow(0).createCell(0).setCellValue("Menge");
            workbook.createSheet("Temp");
            workbook.write(workbookBytes);
            try (var compound = new org.apache.poi.poifs.filesystem.POIFSFileSystem(
                    new ByteArrayInputStream(workbookBytes.toByteArray())); var output = new ByteArrayOutputStream()) {
                compound.getRoot().createDocument("_VBA_PROJECT_CUR", new ByteArrayInputStream(new byte[] {1, 2, 3}));
                compound.writeFilesystem(output);
                macroWorkbook = output.toByteArray();
            }
        }

        var file = new MockMultipartFile("file", "macro.xls", "application/vnd.ms-excel", macroWorkbook);
        assertThrows(IllegalArgumentException.class, () -> service.vorschau(17L, file, null, 4L));
        verify(imports, never()).save(any());
    }

    @Test
    void rejectsExternalWorkbookReferencesEncodedAsLegacyXlsRecords() throws Exception {
        HiCadImportRepository imports = mock(HiCadImportRepository.class);
        when(imports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        HiCadImportService service = new HiCadImportService(imports,
                mock(EinkaufBedarfService.class), mock(EinkaufDateiService.class));
        byte[] externalBook;
        try (var workbook = new org.apache.poi.hssf.usermodel.HSSFWorkbook();
                var linkedWorkbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            workbook.createSheet("Teile").createRow(0).createCell(0).setCellValue("Menge");
            linkedWorkbook.createSheet("Extern");
            workbook.linkExternalWorkbook("https://example.invalid/external.xlsx", linkedWorkbook);
            workbook.write(output);
            externalBook = output.toByteArray();
        }

        var file = new MockMultipartFile("file", "linked.xls", "application/vnd.ms-excel", externalBook);
        assertThrows(IllegalArgumentException.class, () -> service.vorschau(17L, file, null, 4L));
        verify(imports, never()).save(any());
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
        when(imports.findByIdForUpdate(5L)).thenReturn(Optional.of(imported));
        when(imported.getProjektId()).thenReturn(17L);
        when(imported.getImportInstanz()).thenReturn("12345678-1234-1234-1234-123456789abc");
        when(imported.isDuplikat()).thenReturn(false);
        when(imported.getZeilen()).thenReturn(List.of(first, second));
        when(first.getZeilennummer()).thenReturn(2);
        when(second.getZeilennummer()).thenReturn(3);
        when(second.getUebernommeneMenge()).thenReturn(java.math.BigDecimal.ZERO);
        when(second.getBildDateiIdsJson()).thenReturn("[7]");
        String snapshot = "{\"art\":\"ZEICHNUNGSTEIL\",\"interneReferenz\":\"P-17\",\"zeichnungsnummer\":\"Z-17\",\"zeichnungsrevision\":\"A\",\"bezeichnung\":\"Konsole\",\"basis\":{\"menge\":1,\"einheit\":\"STUECK\"},\"dokumente\":[],\"anlageVersionIds\":[7]}";
        when(first.getSnapshotJson()).thenReturn(snapshot);
        when(second.getSnapshotJson()).thenReturn(snapshot);
        var expected = new EinkaufBedarfDto.Response(99L, 1L, null, null, null, false, null);
        when(bedarfe.anlegen(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(4L))).thenReturn(expected);
        when(bedarfe.aktualisieren(org.mockito.ArgumentMatchers.eq(99L), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(4L))).thenReturn(expected);
        EinkaufDateiService fileService = mock(EinkaufDateiService.class);
        when(fileService.anhaengenImportBild(eq(99L), eq(7L), anyString(), eq(4L)))
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
        when(imports.findByIdForUpdate(5L)).thenReturn(Optional.of(imported));
        when(imported.getVersion()).thenReturn(3L);
        when(imported.getIdempotenzKey()).thenReturn(key.toString());
        when(imported.getPayloadHash()).thenReturn(hash("duplikatBewusst=false&zeilen=" + request.zeilen()));
        when(imported.getResultJson()).thenReturn("[]");

        var result = service.uebernehmen(5L, request, 4L);

        assertEquals(List.of(), result);
        var changedPayload = new HiCadImportDto.Uebernahme(2L,
                List.of(new HiCadImportDto.ZeilenAuswahl(3, new java.math.BigDecimal("1"), null)), false, key);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.uebernehmen(5L, changedPayload, 4L));
        verify(bedarfe, never()).anlegen(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void partialTransferPersistsConsumedQuantityAllowsRemainderAndReplaysEveryRequestKey() {
        HiCadImportRepository imports = mock(HiCadImportRepository.class);
        EinkaufBedarfService bedarfe = mock(EinkaufBedarfService.class);
        EinkaufDateiService files = mock(EinkaufDateiService.class);
        HiCadImport imported = new HiCadImport(17L, "a".repeat(64), 4L, false);
        org.springframework.test.util.ReflectionTestUtils.setField(imported, "id", 5L);
        org.springframework.test.util.ReflectionTestUtils.setField(imported, "version", 0L);
        HiCadImportZeile row = new HiCadImportZeile(3, "HiCAD row", "{\"art\":\"ZEICHNUNGSTEIL\",\"interneReferenz\":\"P-17\",\"zeichnungsnummer\":\"Z-17\",\"zeichnungsrevision\":\"A\",\"bezeichnung\":\"Konsole\",\"basis\":{\"menge\":10,\"einheit\":\"STUECK\",\"stueckzahl\":10},\"dokumente\":[],\"anlageVersionIds\":[]}");
        row.setBildDateiIdsJson("[7]");
        imported.addZeile(row);
        when(imports.findByIdForUpdate(5L)).thenReturn(Optional.of(imported));
        when(imports.findById(5L)).thenReturn(Optional.of(imported));
        when(imports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        java.util.concurrent.atomic.AtomicLong nextDemandId = new java.util.concurrent.atomic.AtomicLong(100L);
        when(bedarfe.anlegen(any(), eq(4L))).thenAnswer(invocation ->
                new EinkaufBedarfDto.Response(nextDemandId.getAndIncrement(), 0L, null, null, null, false, null));
        when(bedarfe.aktualisieren(org.mockito.ArgumentMatchers.anyLong(), any(), eq(4L))).thenAnswer(invocation ->
                new EinkaufBedarfDto.Response(invocation.getArgument(0), 1L, null, null, null, false, null));
        java.util.concurrent.atomic.AtomicLong nextVersionId = new java.util.concurrent.atomic.AtomicLong(200L);
        when(files.anhaengenImportBild(org.mockito.ArgumentMatchers.anyLong(), eq(7L), anyString(), eq(4L)))
                .thenAnswer(invocation -> new EinkaufDateiDto.AnlageDto(nextVersionId.getAndIncrement(), 70L,
                        invocation.getArgument(0), invocation.getArgument(2), "image.png", "image/png", 9L,
                        "hash", false, false, null));
        HiCadImportService service = new HiCadImportService(imports, bedarfe, files);
        UUID firstKey = UUID.randomUUID();
        var firstRequest = new HiCadImportDto.Uebernahme(0L,
                List.of(new HiCadImportDto.ZeilenAuswahl(3, new java.math.BigDecimal("4"), null, List.of(7L))),
                false, firstKey);

        var firstResult = service.uebernehmen(5L, firstRequest, 4L);

        assertEquals(false, row.isUebernommen());
        assertEquals(0, new java.math.BigDecimal("4").compareTo(
                (java.math.BigDecimal) org.springframework.test.util.ReflectionTestUtils.getField(row, "uebernommeneMenge")));
        var firstProgress = service.fortschritt(5L, 4L);
        assertEquals(0, new java.math.BigDecimal("6").compareTo(firstProgress.zeilen().get(0).verbleibendeMenge()));
        assertEquals(false, firstProgress.zeilen().get(0).vollstaendigUebernommen());
        assertEquals(firstResult, service.uebernehmen(5L, firstRequest, 4L));
        var excess = new HiCadImportDto.Uebernahme(0L,
                List.of(new HiCadImportDto.ZeilenAuswahl(3, new java.math.BigDecimal("7"), null, List.of(7L))),
                false, UUID.randomUUID());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.uebernehmen(5L, excess, 4L));
        verify(bedarfe, times(1)).anlegen(any(), eq(4L));

        UUID remainderKey = UUID.randomUUID();
        org.springframework.test.util.ReflectionTestUtils.setField(imported, "version", 1L);
        var remainder = new HiCadImportDto.Uebernahme(1L,
                List.of(new HiCadImportDto.ZeilenAuswahl(3, new java.math.BigDecimal("6"), null, List.of(7L))),
                false, remainderKey);
        var remainderResult = service.uebernehmen(5L, remainder, 4L);

        assertEquals(1, remainderResult.size());
        assertEquals(101L, remainderResult.get(0).id());
        assertEquals(true, row.isUebernommen());
        assertEquals(0, new java.math.BigDecimal("10").compareTo(
                (java.math.BigDecimal) org.springframework.test.util.ReflectionTestUtils.getField(row, "uebernommeneMenge")));
        var completedProgress = service.fortschritt(5L, 4L);
        assertEquals(0, completedProgress.zeilen().get(0).verbleibendeMenge().compareTo(java.math.BigDecimal.ZERO));
        assertEquals(true, completedProgress.zeilen().get(0).vollstaendigUebernommen());
        assertEquals(firstResult, service.uebernehmen(5L, firstRequest, 4L));
        assertEquals(remainderResult, service.uebernehmen(5L, remainder, 4L));
        verify(bedarfe, times(2)).anlegen(any(), eq(4L));
        org.mockito.ArgumentCaptor<String> revisions = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(files, times(2)).anhaengenImportBild(org.mockito.ArgumentMatchers.anyLong(), eq(7L), revisions.capture(), eq(4L));
        org.junit.jupiter.api.Assertions.assertNotEquals(revisions.getAllValues().get(0), revisions.getAllValues().get(1));
    }

    @Test
    void rejectsFractionalDigitsBeyondSixBeforeRoundingWholePieces() {
        HiCadImportRepository imports = mock(HiCadImportRepository.class);
        HiCadImport imported = new HiCadImport(17L, "d".repeat(64), 4L, false);
        org.springframework.test.util.ReflectionTestUtils.setField(imported, "id", 6L);
        org.springframework.test.util.ReflectionTestUtils.setField(imported, "version", 0L);
        HiCadImportZeile row = new HiCadImportZeile(3, "HiCAD row",
                "{\"art\":\"ZEICHNUNGSTEIL\",\"interneReferenz\":\"P-17\",\"zeichnungsnummer\":\"Z-17\",\"zeichnungsrevision\":\"A\",\"bezeichnung\":\"Konsole\",\"basis\":{\"menge\":1,\"einheit\":\"STUECK\",\"stueckzahl\":1},\"dokumente\":[],\"anlageVersionIds\":[]}");
        imported.addZeile(row);
        when(imports.findByIdForUpdate(6L)).thenReturn(Optional.of(imported));
        when(imports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        HiCadImportService service = new HiCadImportService(imports, mock(EinkaufBedarfService.class),
                mock(EinkaufDateiService.class));
        var request = new HiCadImportDto.Uebernahme(0L,
                List.of(new HiCadImportDto.ZeilenAuswahl(3, new java.math.BigDecimal("0.9999999"), null, List.of())),
                false, UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () -> service.uebernehmen(6L, request, 4L));

        row.setSnapshotJson("{\"art\":\"ZEICHNUNGSTEIL\",\"interneReferenz\":\"P-17\",\"zeichnungsnummer\":\"Z-17\",\"zeichnungsrevision\":\"A\",\"bezeichnung\":\"Konsole\",\"basis\":{\"menge\":1,\"einheit\":\"METER\"},\"dokumente\":[],\"anlageVersionIds\":[]}");
        row.setBildDateiIdsJson("[7]");
        EinkaufDateiService files = mock(EinkaufDateiService.class);
        when(files.anhaengenImportBild(org.mockito.ArgumentMatchers.anyLong(), eq(7L), anyString(), eq(4L)))
                .thenReturn(new EinkaufDateiDto.AnlageDto(700L, 70L, 71L, "HiCAD-test", "test.png", "image/png", 9L, "hash", false, false, null));
        when(imports.findByIdForUpdate(6L)).thenReturn(Optional.of(imported));
        when(imports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        EinkaufBedarfService needs = mock(EinkaufBedarfService.class);
        when(needs.anlegen(any(), eq(4L))).thenReturn(new EinkaufBedarfDto.Response(900L, 0L, null, null, null, false, null));
        when(needs.aktualisieren(org.mockito.ArgumentMatchers.anyLong(), any(), eq(4L)))
                .thenAnswer(invocation -> new EinkaufBedarfDto.Response(invocation.getArgument(0), 1L, null, null, null, false, null));
        HiCadImportService serviceWithAttachments = new HiCadImportService(imports, needs, files);
        var exactPrecision = new HiCadImportDto.Uebernahme(0L,
                List.of(new HiCadImportDto.ZeilenAuswahl(3, new java.math.BigDecimal("0.123456"), null, List.of(7L))),
                false, UUID.randomUUID());
        assertEquals(1, serviceWithAttachments.uebernehmen(6L, exactPrecision, 4L).size());
        assertEquals(new java.math.BigDecimal("0.123456"), row.getUebernommeneMenge());
    }

    @Test
    void secondPreviewOfIdenticalCompressedWorkbookIsExplicitlyMarkedAsDuplicate() throws Exception {
        HiCadImportRepository imports = mock(HiCadImportRepository.class);
        java.util.Map<String, HiCadImport> byHash = new java.util.HashMap<>();
        when(imports.findFirstByProjektIdAndDateiHashOrderByIdDesc(eq(17L), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(byHash.get(invocation.getArgument(1))));
        when(imports.save(any())).thenAnswer(invocation -> {
            HiCadImport saved = invocation.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 12L + byHash.size());
            byHash.put(saved.getDateiHash(), saved);
            return saved;
        });
        HiCadImportService service = new HiCadImportService(imports, mock(EinkaufBedarfService.class),
                mock(EinkaufDateiService.class));
        byte[] contents = workbook("12,5");

        var first = service.vorschau(17L, new MockMultipartFile("file", "parts.xlsx", "application/octet-stream", contents), null, 4L);
        var second = service.vorschau(17L, new MockMultipartFile("file", "parts.xlsx", "application/octet-stream", contents), null, 4L);

        assertEquals(false, first.dateiSchonImportiert());
        assertEquals(true, second.dateiSchonImportiert());
        org.junit.jupiter.api.Assertions.assertNotEquals(first.id(), second.id());
    }

    @Test
    void progressIsVisibleOnlyToTheImportOwner() {
        HiCadImportRepository imports = mock(HiCadImportRepository.class);
        HiCadImport imported = mock(HiCadImport.class);
        when(imports.findById(5L)).thenReturn(Optional.of(imported));
        when(imported.getAkteurId()).thenReturn(4L);
        HiCadImportService service = new HiCadImportService(imports, mock(EinkaufBedarfService.class),
                mock(EinkaufDateiService.class));

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.fortschritt(5L, 99L));
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

    private static byte[] zipEntry(byte[] archive, String name, byte[] value) throws Exception {
        try (var input = new ZipInputStream(new ByteArrayInputStream(archive)); var output = new ByteArrayOutputStream();
                var zip = new ZipOutputStream(output)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.getName().equals(name)) {
                    zip.putNextEntry(new ZipEntry(entry.getName()));
                    zip.write(input.readAllBytes());
                    zip.closeEntry();
                } else {
                    input.readAllBytes();
                }
            }
            zip.putNextEntry(new ZipEntry(name));
            zip.write(value);
            zip.closeEntry();
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
