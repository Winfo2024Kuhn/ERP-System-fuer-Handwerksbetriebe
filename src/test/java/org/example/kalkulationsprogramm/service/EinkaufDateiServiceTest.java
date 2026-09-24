package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;

import java.nio.file.Path;
import java.util.Optional;
import java.util.HashMap;
import java.util.List;

import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBedarf;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufDatei;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufAnlageVersion;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.repository.EinkaufAnlageVersionRepository;
import org.example.kalkulationsprogramm.repository.EinkaufBedarfRepository;
import org.example.kalkulationsprogramm.repository.EinkaufDateiRepository;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufDateiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class EinkaufDateiServiceTest {
    @TempDir Path uploadRoot;
    private EinkaufDateiService service;

    @BeforeEach
    void setUp() {
        EinkaufDateiRepository files = mock(EinkaufDateiRepository.class);
        EinkaufAnlageVersionRepository versions = mock(EinkaufAnlageVersionRepository.class);
        EinkaufBedarfRepository needs = mock(EinkaufBedarfRepository.class);
        when(needs.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(EinkaufBedarf.class)));
        service = new EinkaufDateiService(files, versions, needs, uploadRoot.toString());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"../../dummy.pdf", "dummy.exe.pdf", "dummy.html", "dummy.js", "dummy\\file.pdf"})
    void lieferantenUploadWeistUnsichereDateinamenZurueck(String name) {
        var supplier = mock(org.example.kalkulationsprogramm.domain.Lieferanten.class);
        assertThrows(IllegalArgumentException.class, () -> service.ladeLieferantenbelegHoch(supplier,
                org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.SONSTIG,
                new MockMultipartFile("datei", name, "application/pdf", "%PDF-1.7 Dummy".getBytes())));
    }

    @Test void lieferantenUploadPrueftInhaltMimeUndLeereDatei() {
        var supplier = mock(org.example.kalkulationsprogramm.domain.Lieferanten.class);
        for (var upload : List.of(new MockMultipartFile("datei", "dummy.pdf", "application/pdf", "MZ-executable".getBytes()),
                new MockMultipartFile("datei", "dummy.pdf", "text/html", "%PDF-1.7 Dummy".getBytes()),
                new MockMultipartFile("datei", "dummy.pdf", "application/pdf", new byte[0]))) {
            assertThrows(IllegalArgumentException.class, () -> service.ladeLieferantenbelegHoch(supplier,
                    org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.SONSTIG, upload));
        }
    }

    @Test
    void rejectsPathTraversalEvenWhenTheExtensionLooksLikePdf() {
        MockMultipartFile pdf = new MockMultipartFile("datei", "../../angebot.pdf", "application/pdf",
                "%PDF-1.7\nDummy PDF".getBytes());
        assertThrows(IllegalArgumentException.class, () -> service.hochladen(1L, pdf, "A", 1L));
    }

    @Test
    void rejectsDoubleExtensionsAndExecutablesMasqueradingAsPdf() {
        MockMultipartFile executable = new MockMultipartFile("datei", "angebot.exe.pdf", "application/pdf",
                "MZ\u0000\u0000".getBytes());
        assertThrows(IllegalArgumentException.class, () -> service.hochladen(1L, executable, "A", 1L));
    }

    @Test
    void doesNotAllowReplacingAnExistingRevisionAfterItWasSent() {
        EinkaufBedarfRepository needs = mock(EinkaufBedarfRepository.class);
        when(needs.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(EinkaufBedarf.class)));
        EinkaufAnlageVersionRepository versions = mock(EinkaufAnlageVersionRepository.class);
        when(versions.findByBedarfIdAndRevision(1L, "A")).thenReturn(Optional.of(mock(EinkaufAnlageVersion.class)));
        var service = new EinkaufDateiService(mock(EinkaufDateiRepository.class), versions, needs, uploadRoot.toString());

        var error = org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.hochladen(1L, pdf("%PDF-1.7\nChanged content".getBytes(), "angebot.pdf"), "A", 1L));

        org.junit.jupiter.api.Assertions.assertEquals(409, error.getStatusCode().value());
        verify(versions, never()).save(any());
    }

    @Test
    void storesEqualBytesOnlyOnceAcrossDifferentRevisions() {
        EinkaufDateiRepository files = mock(EinkaufDateiRepository.class);
        EinkaufAnlageVersionRepository versions = mock(EinkaufAnlageVersionRepository.class);
        EinkaufBedarfRepository needs = mock(EinkaufBedarfRepository.class);
        when(needs.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(EinkaufBedarf.class)));
        when(versions.findByBedarfIdAndRevision(1L, "A")).thenReturn(Optional.empty());
        when(versions.findByBedarfIdAndRevision(1L, "B")).thenReturn(Optional.empty());
        var byHash = new HashMap<String, EinkaufDatei>();
        when(files.sperreBySha256(anyString())).thenAnswer(call -> Optional.ofNullable(byHash.get(call.getArgument(0))));
        when(files.save(any())).thenAnswer(call -> {
            EinkaufDatei value = call.getArgument(0);
            byHash.put(value.getSha256(), value);
            return value;
        });
        when(versions.save(any())).thenAnswer(call -> call.getArgument(0));
        var service = new EinkaufDateiService(files, versions, needs, uploadRoot.toString());
        byte[] bytes = "%PDF-1.7\nSame dummy file".getBytes();

        service.hochladen(1L, pdf(bytes, "angebot.pdf"), "A", 1L);
        service.hochladen(1L, pdf(bytes, "angebot.pdf"), "B", 1L);

        verify(files).save(any());
        org.junit.jupiter.api.Assertions.assertEquals(1, uploadRoot.resolve("einkauf").toFile().list().length);
    }

    @Test
    void entferntEineNeuAngelegteDateiBeiTransaktionsrollback() throws Exception {
        EinkaufDateiRepository files = mock(EinkaufDateiRepository.class);
        EinkaufAnlageVersionRepository versions = mock(EinkaufAnlageVersionRepository.class);
        EinkaufBedarfRepository needs = mock(EinkaufBedarfRepository.class);
        when(needs.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(EinkaufBedarf.class)));
        when(files.sperreBySha256(anyString())).thenReturn(Optional.empty());
        when(files.save(any())).thenAnswer(call -> call.getArgument(0));
        when(versions.findByBedarfIdAndRevision(1L, "R1")).thenReturn(Optional.empty());
        when(versions.save(any())).thenAnswer(call -> call.getArgument(0));
        var service = new EinkaufDateiService(files, versions, needs, uploadRoot.toString());
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.hochladen(1L, pdf("%PDF-1.7\nRollback-Datei".getBytes(), "zeichnung.pdf"), "R1", 7L);
            org.junit.jupiter.api.Assertions.assertEquals(1, uploadRoot.resolve("einkauf").toFile().list().length);
        } finally {
            var synchronisierungen = org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations();
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
            synchronisierungen.forEach(synchronisierung -> synchronisierung.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
        }
        org.junit.jupiter.api.Assertions.assertEquals(0, uploadRoot.resolve("einkauf").toFile().list().length);
    }

    @Test
    void erhaeltDeduplizierteDateiBeiTransaktionsrollback() throws Exception {
        byte[] bytes = "%PDF-1.7\nGemeinsam genutzte Datei".getBytes();
        String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        Path dateipfad = uploadRoot.resolve("einkauf").resolve("bestehende-datei");
        java.nio.file.Files.createDirectories(dateipfad.getParent()); java.nio.file.Files.write(dateipfad, bytes);
        EinkaufDatei bestehendeDatei = new EinkaufDatei(hash, "bestehende-datei", "bestehend.pdf", "application/pdf", bytes.length);
        EinkaufDateiRepository files = mock(EinkaufDateiRepository.class);
        when(files.sperreBySha256(hash)).thenReturn(Optional.of(bestehendeDatei));
        EinkaufAnlageVersionRepository versions = mock(EinkaufAnlageVersionRepository.class);
        when(versions.findByBedarfIdAndRevision(1L, "R2")).thenReturn(Optional.empty());
        when(versions.save(any())).thenAnswer(call -> call.getArgument(0));
        EinkaufBedarfRepository needs = mock(EinkaufBedarfRepository.class);
        when(needs.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(EinkaufBedarf.class)));
        var service = new EinkaufDateiService(files, versions, needs, uploadRoot.toString());
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try { service.hochladen(1L, pdf(bytes, "gleich.pdf"), "R2", 7L); }
        finally {
            var synchronisierungen = org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations();
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
            synchronisierungen.forEach(synchronisierung -> synchronisierung.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
        }
        org.junit.jupiter.api.Assertions.assertTrue(java.nio.file.Files.exists(dateipfad));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @org.junit.jupiter.params.provider.ValueSource(longs = 7L)
    void explicitReuploadRepairsDedupedHistoricalReferenceWhoseSourceIsGone(Long supplierDocumentId) throws Exception {
        byte[] bytes = "%PDF-1.7\nRecovered from explicit upload".getBytes();
        String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        EinkaufDatei historicalReference = new EinkaufDatei(hash, null, "historisch.pdf", "application/pdf",
                bytes.length, 55L, supplierDocumentId);
        EinkaufDateiRepository files = mock(EinkaufDateiRepository.class);
        when(files.sperreBySha256(hash)).thenReturn(Optional.of(historicalReference));
        when(files.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        EinkaufAnlageVersionRepository versions = mock(EinkaufAnlageVersionRepository.class);
        when(versions.findByBedarfIdAndRevision(1L, "Reupload")).thenReturn(Optional.empty());
        when(versions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        EinkaufBedarfRepository needs = mock(EinkaufBedarfRepository.class);
        when(needs.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(EinkaufBedarf.class)));
        var attachments = mock(org.example.kalkulationsprogramm.repository.EmailAttachmentRepository.class);
        when(attachments.findById(55L)).thenReturn(Optional.empty());
        var service = new EinkaufDateiService(files, versions, needs, attachments,
                mock(org.example.kalkulationsprogramm.repository.LieferantDokumentRepository.class),
                uploadRoot.toString(), uploadRoot.resolve("email").toString());

        service.hochladen(1L, pdf(bytes, "erneuert.pdf"), "Reupload", 4L);

        verify(files).save(argThat(file -> file == historicalReference && file.getGespeicherterName() != null
                && file.getEmailAttachmentId() == null && java.util.Objects.equals(file.getLieferantDokumentId(), supplierDocumentId)));
        org.junit.jupiter.api.Assertions.assertArrayEquals(bytes, java.nio.file.Files.readAllBytes(
                uploadRoot.resolve("einkauf").resolve(historicalReference.getGespeicherterName())));
    }

    @Test
    void persistsHiCadPreviewImagesAsDeduplicatedFilesAndReturnsImageMetadata() throws Exception {
        EinkaufDateiRepository files = mock(EinkaufDateiRepository.class);
        var byHash = new HashMap<String, EinkaufDatei>();
        when(files.findBySha256(anyString())).thenAnswer(call -> Optional.ofNullable(byHash.get(call.getArgument(0))));
        when(files.save(any())).thenAnswer(call -> {
            EinkaufDatei value = call.getArgument(0);
            byHash.put(value.getSha256(), value);
            return value;
        });
        var service = new EinkaufDateiService(files, mock(EinkaufAnlageVersionRepository.class),
                mock(EinkaufBedarfRepository.class), uploadRoot.toString());
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jqGQAAAAASUVORK5CYII=");

        var first = service.speichereImportBild("teil.png", "image/png", png);
        var second = service.speichereImportBild("anderer-name.png", "image/png", png);

        org.junit.jupiter.api.Assertions.assertEquals(first.byteAnzahl(), second.byteAnzahl());
        org.junit.jupiter.api.Assertions.assertEquals("image/png", first.mimeTyp());
        org.junit.jupiter.api.Assertions.assertEquals(1, byHash.size());
        String storedName = byHash.values().iterator().next().getGespeicherterName();
        org.junit.jupiter.api.Assertions.assertArrayEquals(png,
                java.nio.file.Files.readAllBytes(uploadRoot.resolve("einkauf").resolve(storedName)));
    }

    @Test
    void refusesAFileThatHasGoneMissingBeforeMailAssembly() {
        EinkaufDatei file = mock(EinkaufDatei.class);
        when(file.getGespeicherterName()).thenReturn("missing-file-uuid");
        EinkaufAnlageVersion version = mock(EinkaufAnlageVersion.class);
        when(version.getDatei()).thenReturn(file);
        when(version.isFreigegeben()).thenReturn(true);
        EinkaufAnlageVersionRepository versions = mock(EinkaufAnlageVersionRepository.class);
        when(versions.findAllByIdIn(List.of(7L))).thenReturn(List.of(version));
        var service = new EinkaufDateiService(mock(EinkaufDateiRepository.class), versions,
                mock(EinkaufBedarfRepository.class), uploadRoot.toString());

        var error = org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.ladeVersandanlagen(List.of(7L)));
        org.junit.jupiter.api.Assertions.assertEquals(409, error.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertEquals("Anlage fehlt, bitte neu hochladen", error.getReason());
    }

    @Test
    void positiveVersionIdCannotPassTheSendGateWhenItBelongsToAnotherDemand() {
        EinkaufBedarf foreignNeed = mock(EinkaufBedarf.class);
        when(foreignNeed.getId()).thenReturn(99L);
        EinkaufDatei file = mock(EinkaufDatei.class);
        when(file.getGespeicherterName()).thenReturn("some-file");
        EinkaufAnlageVersion version = mock(EinkaufAnlageVersion.class);
        when(version.getBedarf()).thenReturn(foreignNeed);
        when(version.getDatei()).thenReturn(file);
        when(version.isFreigegeben()).thenReturn(true);
        EinkaufAnlageVersionRepository versions = mock(EinkaufAnlageVersionRepository.class);
        when(versions.findAllByIdIn(List.of(7L))).thenReturn(List.of(version));
        var service = new EinkaufDateiService(mock(EinkaufDateiRepository.class), versions,
                mock(EinkaufBedarfRepository.class), uploadRoot.toString());

        var error = org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.pruefeFreigegebeneBedarfsanlagen(1L, List.of(7L)));
        org.junit.jupiter.api.Assertions.assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void linksExistingEmailAttachmentByReferenceWithoutCopyingBytes() throws Exception {
        Path emailRoot = uploadRoot.resolve("email-source");
        java.nio.file.Files.createDirectories(emailRoot);
        byte[] pdf = "%PDF-1.7\nExisting mail file".getBytes();
        java.nio.file.Files.write(emailRoot.resolve("source.pdf"), pdf);
        Email email = mock(Email.class);
        when(email.getId()).thenReturn(14L);
        EmailAttachment attachment = mock(EmailAttachment.class);
        when(attachment.getId()).thenReturn(55L);
        when(attachment.getOriginalFilename()).thenReturn("source.pdf");
        when(attachment.getStoredFilename()).thenReturn("source.pdf");
        when(attachment.getMimeType()).thenReturn("application/pdf");
        when(attachment.getSizeBytes()).thenReturn((long) pdf.length);
        when(attachment.getEmail()).thenReturn(email);
        var attachments = mock(org.example.kalkulationsprogramm.repository.EmailAttachmentRepository.class);
        when(attachments.findById(55L)).thenReturn(Optional.of(attachment));
        var files = mock(EinkaufDateiRepository.class);
        when(files.findBySha256(anyString())).thenReturn(Optional.empty());
        when(files.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var versions = mock(EinkaufAnlageVersionRepository.class);
        when(versions.findByBedarfIdAndRevision(1L, "Mail-1")).thenReturn(Optional.empty());
        when(versions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var demands = mock(EinkaufBedarfRepository.class);
        when(demands.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(EinkaufBedarf.class)));
        var service = new EinkaufDateiService(files, versions, demands, attachments,
                mock(org.example.kalkulationsprogramm.repository.LieferantDokumentRepository.class),
                uploadRoot.toString(), emailRoot.toString());
        service.nutzeEmailAnlage(1L, 55L, "Mail-1", 4L);

        verify(files).save(argThat(file -> file.getGespeicherterName() == null
                && Long.valueOf(55L).equals(file.getEmailAttachmentId())
                && file.getByteAnzahl() == pdf.length));
        org.junit.jupiter.api.Assertions.assertFalse(java.nio.file.Files.exists(uploadRoot.resolve("einkauf")));
    }

    @Test
    void reportsMissingHistoricalEmailFileAsConflict() throws Exception {
        Path emailRoot = uploadRoot.resolve("email-empty");
        java.nio.file.Files.createDirectories(emailRoot);
        Email email = mock(Email.class);
        when(email.getId()).thenReturn(14L);
        EmailAttachment attachment = mock(EmailAttachment.class);
        when(attachment.getId()).thenReturn(55L);
        when(attachment.getOriginalFilename()).thenReturn("source.pdf");
        when(attachment.getStoredFilename()).thenReturn("missing.pdf");
        when(attachment.getMimeType()).thenReturn("application/pdf");
        when(attachment.getEmail()).thenReturn(email);
        var attachments = mock(org.example.kalkulationsprogramm.repository.EmailAttachmentRepository.class);
        when(attachments.findById(55L)).thenReturn(Optional.of(attachment));
        var demands = mock(EinkaufBedarfRepository.class);
        when(demands.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(EinkaufBedarf.class)));
        var service = new EinkaufDateiService(mock(EinkaufDateiRepository.class),
                mock(EinkaufAnlageVersionRepository.class), demands, attachments,
                mock(org.example.kalkulationsprogramm.repository.LieferantDokumentRepository.class),
                uploadRoot.toString(), emailRoot.toString());

        var error = org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.nutzeEmailAnlage(1L, 55L, "Mail-1", 4L));
        org.junit.jupiter.api.Assertions.assertEquals(409, error.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertEquals("Anlage fehlt, bitte neu hochladen", error.getReason());
    }

    private static MockMultipartFile pdf(byte[] bytes, String filename) {
        return new MockMultipartFile("datei", filename, "application/pdf", bytes);
    }

    @Test
    void metadatenEnthaltenDateinameRevisionUndFreigabeUndFehlendeIdsScheitern() {
        var versions = mock(EinkaufAnlageVersionRepository.class);
        var needs = mock(EinkaufBedarfRepository.class);
        var need = mock(EinkaufBedarf.class); when(need.getId()).thenReturn(1L);
        var file = new EinkaufDatei("dummy", "dummy", "Profil.pdf", "application/pdf", 20);
        org.springframework.test.util.ReflectionTestUtils.setField(file, "id", 8L);
        var version = new EinkaufAnlageVersion(need, file, "B");
        org.springframework.test.util.ReflectionTestUtils.setField(version, "id", 18L);
        version.setFreigegeben(true);
        when(needs.existsById(1L)).thenReturn(true);
        when(versions.findByBedarfIdOrderByIdAsc(1L)).thenReturn(List.of(version));
        when(versions.findAllByIdIn(List.of(18L))).thenReturn(List.of(version));
        var local = new EinkaufDateiService(mock(EinkaufDateiRepository.class), versions, needs, uploadRoot.toString());
        var dto = local.auflisten(1L).getFirst();
        org.junit.jupiter.api.Assertions.assertEquals("Profil.pdf", dto.dateiname());
        org.junit.jupiter.api.Assertions.assertEquals("B", dto.revision());
        org.junit.jupiter.api.Assertions.assertTrue(dto.freigegeben());
        org.junit.jupiter.api.Assertions.assertEquals(List.of(dto), local.metadaten(List.of(18L)));
        assertThrows(org.example.kalkulationsprogramm.exception.NotFoundException.class, () -> local.metadaten(List.of(999L)));
        assertThrows(org.example.kalkulationsprogramm.exception.NotFoundException.class, () -> local.auflisten(999L));
    }
}
