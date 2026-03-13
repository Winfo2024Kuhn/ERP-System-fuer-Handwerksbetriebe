package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit-Tests für MonatsSaldoService.
 * Testet Caching, Berechnung, Invalidierung und Fehlerfälle.
 */
@ExtendWith(MockitoExtension.class)
class MonatsSaldoServiceTest {

    @Mock
    private MonatsSaldoRepository monatsSaldoRepository;

    @Mock
    private ZeitbuchungRepository zeitbuchungRepository;

    @Mock
    private AbwesenheitRepository abwesenheitRepository;

    @Mock
    private ZeitkontoKorrekturRepository korrekturRepository;

    @Mock
    private MitarbeiterRepository mitarbeiterRepository;

    @Mock
    private ZeitkontoService zeitkontoService;

    @Mock
    private FeiertagService feiertagService;

    @InjectMocks
    private MonatsSaldoService monatsSaldoService;

    private Mitarbeiter testMitarbeiter;
    private Zeitkonto testZeitkonto;

    private static final Long MITARBEITER_ID = 1L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(monatsSaldoService, "self", monatsSaldoService);

        testMitarbeiter = new Mitarbeiter();
        testMitarbeiter.setId(MITARBEITER_ID);
        testMitarbeiter.setVorname("Max");
        testMitarbeiter.setNachname("Mustermann");

        testZeitkonto = new Zeitkonto(testMitarbeiter);
        testZeitkonto.setMontagStunden(new BigDecimal("8.00"));
        testZeitkonto.setDienstagStunden(new BigDecimal("8.00"));
        testZeitkonto.setMittwochStunden(new BigDecimal("8.00"));
        testZeitkonto.setDonnerstagStunden(new BigDecimal("8.00"));
        testZeitkonto.setFreitagStunden(new BigDecimal("8.00"));
        testZeitkonto.setSamstagStunden(BigDecimal.ZERO);
        testZeitkonto.setSonntagStunden(BigDecimal.ZERO);

