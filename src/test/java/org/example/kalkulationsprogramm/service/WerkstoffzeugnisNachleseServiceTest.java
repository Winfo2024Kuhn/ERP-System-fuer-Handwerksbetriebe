package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.dto.WerkstoffzeugnisNachleseErgebnis;
import org.example.kalkulationsprogramm.repository.LieferantDokumentPositionRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WerkstoffzeugnisNachleseServiceTest {

    @Mock
    private LieferantDokumentRepository dokumentRepository;

    @Mock
    private LieferantDokumentPositionRepository positionRepository;

    @Mock
    private GeminiDokumentAnalyseService analyseService;

    @InjectMocks
    private WerkstoffzeugnisNachleseService service;

    private static LieferantGeschaeftsdokument gd(long id, String nummer) {
        LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
        gd.setId(id);
        gd.setDokumentNummer(nummer);
        return gd;
    }

    @Test
    void liestAlleNacheinanderUndZaehltErgebnisse() {
        when(dokumentRepository.findIdsOhneVollstaendigeDaten(LieferantDokumentTyp.WERKSTOFFZEUGNIS))
                .thenReturn(List.of(11L, 12L, 13L, 14L, 15L, 16L));
        when(analyseService.reanalysiereDokumentById(11L)).thenReturn(gd(11L, "4107891"));
        when(analyseService.reanalysiereDokumentById(12L)).thenReturn(gd(12L, "ZND-2"));
        // ohne Nummer, mit Fehler, ohne Positionen, fremde Geschäftsdaten: fehlgeschlagen
        when(analyseService.reanalysiereDokumentById(13L)).thenReturn(gd(13L, " "));
        when(analyseService.reanalysiereDokumentById(14L)).thenThrow(new IllegalStateException("KI nicht erreichbar"));
        when(analyseService.reanalysiereDokumentById(15L)).thenReturn(gd(15L, "ZND-5"));
        when(analyseService.reanalysiereDokumentById(16L)).thenReturn(gd(99L, "LS-99"));
        when(positionRepository.countByGeschaeftsdokumentId(11L)).thenReturn(3L);
        when(positionRepository.countByGeschaeftsdokumentId(12L)).thenReturn(1L);
        when(positionRepository.countByGeschaeftsdokumentId(15L)).thenReturn(0L);
        when(dokumentRepository.zaehleMitVerknuepfung(11L)).thenReturn(1L);
        when(dokumentRepository.zaehleMitVerknuepfung(12L)).thenReturn(0L);
        when(dokumentRepository.zaehleMitVerknuepfung(13L)).thenReturn(0L);
        when(dokumentRepository.zaehleMitVerknuepfung(14L)).thenReturn(1L);
        when(dokumentRepository.zaehleMitVerknuepfung(15L)).thenReturn(0L);
        when(dokumentRepository.zaehleMitVerknuepfung(16L)).thenReturn(0L);

        WerkstoffzeugnisNachleseErgebnis ergebnis = service.liesZeugnisseNach(1L);

        assertThat(ergebnis.gesamt()).isEqualTo(6);
        assertThat(ergebnis.erfolgreich()).isEqualTo(2);
        assertThat(ergebnis.fehlgeschlagen()).isEqualTo(4);
        assertThat(ergebnis.verknuepft()).isEqualTo(2);
        assertThat(ergebnis.fehlgeschlageneIds()).containsExactly(13L, 14L, 15L, 16L);
    }

    @Test
    void nullErgebnisZaehltAlsFehlgeschlagen() {
        when(dokumentRepository.findIdsOhneVollstaendigeDaten(LieferantDokumentTyp.WERKSTOFFZEUGNIS))
                .thenReturn(List.of(21L));
        when(analyseService.reanalysiereDokumentById(21L)).thenReturn(null);

        WerkstoffzeugnisNachleseErgebnis ergebnis = service.liesZeugnisseNach(null);

        assertThat(ergebnis.fehlgeschlagen()).isEqualTo(1);
        assertThat(ergebnis.erfolgreich()).isZero();
    }

    @Test
    void ohneZeugnisseKeineKiAufrufe() {
        when(dokumentRepository.findIdsOhneVollstaendigeDaten(LieferantDokumentTyp.WERKSTOFFZEUGNIS))
                .thenReturn(List.of());

        WerkstoffzeugnisNachleseErgebnis ergebnis = service.liesZeugnisseNach(1L);

        assertThat(ergebnis).isEqualTo(new WerkstoffzeugnisNachleseErgebnis(0, 0, 0, 0, List.of()));
        verifyNoInteractions(analyseService);
    }

    @Test
    void zweiterLaufWaehrendErsterLaeuftWirdAbgelehntDanachWiederFrei() throws Exception {
        CountDownLatch imLauf = new CountDownLatch(1);
        CountDownLatch weiter = new CountDownLatch(1);
        when(dokumentRepository.findIdsOhneVollstaendigeDaten(LieferantDokumentTyp.WERKSTOFFZEUGNIS))
                .thenAnswer(inv -> {
                    imLauf.countDown();
                    weiter.await(5, TimeUnit.SECONDS);
                    return List.of();
                });

        Thread erster = new Thread(() -> service.liesZeugnisseNach(1L));
        erster.start();
        assertThat(imLauf.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> service.liesZeugnisseNach(2L))
                .isInstanceOf(WerkstoffzeugnisNachleseService.LaufAktivException.class);

        weiter.countDown();
        erster.join(5_000);
        assertThat(service.liesZeugnisseNach(2L).gesamt()).isZero();
    }
}
