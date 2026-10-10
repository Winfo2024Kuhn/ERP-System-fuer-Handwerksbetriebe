package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.example.kalkulationsprogramm.service.LieferantDokumentDuplikatService;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LieferantDokumentDuplikatBackfillRunnerTest {

    private static final String VERSION = String.valueOf(LieferantDokumentDuplikatBackfillRunner.BEREINIGUNG_VERSION);

    @Mock private LieferantDokumentDuplikatService duplikatService;
    @Mock private SystemSettingsService einstellungen;

    private static final LieferantDokumentDuplikatService.Ergebnis NICHTS =
            new LieferantDokumentDuplikatService.Ergebnis(0, 0, 0);

    private LieferantDokumentDuplikatBackfillRunner runner() {
        return new LieferantDokumentDuplikatBackfillRunner(duplikatService, einstellungen);
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "1", "kaputt", " " })
    void laeuftBeiAelterOderUnlesbarerVersionUndMerktSichDieNeue(String gespeichert) {
        when(einstellungen.get(LieferantDokumentDuplikatBackfillRunner.EINSTELLUNG, "0")).thenReturn(gespeichert);
        when(duplikatService.bereinigeDateiDuplikate())
                .thenReturn(new LieferantDokumentDuplikatService.Ergebnis(50, 52, 0));
        when(duplikatService.bereinigeInhaltsDuplikate()).thenReturn(NICHTS);

        assertThat(runner().bereinigeFallsNoetig()).isTrue();

        verify(duplikatService).bereinigeDateiDuplikate();
        verify(duplikatService).bereinigeInhaltsDuplikate();
        verify(einstellungen).save(eq(LieferantDokumentDuplikatBackfillRunner.EINSTELLUNG), eq(VERSION), anyString());
    }

    @Test
    void laeuftJeVersionNurEinmal() {
        when(einstellungen.get(LieferantDokumentDuplikatBackfillRunner.EINSTELLUNG, "0")).thenReturn(VERSION);

        assertThat(runner().bereinigeFallsNoetig()).isFalse();

        verify(duplikatService, never()).bereinigeDateiDuplikate();
        verify(einstellungen, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void fehlerWerdenNurProtokolliertUndDieVersionBleibtOffen() {
        when(einstellungen.get(LieferantDokumentDuplikatBackfillRunner.EINSTELLUNG, "0")).thenReturn("0");
        when(duplikatService.bereinigeDateiDuplikate()).thenThrow(new IllegalStateException("DB weg"));

        assertThat(runner().bereinigeFallsNoetig()).isFalse();

        verify(einstellungen, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void uebersprungeneGruppenLassenDieVersionOffenFuerDenNaechstenStart() {
        when(einstellungen.get(LieferantDokumentDuplikatBackfillRunner.EINSTELLUNG, "0")).thenReturn("0");
        when(duplikatService.bereinigeDateiDuplikate())
                .thenReturn(new LieferantDokumentDuplikatService.Ergebnis(3, 2, 1));
        when(duplikatService.bereinigeInhaltsDuplikate()).thenReturn(NICHTS);

        assertThat(runner().bereinigeFallsNoetig()).isTrue();

        verify(einstellungen, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void uebersprungeneInhaltsGruppenLassenDieVersionAuchOffen() {
        when(einstellungen.get(LieferantDokumentDuplikatBackfillRunner.EINSTELLUNG, "0")).thenReturn("1");
        when(duplikatService.bereinigeDateiDuplikate()).thenReturn(NICHTS);
        when(duplikatService.bereinigeInhaltsDuplikate())
                .thenReturn(new LieferantDokumentDuplikatService.Ergebnis(4, 3, 1));

        assertThat(runner().bereinigeFallsNoetig()).isTrue();

        verify(einstellungen, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void startBereinigtDirektVorDemNeuVerknuepfen() {
        when(einstellungen.get(LieferantDokumentDuplikatBackfillRunner.EINSTELLUNG, "0")).thenReturn("0");
        when(duplikatService.bereinigeDateiDuplikate())
                .thenReturn(new LieferantDokumentDuplikatService.Ergebnis(1, 1, 0));
        when(duplikatService.bereinigeInhaltsDuplikate()).thenReturn(NICHTS);

        runner().nachDemStart();

        // Synchron im Start-Ereignis: fertig, bevor der Abgleich-Backfill seine Arbeit einreiht
        verify(duplikatService).bereinigeDateiDuplikate();
    }
}
