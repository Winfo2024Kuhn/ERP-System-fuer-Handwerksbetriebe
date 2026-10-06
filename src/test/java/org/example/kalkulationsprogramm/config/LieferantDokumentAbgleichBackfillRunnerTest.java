package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LieferantDokumentAbgleichBackfillRunnerTest {

    private static final String VERSION = String.valueOf(LieferantDokumentAbgleichBackfillRunner.ABGLEICH_VERSION);

    @Mock private GeminiDokumentAnalyseService analyseService;
    @Mock private SystemSettingsService einstellungen;

    private LieferantDokumentAbgleichBackfillRunner runner() {
        return new LieferantDokumentAbgleichBackfillRunner(analyseService, einstellungen, Runnable::run);
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "1", "kaputt", " " })
    void laeuftBeiAelterOderUnlesbarerVersionUndMerktSichDieNeue(String gespeichert) {
        when(einstellungen.get(LieferantDokumentAbgleichBackfillRunner.EINSTELLUNG, "0")).thenReturn(gespeichert);
        when(analyseService.relinkAlleDokumente()).thenReturn(12);

        assertThat(runner().verknuepfeFallsNoetig()).isTrue();

        verify(analyseService).relinkAlleDokumente();
        verify(einstellungen).save(eq(LieferantDokumentAbgleichBackfillRunner.EINSTELLUNG), eq(VERSION), anyString());
    }

    @Test
    void laeuftJeVersionNurEinmal() {
        when(einstellungen.get(LieferantDokumentAbgleichBackfillRunner.EINSTELLUNG, "0")).thenReturn(VERSION);

        assertThat(runner().verknuepfeFallsNoetig()).isFalse();

        verify(analyseService, never()).relinkAlleDokumente();
        verify(einstellungen, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void fehlerWerdenNurProtokolliertUndDieVersionBleibtOffen() {
        when(einstellungen.get(LieferantDokumentAbgleichBackfillRunner.EINSTELLUNG, "0")).thenReturn("1");
        when(analyseService.relinkAlleDokumente()).thenThrow(new IllegalStateException("DB weg"));

        assertThat(runner().verknuepfeFallsNoetig()).isFalse();

        verify(einstellungen, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void startLaeuftUeberDenHintergrundThread() {
        List<Runnable> aufgaben = new ArrayList<>();
        var runner = new LieferantDokumentAbgleichBackfillRunner(analyseService, einstellungen, aufgaben::add);

        runner.nachDemStart();

        // Nichts blockiert den Start: die Arbeit liegt nur in der Warteschlange
        assertThat(aufgaben).hasSize(1);
        verify(analyseService, never()).relinkAlleDokumente();
        when(einstellungen.get(LieferantDokumentAbgleichBackfillRunner.EINSTELLUNG, "0")).thenReturn(VERSION);
        aufgaben.get(0).run();
        verify(analyseService, never()).relinkAlleDokumente();
    }
}
