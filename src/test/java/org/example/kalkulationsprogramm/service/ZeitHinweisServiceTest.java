package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.BuchungsTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.MitarbeiterArt;
import org.example.kalkulationsprogramm.domain.ZeitHinweisGesendet;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.repository.ZeitHinweisGesendetRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class ZeitHinweisServiceTest {

    private static final LocalDate HEUTE = LocalDate.of(2026, 10, 16);
    private static final Long MA_ID = 7L;

    @Mock private ZeitbuchungRepository zeitbuchungRepository;
    @Mock private ZeitHinweisGesendetRepository gesendetRepository;
    @Mock private WebPushService webPushService;

    private ZeitHinweisService service;

    @BeforeEach
    void setUp() {
        service = new ZeitHinweisService(zeitbuchungRepository, gesendetRepository, webPushService);
        lenient().when(webPushService.isEnabled()).thenReturn(true);
    }

    @AfterEach
    void aufraeumen() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static Mitarbeiter mitarbeiter(Long id) {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(id);
        m.setArt(MitarbeiterArt.MENSCH);
        m.setAktiv(true);
        return m;
    }

    private static Zeitbuchung buchung(Mitarbeiter m, BuchungsTyp typ, LocalDateTime start, LocalDateTime ende) {
        Zeitbuchung z = new Zeitbuchung();
        z.setMitarbeiter(m);
        z.setTyp(typ);
        z.setStartZeit(start);
        z.setEndeZeit(ende);
        return z;
    }

    private static LocalDateTime um(int stunde, int minute) {
        return HEUTE.atTime(stunde, minute);
    }

    private void keinPush() {
        verify(webPushService, never()).notifyMitarbeiter(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Nested
    class Allgemein {

        @Test
        void raeumtAuchOhnePushDienstAufUndSchicktNichts() {
            when(webPushService.isEnabled()).thenReturn(false);

            service.pruefe();

            verify(gesendetRepository).loescheAelterAls(any());
            verifyNoInteractions(zeitbuchungRepository);
        }

        @Test
        void nachtsKlingeltKeinHandy() {
            service.pruefe(um(22, 0));
            service.pruefe(um(5, 59));

            verify(gesendetRepository, times(2)).loescheAelterAls(HEUTE.minusDays(7));
            verifyNoInteractions(zeitbuchungRepository);
        }
    }

    @Nested
    class AutomatischBeendet {

        private final Mitarbeiter ma = mitarbeiter(MA_ID);

        private Zeitbuchung autoStopp(BuchungsTyp typ, LocalDateTime start, LocalDateTime ende, LocalDateTime gestoppt) {
            Zeitbuchung z = buchung(ma, typ, start, ende);
            z.setAutomatischBeendet(true);
            z.setZuletztGeaendertAm(gestoppt);
            return z;
        }

        private void autoStopps(LocalDateTime jetzt, Zeitbuchung... buchungen) {
            when(zeitbuchungRepository.findByAutomatischBeendetTrueAndStartZeitAfterOrderByStartZeitDesc(jetzt.minusDays(2)))
                    .thenReturn(List.of(buchungen));
        }

        @Test
        void schicktDieGlockenMeldungAnDenMitarbeiterSelbst() {
            LocalDateTime jetzt = um(20, 5);
            autoStopps(jetzt, autoStopp(BuchungsTyp.ARBEIT, um(7, 12), um(20, 0), um(20, 1)));

            service.pruefe(jetzt);

            ArgumentCaptor<ZeitHinweisGesendet> gemerkt = ArgumentCaptor.forClass(ZeitHinweisGesendet.class);
            verify(gesendetRepository).save(gemerkt.capture());
            assertThat(gemerkt.getValue().getArt()).isEqualTo(ZeitHinweisGesendet.Art.AUTO_BEENDET);
            assertThat(gemerkt.getValue().getTag()).isEqualTo(HEUTE);
            ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
            verify(webPushService).notifyMitarbeiter(eq(MA_ID), eq("Kein Feierabend gestempelt"), text.capture(),
                    eq(ZeitHinweisService.URL_TAGESBUCHUNGEN), eq(ZeitHinweisService.TYP_AUTO_BEENDET));
            assertThat(text.getValue()).contains("Arbeitszeit vom 16.10.").contains("20:00 Uhr");
        }

        @Test
        void nachtsBeendetWirdMorgensGemeldet() {
            LocalDateTime morgens = HEUTE.plusDays(1).atTime(6, 0);
            autoStopps(morgens, autoStopp(BuchungsTyp.ARBEIT, um(7, 0), um(23, 59), HEUTE.plusDays(1).atTime(0, 2)));

            service.pruefe(morgens);

            verify(webPushService).notifyMitarbeiter(eq(MA_ID), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        void beendetePauseHeisstAuchPause() {
            LocalDateTime jetzt = um(20, 5);
            autoStopps(jetzt, autoStopp(BuchungsTyp.PAUSE, um(12, 0), um(20, 0), um(20, 1)));

            service.pruefe(jetzt);

            ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
            verify(webPushService).notifyMitarbeiter(eq(MA_ID), anyString(), text.capture(), anyString(), anyString());
            assertThat(text.getValue()).startsWith("Deine Pause vom 16.10.");
        }

        @Test
        void schonGemeldetOderZuAltOderUnvollstaendigKeinPush() {
            LocalDateTime jetzt = um(20, 5);
            Mitarbeiter inaktiv = mitarbeiter(9L);
            inaktiv.setAktiv(false);
            Zeitbuchung ohneMitarbeiter = autoStopp(BuchungsTyp.ARBEIT, um(7, 0), um(20, 0), um(20, 1));
            ohneMitarbeiter.setMitarbeiter(null);
            Zeitbuchung vonInaktiv = autoStopp(BuchungsTyp.ARBEIT, um(7, 0), um(20, 0), um(20, 1));
            vonInaktiv.setMitarbeiter(inaktiv);
            autoStopps(jetzt,
                    autoStopp(BuchungsTyp.ARBEIT, um(7, 0), um(20, 0), um(20, 1)),
                    autoStopp(BuchungsTyp.ARBEIT, um(7, 0).minusDays(1), um(20, 0).minusDays(1), um(7, 0)),
                    autoStopp(BuchungsTyp.ARBEIT, um(7, 0), null, um(20, 1)),
                    autoStopp(BuchungsTyp.ARBEIT, um(7, 0), um(20, 0), null),
                    ohneMitarbeiter, vonInaktiv);
            when(gesendetRepository.existsByMitarbeiterIdAndTagAndArt(MA_ID, HEUTE, ZeitHinweisGesendet.Art.AUTO_BEENDET))
                    .thenReturn(true);

            service.pruefe(jetzt);

            verify(gesendetRepository, never()).save(any());
            keinPush();
        }

        @Test
        void schicktErstNachDemCommit() {
            TransactionSynchronizationManager.initSynchronization();
            LocalDateTime jetzt = um(20, 5);
            autoStopps(jetzt, autoStopp(BuchungsTyp.ARBEIT, um(7, 12), um(20, 0), um(20, 1)));

            service.pruefe(jetzt);
            keinPush();

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(webPushService).notifyMitarbeiter(eq(MA_ID), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        void pushFehlerNachCommitBleibtFolgenlos() {
            TransactionSynchronizationManager.initSynchronization();
            doThrow(new IllegalStateException("Dienst weg")).when(webPushService)
                    .notifyMitarbeiter(anyLong(), anyString(), anyString(), anyString(), anyString());
            LocalDateTime jetzt = um(20, 5);
            autoStopps(jetzt, autoStopp(BuchungsTyp.ARBEIT, um(7, 0), um(20, 0), um(20, 1)));

            service.pruefe(jetzt);

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(webPushService).notifyMitarbeiter(anyLong(), anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    class Pausen {

        private final Mitarbeiter ma = mitarbeiter(MA_ID);

        private void heutigeBuchungen(LocalDateTime jetzt, Zeitbuchung... buchungen) {
            Zeitbuchung laufend = buchungen[buchungen.length - 1];
            when(zeitbuchungRepository.findByEndeZeitIsNull()).thenReturn(List.of(laufend));
            when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(MA_ID, HEUTE.atStartOfDay(), jetzt))
                    .thenReturn(List.of(buchungen));
        }

        @Test
        void erinnert15MinutenVorSechsStundenOhnePause() {
            LocalDateTime jetzt = um(13, 45);
            heutigeBuchungen(jetzt, buchung(ma, BuchungsTyp.ARBEIT, um(8, 0), null));

            service.pruefe(jetzt);

            ArgumentCaptor<ZeitHinweisGesendet> gemerkt = ArgumentCaptor.forClass(ZeitHinweisGesendet.class);
            verify(gesendetRepository).save(gemerkt.capture());
            assertThat(gemerkt.getValue().getArt()).isEqualTo(ZeitHinweisGesendet.Art.PAUSE_6_STUNDEN);
            assertThat(gemerkt.getValue().getTag()).isEqualTo(HEUTE);
            ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
            verify(webPushService).notifyMitarbeiter(eq(MA_ID), eq("Zeit für eine Pause"), text.capture(),
                    eq(ZeitHinweisService.URL_ZEITERFASSUNG), eq(ZeitHinweisService.TYP_PAUSE));
            assertThat(text.getValue()).contains("5:45 Std.").contains("6 Stunden").contains("30 Minuten")
                    .contains("fehlen noch 30 Minuten");
        }

        @Test
        void schicktDieselbeErinnerungNurEinmalAmTagUndKeineZweite() {
            LocalDateTime jetzt = um(13, 50);
            heutigeBuchungen(jetzt, buchung(ma, BuchungsTyp.ARBEIT, um(8, 0), null));
            when(gesendetRepository.existsByMitarbeiterIdAndTagAndArt(MA_ID, HEUTE,
                    ZeitHinweisGesendet.Art.PAUSE_6_STUNDEN)).thenReturn(true);

            service.pruefe(jetzt);

            // Auch die „am Stück“-Erinnerung bleibt aus: Es ist dieselbe fehlende Pause.
            verify(gesendetRepository, never()).save(any());
            keinPush();
        }

        @Test
        void vorDerVorlaufzeitKeineErinnerung() {
            LocalDateTime jetzt = um(13, 44);
            heutigeBuchungen(jetzt, buchung(ma, BuchungsTyp.ARBEIT, um(8, 0), null));

            service.pruefe(jetzt);

            verify(gesendetRepository, never()).save(any());
        }

        @Test
        void genugPauseGemachtKeineErinnerung() {
            LocalDateTime jetzt = um(14, 30);
            heutigeBuchungen(jetzt,
                    buchung(ma, BuchungsTyp.ARBEIT, um(8, 0), um(12, 0)),
                    buchung(ma, BuchungsTyp.PAUSE, um(12, 0), um(12, 30)),
                    buchung(ma, BuchungsTyp.ARBEIT, um(12, 30), null));

            service.pruefe(jetzt);

            verify(gesendetRepository, never()).save(any());
        }

        @Test
        void kurzeUnterbrechungenUnter15MinutenZaehlenNicht() {
            // 3 x 10 Minuten weg = 30 Minuten, aber keine davon zählt als Pause.
            LocalDateTime jetzt = um(14, 15);
            heutigeBuchungen(jetzt,
                    buchung(ma, BuchungsTyp.ARBEIT, um(8, 0), um(10, 0)),
                    buchung(ma, BuchungsTyp.ARBEIT, um(10, 10), um(12, 0)),
                    buchung(ma, BuchungsTyp.ARBEIT, um(12, 10), um(13, 0)),
                    buchung(ma, BuchungsTyp.ARBEIT, um(13, 10), null));

            service.pruefe(jetzt);

            ArgumentCaptor<ZeitHinweisGesendet> gemerkt = ArgumentCaptor.forClass(ZeitHinweisGesendet.class);
            verify(gesendetRepository).save(gemerkt.capture());
            assertThat(gemerkt.getValue().getArt()).isEqualTo(ZeitHinweisGesendet.Art.PAUSE_6_STUNDEN);
        }

        @Test
        void kurzVorNeunStundenZaehltNurDieHoehereGrenze() {
            LocalDateTime jetzt = um(17, 15);
            heutigeBuchungen(jetzt,
                    buchung(ma, BuchungsTyp.ARBEIT, um(8, 0), um(12, 0)),
                    buchung(ma, BuchungsTyp.ARBEIT, um(12, 30), null));

            service.pruefe(jetzt);

            ArgumentCaptor<ZeitHinweisGesendet> gemerkt = ArgumentCaptor.forClass(ZeitHinweisGesendet.class);
            verify(gesendetRepository, times(1)).save(gemerkt.capture());
            assertThat(gemerkt.getValue().getArt()).isEqualTo(ZeitHinweisGesendet.Art.PAUSE_9_STUNDEN);
            ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
            verify(webPushService).notifyMitarbeiter(eq(MA_ID), anyString(), text.capture(), anyString(), anyString());
            assertThat(text.getValue()).contains("8:45 Std.").contains("45 Minuten").contains("fehlen noch 15 Minuten");
        }

        @Test
        void neunStundenGrenzeMitGenugPauseFragtSechsStundenNichtMehr() {
            LocalDateTime jetzt = um(17, 30);
            heutigeBuchungen(jetzt,
                    buchung(ma, BuchungsTyp.ARBEIT, um(8, 0), um(12, 0)),
                    buchung(ma, BuchungsTyp.ARBEIT, um(12, 45), null));

            service.pruefe(jetzt);

            verify(gesendetRepository, never()).existsByMitarbeiterIdAndTagAndArt(any(), any(), any());
            verify(gesendetRepository, never()).save(any());
        }

        @Test
        void erinnertBeiFastSechsStundenAmStueckTrotzGenugGesamtpause() {
            // Frühstück 9:00–9:30, dann ohne Mittag durch: Gesamtpause reicht, am Stück zu lang.
            LocalDateTime jetzt = um(15, 15);
            heutigeBuchungen(jetzt,
                    buchung(ma, BuchungsTyp.ARBEIT, um(7, 0), um(9, 0)),
                    buchung(ma, BuchungsTyp.ARBEIT, um(9, 30), null));

            service.pruefe(jetzt);

            ArgumentCaptor<ZeitHinweisGesendet> gemerkt = ArgumentCaptor.forClass(ZeitHinweisGesendet.class);
            verify(gesendetRepository).save(gemerkt.capture());
            assertThat(gemerkt.getValue().getArt()).isEqualTo(ZeitHinweisGesendet.Art.PAUSE_AM_STUECK);
            ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
            verify(webPushService).notifyMitarbeiter(eq(MA_ID), eq("Zeit für eine Pause"), text.capture(),
                    eq(ZeitHinweisService.URL_ZEITERFASSUNG), eq(ZeitHinweisService.TYP_PAUSE));
            assertThat(text.getValue()).contains("seit 5:45 Std. ohne Pause").contains("am Stück");
        }

        @Test
        void amStueckKnappUnterDerVorlaufzeitKeineErinnerung() {
            LocalDateTime jetzt = um(15, 14);
            heutigeBuchungen(jetzt,
                    buchung(ma, BuchungsTyp.ARBEIT, um(7, 0), um(9, 0)),
                    buchung(ma, BuchungsTyp.ARBEIT, um(9, 30), null));

            service.pruefe(jetzt);

            verify(gesendetRepository, never()).save(any());
        }

        @Test
        void laufendePauseFremdeArtInaktiveUndGestrigeBuchungenWerdenUebersprungen() {
            Mitarbeiter maschine = mitarbeiter(8L);
            maschine.setArt(MitarbeiterArt.SYSTEM);
            Mitarbeiter inaktiv = mitarbeiter(9L);
            inaktiv.setAktiv(false);
            when(zeitbuchungRepository.findByEndeZeitIsNull()).thenReturn(List.of(
                    buchung(ma, BuchungsTyp.PAUSE, um(12, 0), null),
                    buchung(maschine, BuchungsTyp.ARBEIT, um(6, 0), null),
                    buchung(inaktiv, BuchungsTyp.ARBEIT, um(6, 0), null),
                    buchung(mitarbeiter(10L), BuchungsTyp.ARBEIT, um(6, 0).minusDays(1), null),
                    buchung(null, BuchungsTyp.ARBEIT, um(6, 0), null),
                    buchung(mitarbeiter(11L), BuchungsTyp.ARBEIT, null, null)));

            service.pruefe(um(14, 0));

            verify(zeitbuchungRepository, never()).findByMitarbeiterIdAndStartZeitBetween(any(), any(), any());
        }

        @Test
        void mehrereOffeneBuchungenDerselbenPersonWerdenEinmalGeprueft() {
            LocalDateTime jetzt = um(10, 0);
            Zeitbuchung a = buchung(ma, BuchungsTyp.ARBEIT, um(8, 0), null);
            Zeitbuchung b = buchung(ma, BuchungsTyp.ARBEIT, um(9, 0), null);
            when(zeitbuchungRepository.findByEndeZeitIsNull()).thenReturn(List.of(a, b));
            when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(MA_ID, HEUTE.atStartOfDay(), jetzt))
                    .thenReturn(List.of(a, b));

            service.pruefe(jetzt);

            verify(zeitbuchungRepository, times(1)).findByMitarbeiterIdAndStartZeitBetween(any(), any(), any());
        }
    }

    @Nested
    class Bilanz {

        private final Mitarbeiter ma = mitarbeiter(MA_ID);

        @Test
        void ueberlappendeBuchungenZaehlenNichtDoppelt() {
            ZeitHinweisService.Tagesbilanz bilanz = ZeitHinweisService.Tagesbilanz.aus(List.of(
                    buchung(ma, BuchungsTyp.ARBEIT, um(8, 0), um(12, 0)),
                    buchung(ma, BuchungsTyp.ARBEIT, um(11, 0), um(11, 30)),
                    buchung(ma, BuchungsTyp.ARBEIT, um(11, 30), um(13, 0))), um(18, 0));

            assertThat(bilanz.arbeit()).isEqualTo(Duration.ofHours(5));
            assertThat(bilanz.pause()).isZero();
            assertThat(bilanz.amStueck()).isEqualTo(Duration.ofHours(5));
        }

        @Test
        void kapptAmJetztUndIgnoriertPausenbuchungenUndLeeres() {
            ZeitHinweisService.Tagesbilanz bilanz = ZeitHinweisService.Tagesbilanz.aus(List.of(
                    buchung(ma, BuchungsTyp.ARBEIT, um(13, 0), um(19, 0)),
                    buchung(ma, BuchungsTyp.PAUSE, um(12, 0), um(13, 0)),
                    buchung(ma, BuchungsTyp.ARBEIT, null, um(9, 0)),
                    buchung(ma, BuchungsTyp.ARBEIT, um(16, 0), um(17, 0)),
                    buchung(ma, null, um(8, 0), um(12, 0))), um(15, 0));

            // 8–12 (Typ fehlt = Arbeit) + 13–15 (gekappt); 16–17 liegt in der Zukunft.
            assertThat(bilanz.arbeit()).isEqualTo(Duration.ofHours(6));
            assertThat(bilanz.pause()).isEqualTo(Duration.ofHours(1));
            assertThat(bilanz.amStueck()).isEqualTo(Duration.ofHours(2));
        }

        @Test
        void kurzeLueckeUnterbrichtDasAmStueckNicht() {
            ZeitHinweisService.Tagesbilanz bilanz = ZeitHinweisService.Tagesbilanz.aus(List.of(
                    buchung(ma, BuchungsTyp.ARBEIT, um(8, 0), um(11, 0)),
                    buchung(ma, BuchungsTyp.ARBEIT, um(11, 10), um(13, 0))), um(13, 0));

            assertThat(bilanz.amStueck()).isEqualTo(Duration.ofMinutes(290));
            assertThat(bilanz.pause()).isZero();
        }
    }
}
