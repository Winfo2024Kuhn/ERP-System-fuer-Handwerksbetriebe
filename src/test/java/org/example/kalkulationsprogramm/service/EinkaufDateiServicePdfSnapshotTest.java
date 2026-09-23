package org.example.kalkulationsprogramm.service;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufDatei;
import org.example.kalkulationsprogramm.repository.EinkaufAnlageVersionRepository;
import org.example.kalkulationsprogramm.repository.EinkaufBedarfRepository;
import org.example.kalkulationsprogramm.repository.EinkaufDateiRepository;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufDateiService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EinkaufDateiServicePdfSnapshotTest {
    @TempDir Path temp;

    @Test
    void speichertUnveraenderlichenPDFSnapshotEinmalUndLiefertExaktDieselbenBytes() {
        var files = mock(EinkaufDateiRepository.class);
        var versions = mock(EinkaufAnlageVersionRepository.class);
        var needs = mock(EinkaufBedarfRepository.class);
        var storedRef = new AtomicReference<EinkaufDatei>();
        when(files.findBySha256(any())).thenAnswer(call -> Optional.ofNullable(storedRef.get()));
        when(files.findById(42L)).thenAnswer(call -> Optional.ofNullable(storedRef.get()));
        when(files.save(any())).thenAnswer(call -> {
            EinkaufDatei file = call.getArgument(0);
            ReflectionTestUtils.setField(file, "id", 42L);
            storedRef.set(file);
            return file;
        });
        var service = new EinkaufDateiService(files, versions, needs, temp.toString());
        byte[] pdf = "%PDF-1.7\nDummy test pdf\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        var first = service.speicherePdfSnapshot(pdf, "PA-2026-00042-Anfrage-1.pdf");
        var second = service.speicherePdfSnapshot(pdf, "PA-2026-00042-Anfrage-1.pdf");

        assertEquals(first.dateiId(), second.dateiId());
        assertArrayEquals(pdf, service.ladePdfSnapshotBytes(first.dateiId()));
        verify(files, times(1)).save(any());
    }

    @Test
    void lehntDateienAbDieKeinePDFBytesSind() {
        var service = new EinkaufDateiService(mock(EinkaufDateiRepository.class),
                mock(EinkaufAnlageVersionRepository.class), mock(EinkaufBedarfRepository.class), temp.toString());
        assertThrows(IllegalArgumentException.class, () -> service.speicherePdfSnapshot(new byte[] {1, 2, 3}, "preview.pdf"));
    }
    @Test
    void externerNachweisBrauchtLesbareOriginalbytesUndUnbenutztenLieferantenbeleg() throws Exception {
        var files = mock(EinkaufDateiRepository.class);
        var docs = mock(org.example.kalkulationsprogramm.repository.LieferantDokumentRepository.class);
        var service = new EinkaufDateiService(files, mock(EinkaufAnlageVersionRepository.class), mock(EinkaufBedarfRepository.class),
                mock(org.example.kalkulationsprogramm.repository.EmailAttachmentRepository.class), docs, temp.toString(), temp.toString());
        byte[] bytes = "%PDF-Dummy Versandbestätigung".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        var file = new EinkaufDatei(hash, "dummy-proof", "Beleg.pdf", "application/pdf", bytes.length, null, 42L);
        ReflectionTestUtils.setField(file, "id", 21L);
        when(files.findById(21L)).thenReturn(Optional.of(file));
        var supplier = new org.example.kalkulationsprogramm.domain.Lieferanten(); supplier.setId(7L);
        var doc = new org.example.kalkulationsprogramm.domain.LieferantDokument();doc.setId(42L);doc.setLieferant(supplier);
        doc.setTyp(org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.SONSTIG);
        when(docs.sperreEinkaufsbeleg(42L)).thenReturn(Optional.of(doc));
        assertThrows(org.example.kalkulationsprogramm.exception.NotFoundException.class, () -> service.pruefeExternenVersandbeleg(999L,7L,1L));
        assertThrows(IllegalArgumentException.class, () -> service.pruefeExternenVersandbeleg(21L,8L,1L));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.pruefeExternenVersandbeleg(21L,7L,1L));
        java.nio.file.Files.createDirectories(temp.resolve("einkauf"));
        java.nio.file.Files.write(temp.resolve("einkauf/dummy-proof"), bytes);
        assertEquals(hash, service.pruefeExternenVersandbeleg(21L,7L,1L).get("sha256"));
        assertEquals(1L, doc.getEinkaufBestellungId());
        assertThrows(IllegalArgumentException.class, () -> service.pruefeExternenVersandbeleg(21L,7L,2L));
        assertThrows(IllegalArgumentException.class, () -> service.pruefeExternenVersandbeleg(21L,7L,1L));
    }

}
