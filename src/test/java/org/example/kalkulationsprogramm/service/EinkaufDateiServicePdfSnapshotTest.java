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
}
