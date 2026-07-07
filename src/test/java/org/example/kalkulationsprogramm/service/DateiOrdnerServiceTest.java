package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.service.SystemSettingsService.TestResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DateiOrdnerServiceTest {

    @Mock
    private SystemSettingsService settingsService;
    @Mock
    private SmbShareRunner smbShareRunner;
    @InjectMocks
    private DateiOrdnerService service;

    @TempDir
    Path tempDir;

    @Test
    void pruefeOrdner_leererPfadWirdAbgelehnt() {
        TestResult result = service.pruefeOrdner("   ");
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("Ordner-Pfad");
    }

    @Test
    void pruefeOrdner_pathTraversalWirdAbgelehnt() {
        TestResult result = service.pruefeOrdner("C:\\Zeichnungen\\..\\..\\Windows");
        assertThat(result.success()).isFalse();
    }

    @Test
    void pruefeOrdner_relativerPfadWirdAbgelehnt() {
        TestResult result = service.pruefeOrdner("zeichnungen/ordner");
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("vollständigen Pfad");
    }

    @Test
    void pruefeOrdner_ueberlangerPfadWirdAbgelehnt() {
        TestResult result = service.pruefeOrdner("C:\\" + "a".repeat(600));
        assertThat(result.success()).isFalse();
    }

    @Test
    void pruefeOrdner_gueltigerOrdnerWirdAngelegtUndIstBeschreibbar() {
        Path neu = tempDir.resolve("zeichnungen");
        TestResult result = service.pruefeOrdner(neu.toString());
        assertThat(result.success()).isTrue();
        assertThat(neu).exists();
    }

    @Test
    void speichereOrdner_gueltigWirdGespeichert() {
        Path neu = tempDir.resolve("dateien");
        TestResult result = service.speichereOrdner(neu.toString(), "\\\\server\\dateien");
        assertThat(result.success()).isTrue();
        verify(settingsService).saveDateiOrdner(neu.toString(), "\\\\server\\dateien");
    }

    @Test
    void speichereOrdner_ungueltigeNetworkUrlWirdAbgelehnt() {
        Path neu = tempDir.resolve("dateien");
        TestResult result = service.speichereOrdner(neu.toString(), "Z:\\kein-unc");
        assertThat(result.success()).isFalse();
        verify(settingsService, never()).saveDateiOrdner(anyString(), anyString());
    }

    @Test
    void speichereOrdner_ungueltigerPfadWirdNichtGespeichert() {
        TestResult result = service.speichereOrdner("..\\boese", "");
        assertThat(result.success()).isFalse();
        verify(settingsService, never()).saveDateiOrdner(anyString(), anyString());
    }

    @Test
    void gebeOrdnerFrei_nutztNurDenGespeichertenPfad() {
        when(settingsService.getDateiOrdnerPfad()).thenReturn(tempDir.toString());
        when(smbShareRunner.freigeben(any(Path.class), eq("ERP-Dateien")))
                .thenReturn(TestResult.success("Ordner freigegeben."));

        TestResult result = service.gebeOrdnerFrei();
        assertThat(result.success()).isTrue();
        verify(smbShareRunner).freigeben(eq(Path.of(tempDir.toString()).toAbsolutePath().normalize()), eq("ERP-Dateien"));
    }

    @Test
    void gebeOrdnerFrei_ohneGespeichertenPfadKommtFehler() {
        when(settingsService.getDateiOrdnerPfad()).thenReturn("");
        TestResult result = service.gebeOrdnerFrei();
        assertThat(result.success()).isFalse();
        verify(smbShareRunner, never()).freigeben(any(), anyString());
    }
}