        // self-injection für @Lazy @Autowired self-Proxy simulieren
        ReflectionTestUtils.setField(monatsSaldoService, "self", monatsSaldoService);
    }

    // ==================== Hilfsmethoden ====================

    private Zeitbuchung erstelleArbeitsbuchung(LocalDateTime start, LocalDateTime ende, BigDecimal stunden) {
        Zeitbuchung b = new Zeitbuchung();
        b.setMitarbeiter(testMitarbeiter);
        b.setStartZeit(start);
        b.setEndeZeit(ende);
        b.setAnzahlInStunden(stunden);
        b.setTyp(BuchungsTyp.ARBEIT);
        return b;
    }

    private Zeitbuchung erstellePause(LocalDateTime start, LocalDateTime ende, BigDecimal stunden) {
        Zeitbuchung b = new Zeitbuchung();
        b.setMitarbeiter(testMitarbeiter);
        b.setStartZeit(start);
        b.setEndeZeit(ende);
        b.setAnzahlInStunden(stunden);
        b.setTyp(BuchungsTyp.PAUSE);
        return b;
    }

    private ZeitkontoKorrektur erstelleKorrektur(LocalDate datum, BigDecimal stunden, boolean storniert, KorrekturTyp typ) {
        ZeitkontoKorrektur k = new ZeitkontoKorrektur();
        k.setMitarbeiter(testMitarbeiter);
        k.setDatum(datum);
        k.setStunden(stunden);
        k.setStorniert(storniert);
        k.setTyp(typ);
        k.setGrund("Test-Korrektur");
        return k;
    }

    private MonatsSaldo erstelleGueltigesCache(int jahr, int monat) {
        MonatsSaldo ms = new MonatsSaldo();
        ms.setId(100L);
        ms.setMitarbeiter(testMitarbeiter);
        ms.setJahr(jahr);
        ms.setMonat(monat);
        ms.setIstStunden(new BigDecimal("160.00"));
        ms.setSollStunden(new BigDecimal("168.00"));
        ms.setAbwesenheitsStunden(BigDecimal.ZERO);
        ms.setFeiertagsStunden(BigDecimal.ZERO);
        ms.setKorrekturStunden(BigDecimal.ZERO);
        ms.setGueltig(true);
        ms.setBerechnetAm(LocalDateTime.now().minusDays(1));
        return ms;
    }

    private void setupStandardMocks(int jahr, int monat) {
        LocalDate ersterTag = LocalDate.of(jahr, monat, 1);
        LocalDate letzterTag = YearMonth.of(jahr, monat).atEndOfMonth();
        LocalDateTime startDT = ersterTag.atStartOfDay();
        LocalDateTime endDT = letzterTag.atTime(23, 59, 59);

        lenient().when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(
                eq(MITARBEITER_ID), eq(startDT), eq(endDT)))
                .thenReturn(Collections.emptyList());

        lenient().when(zeitkontoService.berechneSollstundenFuerMonat(MITARBEITER_ID, jahr, monat))
                .thenReturn(new BigDecimal("168.00"));

        lenient().when(abwesenheitRepository.sumStundenByMitarbeiterIdAndDatumBetween(
                eq(MITARBEITER_ID), eq(ersterTag), eq(letzterTag)))
                .thenReturn(BigDecimal.ZERO);

        lenient().when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID))
                .thenReturn(testZeitkonto);

        lenient().when(feiertagService.istFeiertag(any(LocalDate.class))).thenReturn(false);

        lenient().when(korrekturRepository.findByMitarbeiterIdAndDatumBetween(
                eq(MITARBEITER_ID), eq(ersterTag), eq(letzterTag)))
                .thenReturn(Collections.emptyList());
    }

    // ==================== Cache-Verhalten ====================

    @Nested
    class CacheVerhalten {

        @Test
        void vergangenerMonat_GueltigerCache_WirdDirektZurueckgegeben() {
            // Vergangener Monat: Cache sollte genutzt werden
            int jahr = 2025;
            int monat = 1;
            MonatsSaldo cachedSaldo = erstelleGueltigesCache(jahr, monat);

            when(monatsSaldoRepository.findByMitarbeiterIdAndJahrAndMonat(MITARBEITER_ID, jahr, monat))
                    .thenReturn(Optional.of(cachedSaldo));

            MonatsSaldo result = monatsSaldoService.getOrBerechne(MITARBEITER_ID, jahr, monat);

            assertNotNull(result);
            assertEquals(new BigDecimal("160.00"), result.getIstStunden());
            assertTrue(result.getGueltig());
            // Keine Neuberechnung → keine Repository-Aufrufe für Zeitbuchungen
            verifyNoInteractions(zeitbuchungRepository);
        }

        @Test
        void vergangenerMonat_UngueltigerCache_WirdNeuBerechnet() {
            int jahr = 2025;
            int monat = 1;
            MonatsSaldo invalidCached = erstelleGueltigesCache(jahr, monat);
            invalidCached.setGueltig(false);

            when(monatsSaldoRepository.findByMitarbeiterIdAndJahrAndMonat(MITARBEITER_ID, jahr, monat))
                    .thenReturn(Optional.of(invalidCached));

            setupStandardMocks(jahr, monat);
            when(monatsSaldoRepository.save(any(MonatsSaldo.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            MonatsSaldo result = monatsSaldoService.getOrBerechne(MITARBEITER_ID, jahr, monat);

            assertNotNull(result);
            assertTrue(result.getGueltig());
            verify(monatsSaldoRepository).save(any(MonatsSaldo.class));
        }

        @Test
        void vergangenerMonat_KeinCache_WirdNeuBerechnetUndGespeichert() {
            int jahr = 2025;
            int monat = 1;

            when(monatsSaldoRepository.findByMitarbeiterIdAndJahrAndMonat(MITARBEITER_ID, jahr, monat))
                    .thenReturn(Optional.empty());

            setupStandardMocks(jahr, monat);
            when(mitarbeiterRepository.findById(MITARBEITER_ID))
                    .thenReturn(Optional.of(testMitarbeiter));
            when(monatsSaldoRepository.save(any(MonatsSaldo.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            MonatsSaldo result = monatsSaldoService.getOrBerechne(MITARBEITER_ID, jahr, monat);

            assertNotNull(result);
            assertTrue(result.getGueltig());
            verify(monatsSaldoRepository).save(any(MonatsSaldo.class));
            verify(mitarbeiterRepository).findById(MITARBEITER_ID);
        }

        @Test
        void aktuellerMonat_WirdImmerLiveBerechnet_NieGecached() {
            LocalDate heute = LocalDate.now();
            int jahr = heute.getYear();
            int monat = heute.getMonthValue();

            setupStandardMocks(jahr, monat);

            MonatsSaldo result = monatsSaldoService.getOrBerechne(MITARBEITER_ID, jahr, monat);

            assertNotNull(result);
            // Kein Cache-Zugriff bei aktuellem Monat
            verify(monatsSaldoRepository, never()).findByMitarbeiterIdAndJahrAndMonat(
                    anyLong(), anyInt(), anyInt());
            // Kein Speichern bei aktuellem Monat
            verify(monatsSaldoRepository, never()).save(any(MonatsSaldo.class));
        }

        @Test
        void zukuenftigerMonat_WirdImmerLiveBerechnet() {
            LocalDate zukunft = LocalDate.now().plusMonths(2);
            int jahr = zukunft.getYear();
            int monat = zukunft.getMonthValue();

            setupStandardMocks(jahr, monat);

            MonatsSaldo result = monatsSaldoService.getOrBerechne(MITARBEITER_ID, jahr, monat);

            assertNotNull(result);
            verify(monatsSaldoRepository, never()).findByMitarbeiterIdAndJahrAndMonat(
                    anyLong(), anyInt(), anyInt());
            verify(monatsSaldoRepository, never()).save(any(MonatsSaldo.class));
        }
    }

    // ==================== Berechnung ====================

    @Nested
    class Berechnung {

        @Test
        void berechnetIstStundenAusArbeitsbuchungenOhnePausen() {
            LocalDate heute = LocalDate.now();
            int jahr = heute.getYear();
            int monat = heute.getMonthValue();
            LocalDate ersterTag = LocalDate.of(jahr, monat, 1);
            LocalDate letzterTag = YearMonth.of(jahr, monat).atEndOfMonth();

            Zeitbuchung arbeit1 = erstelleArbeitsbuchung(
                    ersterTag.atTime(8, 0), ersterTag.atTime(12, 0), new BigDecimal("4.00"));
            Zeitbuchung arbeit2 = erstelleArbeitsbuchung(
                    ersterTag.atTime(13, 0), ersterTag.atTime(17, 0), new BigDecimal("4.00"));
            Zeitbuchung pause = erstellePause(
                    ersterTag.atTime(12, 0), ersterTag.atTime(13, 0), new BigDecimal("1.00"));

            when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(List.of(arbeit1, arbeit2, pause));

            when(zeitkontoService.berechneSollstundenFuerMonat(MITARBEITER_ID, jahr, monat))
                    .thenReturn(new BigDecimal("168.00"));
            when(abwesenheitRepository.sumStundenByMitarbeiterIdAndDatumBetween(
                    eq(MITARBEITER_ID), eq(ersterTag), eq(letzterTag)))
                    .thenReturn(BigDecimal.ZERO);
            when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID)).thenReturn(testZeitkonto);
            when(feiertagService.istFeiertag(any())).thenReturn(false);
            when(korrekturRepository.findByMitarbeiterIdAndDatumBetween(
                    eq(MITARBEITER_ID), eq(ersterTag), eq(letzterTag)))
                    .thenReturn(Collections.emptyList());

            MonatsSaldo result = monatsSaldoService.getOrBerechne(MITARBEITER_ID, jahr, monat);

            // 4 + 4 = 8 (Pause wird ignoriert)
            assertEquals(0, new BigDecimal("8.00").compareTo(result.getIstStunden()));
        }

        @Test
        void beruecksichtigtAbwesenheitsstunden() {
            LocalDate heute = LocalDate.now();
            int jahr = heute.getYear();
            int monat = heute.getMonthValue();
            LocalDate ersterTag = LocalDate.of(jahr, monat, 1);
            LocalDate letzterTag = YearMonth.of(jahr, monat).atEndOfMonth();

            when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(zeitkontoService.berechneSollstundenFuerMonat(MITARBEITER_ID, jahr, monat))
                    .thenReturn(new BigDecimal("168.00"));
            when(abwesenheitRepository.sumStundenByMitarbeiterIdAndDatumBetween(
                    eq(MITARBEITER_ID), eq(ersterTag), eq(letzterTag)))
                    .thenReturn(new BigDecimal("40.00")); // 5 Urlaubstage
            when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID)).thenReturn(testZeitkonto);
            when(feiertagService.istFeiertag(any())).thenReturn(false);
            when(korrekturRepository.findByMitarbeiterIdAndDatumBetween(
                    eq(MITARBEITER_ID), eq(ersterTag), eq(letzterTag)))
                    .thenReturn(Collections.emptyList());

            MonatsSaldo result = monatsSaldoService.getOrBerechne(MITARBEITER_ID, jahr, monat);

            assertEquals(0, new BigDecimal("40.00").compareTo(result.getAbwesenheitsStunden()));
            // GesamtIst = 0 (ist) + 40 (abwesenheit) + 0 (feiertag) + 0 (korrektur)
            assertEquals(0, new BigDecimal("40.00").compareTo(result.getGesamtIst()));
        }

        @Test
        void beruecksichtigtKorrekturstunden_NurAktiveUndTypStunden() {
            LocalDate heute = LocalDate.now();
            int jahr = heute.getYear();
            int monat = heute.getMonthValue();
            LocalDate ersterTag = LocalDate.of(jahr, monat, 1);
            LocalDate letzterTag = YearMonth.of(jahr, monat).atEndOfMonth();

            // Aktive Stundenkorrektur (+2h)
            ZeitkontoKorrektur aktiveKorrektur = erstelleKorrektur(
                    ersterTag.plusDays(5), new BigDecimal("2.00"), false, KorrekturTyp.STUNDEN);
            // Stornierte Korrektur (sollte ignoriert werden)
            ZeitkontoKorrektur stornierteKorrektur = erstelleKorrektur(
                    ersterTag.plusDays(6), new BigDecimal("5.00"), true, KorrekturTyp.STUNDEN);
            // Urlaubs-Korrektur (falscher Typ, sollte ignoriert werden)
            ZeitkontoKorrektur urlaubsKorrektur = erstelleKorrektur(
                    ersterTag.plusDays(7), new BigDecimal("1.00"), false, KorrekturTyp.URLAUB);

            when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(zeitkontoService.berechneSollstundenFuerMonat(MITARBEITER_ID, jahr, monat))
                    .thenReturn(new BigDecimal("168.00"));
            when(abwesenheitRepository.sumStundenByMitarbeiterIdAndDatumBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(BigDecimal.ZERO);
            when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID)).thenReturn(testZeitkonto);
            when(feiertagService.istFeiertag(any())).thenReturn(false);
            when(korrekturRepository.findByMitarbeiterIdAndDatumBetween(
                    eq(MITARBEITER_ID), eq(ersterTag), eq(letzterTag)))
                    .thenReturn(List.of(aktiveKorrektur, stornierteKorrektur, urlaubsKorrektur));

            MonatsSaldo result = monatsSaldoService.getOrBerechne(MITARBEITER_ID, jahr, monat);

            // Nur die aktive STUNDEN-Korrektur (2h) sollte gezählt werden
            assertEquals(0, new BigDecimal("2.00").compareTo(result.getKorrekturStunden()));
        }

        @Test
        void beruecksichtigtFeiertagsstunden() {
            LocalDate heute = LocalDate.now();
            int jahr = heute.getYear();
            int monat = heute.getMonthValue();
            LocalDate ersterTag = LocalDate.of(jahr, monat, 1);
            LocalDate letzterTag = YearMonth.of(jahr, monat).atEndOfMonth();

            // Einen Wochentag als Feiertag markieren
            LocalDate feiertag = ersterTag;
            while (feiertag.getDayOfWeek().getValue() > 5) {
                feiertag = feiertag.plusDays(1); // Nächsten Wochentag finden
            }
            final LocalDate feiertagFinal = feiertag;

            when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(zeitkontoService.berechneSollstundenFuerMonat(MITARBEITER_ID, jahr, monat))
                    .thenReturn(new BigDecimal("168.00"));
            when(abwesenheitRepository.sumStundenByMitarbeiterIdAndDatumBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(BigDecimal.ZERO);
            when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID)).thenReturn(testZeitkonto);
            when(feiertagService.istFeiertag(any(LocalDate.class)))
                    .thenAnswer(inv -> inv.getArgument(0).equals(feiertagFinal));
            when(feiertagService.istHalberFeiertag(any(LocalDate.class))).thenReturn(false);
            when(korrekturRepository.findByMitarbeiterIdAndDatumBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());

            MonatsSaldo result = monatsSaldoService.getOrBerechne(MITARBEITER_ID, jahr, monat);

            // Ein voller Feiertag an einem Arbeitstag = 8 Stunden
            assertEquals(0, new BigDecimal("8.00").compareTo(result.getFeiertagsStunden()),
                    "Voller Feiertag an Arbeitstag sollte 8 Stunden ergeben, war: " + result.getFeiertagsStunden());
        }

        @Test
        void halberFeiertag_Zaehlt50Prozent() {
            LocalDate heute = LocalDate.now();
            int jahr = heute.getYear();
            int monat = heute.getMonthValue();
            LocalDate ersterTag = LocalDate.of(jahr, monat, 1);
            LocalDate letzterTag = YearMonth.of(jahr, monat).atEndOfMonth();

            // Einen Wochentag als halben Feiertag markieren
            LocalDate halberFeiertag = ersterTag;
            while (halberFeiertag.getDayOfWeek().getValue() > 5) {
                halberFeiertag = halberFeiertag.plusDays(1);
            }
            final LocalDate halberFeiertagFinal = halberFeiertag;

            when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(zeitkontoService.berechneSollstundenFuerMonat(MITARBEITER_ID, jahr, monat))
                    .thenReturn(new BigDecimal("168.00"));
            when(abwesenheitRepository.sumStundenByMitarbeiterIdAndDatumBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(BigDecimal.ZERO);
            when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID)).thenReturn(testZeitkonto);
            when(feiertagService.istFeiertag(any(LocalDate.class)))
                    .thenAnswer(inv -> inv.getArgument(0).equals(halberFeiertagFinal));
            when(feiertagService.istHalberFeiertag(any(LocalDate.class)))
                    .thenAnswer(inv -> inv.getArgument(0).equals(halberFeiertagFinal));
            when(korrekturRepository.findByMitarbeiterIdAndDatumBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());

            MonatsSaldo result = monatsSaldoService.getOrBerechne(MITARBEITER_ID, jahr, monat);

            // Halber Feiertag = 50% der Sollstunden = 4.00
            assertEquals(0, new BigDecimal("4.00").compareTo(result.getFeiertagsStunden()),
                    "Halber Feiertag sollte 4 Stunden ergeben, war: " + result.getFeiertagsStunden());
        }

        @Test
        void feiertagAmWochenende_WirdNichtGezaehlt() {
            LocalDate heute = LocalDate.now();
            int jahr = heute.getYear();
            int monat = heute.getMonthValue();
            LocalDate ersterTag = LocalDate.of(jahr, monat, 1);
            LocalDate letzterTag = YearMonth.of(jahr, monat).atEndOfMonth();

            // Einen Samstag im Monat finden
            LocalDate samstag = ersterTag;
            while (samstag.getDayOfWeek().getValue() != 6) {
                samstag = samstag.plusDays(1);
            }
            final LocalDate samstagFinal = samstag;

            when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(zeitkontoService.berechneSollstundenFuerMonat(MITARBEITER_ID, jahr, monat))
                    .thenReturn(new BigDecimal("168.00"));
            when(abwesenheitRepository.sumStundenByMitarbeiterIdAndDatumBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(BigDecimal.ZERO);
            when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID)).thenReturn(testZeitkonto);
            // Samstag ist Feiertag, aber Sollstunden = 0 → wird nicht gezählt
            when(feiertagService.istFeiertag(any(LocalDate.class)))
                    .thenAnswer(inv -> inv.getArgument(0).equals(samstagFinal));
            when(korrekturRepository.findByMitarbeiterIdAndDatumBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());

            MonatsSaldo result = monatsSaldoService.getOrBerechne(MITARBEITER_ID, jahr, monat);

            assertEquals(0, BigDecimal.ZERO.compareTo(result.getFeiertagsStunden()),
                    "Feiertag am Wochenende (Soll=0) sollte 0 Stunden ergeben");
        }

        @Test
        void gesamtIstBerechnung_SummiertAlleKomponenten() {
            LocalDate heute = LocalDate.now();
            int jahr = heute.getYear();
            int monat = heute.getMonthValue();
            LocalDate ersterTag = LocalDate.of(jahr, monat, 1);
            LocalDate letzterTag = YearMonth.of(jahr, monat).atEndOfMonth();

            // 120h Arbeit
            Zeitbuchung arbeit = erstelleArbeitsbuchung(
                    ersterTag.atTime(8, 0), ersterTag.atTime(16, 0), new BigDecimal("120.00"));

            // 3h Korrektur
            ZeitkontoKorrektur korrektur = erstelleKorrektur(
                    ersterTag.plusDays(10), new BigDecimal("3.00"), false, KorrekturTyp.STUNDEN);

            when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(List.of(arbeit));
            when(zeitkontoService.berechneSollstundenFuerMonat(MITARBEITER_ID, jahr, monat))
                    .thenReturn(new BigDecimal("168.00"));
            // 16h Abwesenheit
            when(abwesenheitRepository.sumStundenByMitarbeiterIdAndDatumBetween(
                    eq(MITARBEITER_ID), eq(ersterTag), eq(letzterTag)))
                    .thenReturn(new BigDecimal("16.00"));
            when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID)).thenReturn(testZeitkonto);
            when(feiertagService.istFeiertag(any())).thenReturn(false);
            when(korrekturRepository.findByMitarbeiterIdAndDatumBetween(
                    eq(MITARBEITER_ID), eq(ersterTag), eq(letzterTag)))
                    .thenReturn(List.of(korrektur));

            MonatsSaldo result = monatsSaldoService.getOrBerechne(MITARBEITER_ID, jahr, monat);

            assertEquals(0, new BigDecimal("120.00").compareTo(result.getIstStunden()));
            assertEquals(0, new BigDecimal("16.00").compareTo(result.getAbwesenheitsStunden()));
            assertEquals(0, new BigDecimal("3.00").compareTo(result.getKorrekturStunden()));
            // GesamtIst = 120 + 16 + 0 (Feiertage) + 3 = 139
            assertEquals(0, new BigDecimal("139.00").compareTo(result.getGesamtIst()));
            // Differenz = 139 - 168 = -29
            assertEquals(0, new BigDecimal("-29.00").compareTo(result.getDifferenz()));
        }

        @Test
        void nullAbwesenheitsstunden_WirdAlsZeroBehandelt() {
            LocalDate heute = LocalDate.now();
            int jahr = heute.getYear();
            int monat = heute.getMonthValue();
            LocalDate ersterTag = LocalDate.of(jahr, monat, 1);
            LocalDate letzterTag = YearMonth.of(jahr, monat).atEndOfMonth();

            when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(zeitkontoService.berechneSollstundenFuerMonat(MITARBEITER_ID, jahr, monat))
                    .thenReturn(new BigDecimal("168.00"));
            // Repository gibt null zurück (kein SUM-Ergebnis)
            when(abwesenheitRepository.sumStundenByMitarbeiterIdAndDatumBetween(
                    eq(MITARBEITER_ID), eq(ersterTag), eq(letzterTag)))
                    .thenReturn(null);
            when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID)).thenReturn(testZeitkonto);
            when(feiertagService.istFeiertag(any())).thenReturn(false);
            when(korrekturRepository.findByMitarbeiterIdAndDatumBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());

            MonatsSaldo result = monatsSaldoService.getOrBerechne(MITARBEITER_ID, jahr, monat);

            assertEquals(0, BigDecimal.ZERO.compareTo(result.getAbwesenheitsStunden()));
        }

        @Test
        void buchungMitNullStunden_WirdIgnoriert() {
            LocalDate heute = LocalDate.now();
            int jahr = heute.getYear();
            int monat = heute.getMonthValue();
            LocalDate ersterTag = LocalDate.of(jahr, monat, 1);

            Zeitbuchung mitStunden = erstelleArbeitsbuchung(
                    ersterTag.atTime(8, 0), ersterTag.atTime(12, 0), new BigDecimal("4.00"));
            Zeitbuchung ohneStunden = new Zeitbuchung();
            ohneStunden.setMitarbeiter(testMitarbeiter);
            ohneStunden.setStartZeit(ersterTag.atTime(13, 0));
            ohneStunden.setTyp(BuchungsTyp.ARBEIT);
            ohneStunden.setAnzahlInStunden(null); // Noch laufende Buchung

            when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(List.of(mitStunden, ohneStunden));
            when(zeitkontoService.berechneSollstundenFuerMonat(eq(MITARBEITER_ID), anyInt(), anyInt()))
                    .thenReturn(new BigDecimal("168.00"));
            when(abwesenheitRepository.sumStundenByMitarbeiterIdAndDatumBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(BigDecimal.ZERO);
            when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID)).thenReturn(testZeitkonto);
            when(feiertagService.istFeiertag(any())).thenReturn(false);
            when(korrekturRepository.findByMitarbeiterIdAndDatumBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());

            MonatsSaldo result = monatsSaldoService.getOrBerechne(MITARBEITER_ID, jahr, monat);

            // Nur die Buchung mit Stunden zählt
            assertEquals(0, new BigDecimal("4.00").compareTo(result.getIstStunden()));
        }

        @Test
        void korrekturMitNullStunden_WirdAlsZeroBehandelt() {
            LocalDate heute = LocalDate.now();
            int jahr = heute.getYear();
            int monat = heute.getMonthValue();
            LocalDate ersterTag = LocalDate.of(jahr, monat, 1);
            LocalDate letzterTag = YearMonth.of(jahr, monat).atEndOfMonth();

            ZeitkontoKorrektur ohneStunden = erstelleKorrektur(
                    ersterTag.plusDays(5), null, false, KorrekturTyp.STUNDEN);
            // setStunden(null) explizit
            ohneStunden.setStunden(null);

            when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(zeitkontoService.berechneSollstundenFuerMonat(MITARBEITER_ID, jahr, monat))
                    .thenReturn(new BigDecimal("168.00"));
            when(abwesenheitRepository.sumStundenByMitarbeiterIdAndDatumBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(BigDecimal.ZERO);
            when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID)).thenReturn(testZeitkonto);
            when(feiertagService.istFeiertag(any())).thenReturn(false);
            when(korrekturRepository.findByMitarbeiterIdAndDatumBetween(
                    eq(MITARBEITER_ID), eq(ersterTag), eq(letzterTag)))
                    .thenReturn(List.of(ohneStunden));

            MonatsSaldo result = monatsSaldoService.getOrBerechne(MITARBEITER_ID, jahr, monat);

            assertEquals(0, BigDecimal.ZERO.compareTo(result.getKorrekturStunden()));
        }

        @Test
        void negativeKorrektur_WirdKorrektVerarbeitet() {
            LocalDate heute = LocalDate.now();
            int jahr = heute.getYear();
            int monat = heute.getMonthValue();
            LocalDate ersterTag = LocalDate.of(jahr, monat, 1);
            LocalDate letzterTag = YearMonth.of(jahr, monat).atEndOfMonth();

            // Negative Korrektur (-3h)
            ZeitkontoKorrektur negativeKorrektur = erstelleKorrektur(
                    ersterTag.plusDays(5), new BigDecimal("-3.00"), false, KorrekturTyp.STUNDEN);

            when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(zeitkontoService.berechneSollstundenFuerMonat(MITARBEITER_ID, jahr, monat))
                    .thenReturn(new BigDecimal("168.00"));
            when(abwesenheitRepository.sumStundenByMitarbeiterIdAndDatumBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(BigDecimal.ZERO);
            when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID)).thenReturn(testZeitkonto);
            when(feiertagService.istFeiertag(any())).thenReturn(false);
            when(korrekturRepository.findByMitarbeiterIdAndDatumBetween(
                    eq(MITARBEITER_ID), eq(ersterTag), eq(letzterTag)))
                    .thenReturn(List.of(negativeKorrektur));

            MonatsSaldo result = monatsSaldoService.getOrBerechne(MITARBEITER_ID, jahr, monat);

            assertEquals(0, new BigDecimal("-3.00").compareTo(result.getKorrekturStunden()));
            assertEquals(0, new BigDecimal("-3.00").compareTo(result.getGesamtIst()));
        }
    }

    // ==================== Invalidierung ====================

    @Nested
    class Invalidierung {

        @Test
        void invalidiereMonat_RuftRepositoryAuf() {
            monatsSaldoService.invalidiereMonat(MITARBEITER_ID, 2025, 6);

            verify(monatsSaldoRepository).invalidiere(MITARBEITER_ID, 2025, 6);
        }

        @Test
        void invalidiereJahr_RuftRepositoryAuf() {
            monatsSaldoService.invalidiereJahr(MITARBEITER_ID, 2025);

            verify(monatsSaldoRepository).invalidiereJahr(MITARBEITER_ID, 2025);
        }

        @Test
        void invalidiereAlle_RuftRepositoryAuf() {
            monatsSaldoService.invalidiereAlle(MITARBEITER_ID);

            verify(monatsSaldoRepository).invalidiereAlle(MITARBEITER_ID);
        }

        @Test
        void invalidiereFuerDatum_ExtrahiertJahrUndMonat() {
            LocalDate datum = LocalDate.of(2025, 8, 15);

            monatsSaldoService.invalidiereFuerDatum(MITARBEITER_ID, datum);

            verify(monatsSaldoRepository).invalidiere(MITARBEITER_ID, 2025, 8);
        }

        @Test
        void invalidiereFuerDatum_MitNull_TutNichts() {
            monatsSaldoService.invalidiereFuerDatum(MITARBEITER_ID, null);

            verifyNoInteractions(monatsSaldoRepository);
        }

        @Test
        void invalidiereFuerDateTime_ExtrahiertJahrUndMonat() {
            LocalDateTime dateTime = LocalDateTime.of(2025, 3, 20, 14, 30);

            monatsSaldoService.invalidiereFuerDateTime(MITARBEITER_ID, dateTime);

            verify(monatsSaldoRepository).invalidiere(MITARBEITER_ID, 2025, 3);
        }

        @Test
        void invalidiereFuerDateTime_MitNull_TutNichts() {
            monatsSaldoService.invalidiereFuerDateTime(MITARBEITER_ID, null);

            verifyNoInteractions(monatsSaldoRepository);
        }

        @Test
        void invalidiereFuerDatum_Dezember_KorrekterMonat() {
            LocalDate datum = LocalDate.of(2025, 12, 31);

            monatsSaldoService.invalidiereFuerDatum(MITARBEITER_ID, datum);

            verify(monatsSaldoRepository).invalidiere(MITARBEITER_ID, 2025, 12);
        }

        @Test
        void invalidiereFuerDatum_Januar_KorrekterMonat() {
            LocalDate datum = LocalDate.of(2026, 1, 1);

            monatsSaldoService.invalidiereFuerDatum(MITARBEITER_ID, datum);

            verify(monatsSaldoRepository).invalidiere(MITARBEITER_ID, 2026, 1);
        }
    }

    // ==================== Entity-Tests ====================

    @Nested
    class MonatsSaldoEntity {

        @Test
        void getGesamtIst_SummiertAlleKomponenten() {
            MonatsSaldo ms = new MonatsSaldo();
            ms.setIstStunden(new BigDecimal("100.00"));
            ms.setAbwesenheitsStunden(new BigDecimal("16.00"));
            ms.setFeiertagsStunden(new BigDecimal("8.00"));
            ms.setKorrekturStunden(new BigDecimal("2.50"));

            BigDecimal gesamt = ms.getGesamtIst();

            assertEquals(0, new BigDecimal("126.50").compareTo(gesamt));
        }

        @Test
        void getDifferenz_SubtrahiertSollVonGesamt() {
            MonatsSaldo ms = new MonatsSaldo();
            ms.setIstStunden(new BigDecimal("150.00"));
            ms.setAbwesenheitsStunden(BigDecimal.ZERO);
            ms.setFeiertagsStunden(new BigDecimal("8.00"));
            ms.setKorrekturStunden(BigDecimal.ZERO);
            ms.setSollStunden(new BigDecimal("168.00"));

            BigDecimal diff = ms.getDifferenz();

            // GesamtIst = 158, Soll = 168, Differenz = -10
            assertEquals(0, new BigDecimal("-10.00").compareTo(diff));
        }

        @Test
        void getGesamtIst_MitNegativenKorrekturen() {
            MonatsSaldo ms = new MonatsSaldo();
            ms.setIstStunden(new BigDecimal("160.00"));
            ms.setAbwesenheitsStunden(BigDecimal.ZERO);
            ms.setFeiertagsStunden(BigDecimal.ZERO);
            ms.setKorrekturStunden(new BigDecimal("-5.00"));

            BigDecimal gesamt = ms.getGesamtIst();

            assertEquals(0, new BigDecimal("155.00").compareTo(gesamt));
        }

        @Test
        void standardwerte_SindZero() {
            MonatsSaldo ms = new MonatsSaldo();

            assertEquals(0, BigDecimal.ZERO.compareTo(ms.getIstStunden()));
            assertEquals(0, BigDecimal.ZERO.compareTo(ms.getSollStunden()));
            assertEquals(0, BigDecimal.ZERO.compareTo(ms.getAbwesenheitsStunden()));
            assertEquals(0, BigDecimal.ZERO.compareTo(ms.getFeiertagsStunden()));
            assertEquals(0, BigDecimal.ZERO.compareTo(ms.getKorrekturStunden()));
            assertTrue(ms.getGueltig());
        }

        @Test
        void onSave_SetztBerechnetAmWennNull() throws Exception {
            MonatsSaldo ms = new MonatsSaldo();
            assertNull(ms.getBerechnetAm());

            // onSave ist protected (@PrePersist/@PreUpdate), daher via Reflection aufrufen
            java.lang.reflect.Method onSave = MonatsSaldo.class.getDeclaredMethod("onSave");
            onSave.setAccessible(true);
            onSave.invoke(ms);

            assertNotNull(ms.getBerechnetAm());
        }

        @Test
        void onSave_UeberschreibtBerechnetAmNicht() throws Exception {
            MonatsSaldo ms = new MonatsSaldo();
            LocalDateTime original = LocalDateTime.of(2025, 1, 1, 12, 0);
            ms.setBerechnetAm(original);

            java.lang.reflect.Method onSave = MonatsSaldo.class.getDeclaredMethod("onSave");
            onSave.setAccessible(true);
            onSave.invoke(ms);

            assertEquals(original, ms.getBerechnetAm());
        }
    }

    // ==================== Fehlerfälle ====================

    @Nested
    class Fehlerfaelle {

        @Test
        void keinCacheUndMitarbeiterNichtGefunden_WirftException() {
            int jahr = 2025;
            int monat = 1;

            when(monatsSaldoRepository.findByMitarbeiterIdAndJahrAndMonat(MITARBEITER_ID, jahr, monat))
                    .thenReturn(Optional.empty());

            setupStandardMocks(jahr, monat);
            when(mitarbeiterRepository.findById(MITARBEITER_ID))
                    .thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () ->
                    monatsSaldoService.getOrBerechne(MITARBEITER_ID, jahr, monat));
        }

        @Test
        void leereZeitbuchungsliste_ErgibtNullIstStunden() {
            LocalDate heute = LocalDate.now();
            int jahr = heute.getYear();
            int monat = heute.getMonthValue();

            setupStandardMocks(jahr, monat);

            MonatsSaldo result = monatsSaldoService.getOrBerechne(MITARBEITER_ID, jahr, monat);

            assertEquals(0, BigDecimal.ZERO.compareTo(result.getIstStunden()));
        }

        @Test
        void leereKorrekturliste_ErgibtNullKorrekturStunden() {
            LocalDate heute = LocalDate.now();
            int jahr = heute.getYear();
            int monat = heute.getMonthValue();

            setupStandardMocks(jahr, monat);

            MonatsSaldo result = monatsSaldoService.getOrBerechne(MITARBEITER_ID, jahr, monat);

            assertEquals(0, BigDecimal.ZERO.compareTo(result.getKorrekturStunden()));
        }

        @Test
        void mehrereKorrekturen_WerdenKorrektSummiert() {
            LocalDate heute = LocalDate.now();
            int jahr = heute.getYear();
            int monat = heute.getMonthValue();
            LocalDate ersterTag = LocalDate.of(jahr, monat, 1);
            LocalDate letzterTag = YearMonth.of(jahr, monat).atEndOfMonth();

            ZeitkontoKorrektur k1 = erstelleKorrektur(ersterTag.plusDays(1), new BigDecimal("1.50"), false, KorrekturTyp.STUNDEN);
            ZeitkontoKorrektur k2 = erstelleKorrektur(ersterTag.plusDays(5), new BigDecimal("2.50"), false, KorrekturTyp.STUNDEN);
            ZeitkontoKorrektur k3 = erstelleKorrektur(ersterTag.plusDays(10), new BigDecimal("-1.00"), false, KorrekturTyp.STUNDEN);

            when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(zeitkontoService.berechneSollstundenFuerMonat(MITARBEITER_ID, jahr, monat))
                    .thenReturn(new BigDecimal("168.00"));
            when(abwesenheitRepository.sumStundenByMitarbeiterIdAndDatumBetween(
                    eq(MITARBEITER_ID), any(), any()))
                    .thenReturn(BigDecimal.ZERO);
            when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID)).thenReturn(testZeitkonto);
            when(feiertagService.istFeiertag(any())).thenReturn(false);
            when(korrekturRepository.findByMitarbeiterIdAndDatumBetween(
                    eq(MITARBEITER_ID), eq(ersterTag), eq(letzterTag)))
                    .thenReturn(List.of(k1, k2, k3));

            MonatsSaldo result = monatsSaldoService.getOrBerechne(MITARBEITER_ID, jahr, monat);

            // 1.50 + 2.50 - 1.00 = 3.00
            assertEquals(0, new BigDecimal("3.00").compareTo(result.getKorrekturStunden()));
        }

        @Test
        void cacheSpeicherung_UpdatetExistierendenEintrag() {
            int jahr = 2025;
            int monat = 1;
            MonatsSaldo existingInvalid = erstelleGueltigesCache(jahr, monat);
            existingInvalid.setGueltig(false);

            when(monatsSaldoRepository.findByMitarbeiterIdAndJahrAndMonat(MITARBEITER_ID, jahr, monat))
                    .thenReturn(Optional.of(existingInvalid));

            setupStandardMocks(jahr, monat);
            when(monatsSaldoRepository.save(any(MonatsSaldo.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            MonatsSaldo result = monatsSaldoService.getOrBerechne(MITARBEITER_ID, jahr, monat);

            // Bestätigen dass kein neuer Mitarbeiter gesucht wurde (bestehender Eintrag wird aktualisiert)
            verify(mitarbeiterRepository, never()).findById(anyLong());
            assertTrue(result.getGueltig());
        }
    }
}
