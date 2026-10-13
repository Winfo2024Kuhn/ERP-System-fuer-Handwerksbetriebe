package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.example.kalkulationsprogramm.service.LieferantDokumentPositionNachtrag;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LieferantDokumentPositionNachtragRunnerTest {

    private static final String VERSION = String.valueOf(LieferantDokumentPositionNachtragRunner.NACHTRAG_VERSION);

    @Mock private LieferantDokumentPositionNachtrag nachtrag;
    @Mock private SystemSettingsService einstellungen;

    private LieferantDokumentPositionNachtragRunner runner() {
        return new LieferantDokumentPositionNachtragRunner(nachtrag, einstellungen, Runnable::run);
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "kaputt", " " })
    void laeuftBeiAelterOderUnlesbarerVersionUndMerktSichDieNeue(String gespeichert) {
        when(einstellungen.get(LieferantDokumentPositionNachtragRunner.EINSTELLUNG, "0")).thenReturn(gespeichert);
        when(nachtrag.nachtragen()).thenReturn(7);

        assertThat(runner().tragNachFallsNoetig()).isTrue();

        verify(nachtrag).nachtragen();
        verify(einstellungen).save(eq(LieferantDokumentPositionNachtragRunner.EINSTELLUNG), eq(VERSION), anyString());
    }

    @Test
    void laeuftJeVersionNurEinmal() {
        when(einstellungen.get(LieferantDokumentPositionNachtragRunner.EINSTELLUNG, "0")).thenReturn(VERSION);

        assertThat(runner().tragNachFallsNoetig()).isFalse();

        verify(nachtrag, never()).nachtragen();
        verify(einstellungen, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void ohneGespeicherteVersionWirdNachgetragen() {
        when(einstellungen.get(LieferantDokumentPositionNachtragRunner.EINSTELLUNG, "0")).thenReturn(null);

        assertThat(runner().tragNachFallsNoetig()).isTrue();

        verify(nachtrag).nachtragen();
    }

    @Test
    void fehlerWerdenNurProtokolliertUndDieVersionBleibtOffen() {
        when(einstellungen.get(LieferantDokumentPositionNachtragRunner.EINSTELLUNG, "0")).thenReturn("0");
        when(nachtrag.nachtragen()).thenThrow(new IllegalStateException("DB weg"));

        assertThat(runner().tragNachFallsNoetig()).isFalse();

        verify(einstellungen, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void startLaeuftUeberDenHintergrundThread() {
        List<Runnable> aufgaben = new ArrayList<>();
        var runner = new LieferantDokumentPositionNachtragRunner(nachtrag, einstellungen, aufgaben::add);

        runner.nachDemStart();

        // Nichts blockiert den Start: die Arbeit liegt nur in der Warteschlange
        assertThat(aufgaben).hasSize(1);
        verify(nachtrag, never()).nachtragen();
    }
}
