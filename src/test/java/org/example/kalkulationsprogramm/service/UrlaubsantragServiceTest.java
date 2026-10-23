package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für UrlaubsantragService.
 *
 * Schwerpunkt: {@code approveAntrag} bucht Urlaubsstunden über
 * {@code TagesSollService.arbeitsSollJeTag} (Zeitraum-Variante, Abschnitt 4
 * Nachbesserung Befund 2) statt direkt über das ZeitkontoVersion-Soll (E1 im Plan
 * "Langzeitkrankmeldung") — damit folgen Urlaubsstunden während einer
 * laufenden Wiedereingliederung dem Stufenplan, statt Phantom-Überstunden zu
 * erzeugen, und die Phasen/Feiertage werden einmal für den ganzen Zeitraum
 * geladen statt einmal je Tag. {@code pruefeHinweise} delegiert an
 * {@code LangzeitkrankmeldungService.pruefeUrlaubsHinweise}.
 *
 * Dummy-Daten (DSGVO): Max Mustermann, ID 1.
 */
@ExtendWith(MockitoExtension.class)
class UrlaubsantragServiceTest {

    @Mock
    private UrlaubsantragRepository repository;
    @Mock
    private MitarbeiterRepository mitarbeiterRepository;
    @Mock
    private AbwesenheitRepository abwesenheitRepository;
    @Mock
    private FeiertagService feiertagService;
    @Mock
    private ZeitkontoService zeitkontoService;
    @Mock
    private MonatsSaldoService monatsSaldoService;
    @Mock
    private ZeitkontoKorrekturService zeitkontoKorrekturService;
    @Mock
    private TagesSollService tagesSollService;
    @Mock
    private LangzeitkrankmeldungService langzeitkrankmeldungService;
    @Mock
    private WebPushService webPushService;
    @Mock
    private UrlaubUebertragRepository uebertragRepository;
    @Mock
    private UrlaubsverfallEinstellungService urlaubsverfallEinstellungen;

    /** Echte Konto-Rechnung auf den Mocks, damit Resturlaub und Antragsprüfung zusammen getestet werden. */
    private UrlaubskontoService urlaubskontoService;
    private UrlaubsantragService urlaubsantragService;

    private static final Long MITARBEITER_ID = 1L;
    private static final Long ANTRAG_ID = 100L;

    private Mitarbeiter testMitarbeiter;
    private ZeitkontoVersion testZeitkonto;

    @BeforeEach
    void setUp() {
        lenient().when(urlaubsverfallEinstellungen.regel())
                .thenReturn(UrlaubsverfallEinstellungService.Verfallsregel.STANDARD);
        lenient().when(urlaubsverfallEinstellungen.uebertragAbJahr()).thenReturn(2026);
        urlaubskontoService = new UrlaubskontoService(abwesenheitRepository, zeitkontoKorrekturService,
                tagesSollService, new UrlaubstageRechner(), uebertragRepository, urlaubsverfallEinstellungen,
                // Fester Tag vor allen Antragsdaten der Tests (10.01.2027).
                java.time.Clock.fixed(LocalDate.of(2027, 1, 10).atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant(), java.time.ZoneId.systemDefault()),
                // Kein Zeitkonto hinterlegt → 5-Tage-Woche, gesetzlicher Mindesturlaub 20 Tage.
                mock(ZeitkontoVersionRepository.class));
        urlaubsantragService = service(tagesSollService);
        testMitarbeiter = new Mitarbeiter();
        testMitarbeiter.setId(MITARBEITER_ID);
        testMitarbeiter.setVorname("Max");
        testMitarbeiter.setNachname("Mustermann");

        testZeitkonto = new ZeitkontoVersion();
        testZeitkonto.setMitarbeiter(testMitarbeiter);
        testZeitkonto.setGueltigVon(LocalDate.of(2000, 1, 1));
        testZeitkonto.setMontagStunden(new BigDecimal("8.00"));
        testZeitkonto.setDienstagStunden(new BigDecimal("8.00"));
        testZeitkonto.setMittwochStunden(new BigDecimal("8.00"));
        testZeitkonto.setDonnerstagStunden(new BigDecimal("8.00"));
        testZeitkonto.setFreitagStunden(new BigDecimal("8.00"));
        testZeitkonto.setSamstagStunden(BigDecimal.ZERO);
        testZeitkonto.setSonntagStunden(BigDecimal.ZERO);
    }

    private UrlaubsantragService service(TagesSollService soll) {
        return new UrlaubsantragService(repository, mitarbeiterRepository, abwesenheitRepository, feiertagService,
                zeitkontoService, monatsSaldoService, soll, langzeitkrankmeldungService, urlaubskontoService,
                webPushService);
    }

    private Urlaubsantrag antrag(LocalDate von, LocalDate bis) {
        Urlaubsantrag antrag = new Urlaubsantrag();
        antrag.setId(ANTRAG_ID);
        antrag.setMitarbeiter(testMitarbeiter);
        antrag.setVonDatum(von);
        antrag.setBisDatum(bis);
        antrag.setTyp(Urlaubsantrag.Typ.URLAUB);
        antrag.setStatus(Urlaubsantrag.Status.OFFEN);
        return antrag;
    }

    /** Stubbt die für jeden approveAntrag-Test nötige Grundverkabelung. */
    private void stubApproveGrunddaten() {
        when(zeitkontoService.versionenImZeitraum(eq(MITARBEITER_ID), any(), any())).thenReturn(List.of(testZeitkonto));
        when(abwesenheitRepository.existsByMitarbeiterIdAndDatumAndTyp(anyLong(), any(), any())).thenReturn(false);
        when(abwesenheitRepository.save(any(Abwesenheit.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.save(any(Urlaubsantrag.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Baut die Map, die {@code arbeitsSollJeTag} für einen durchgehenden Zeitraum liefert. */
    private Map<LocalDate, BigDecimal> jeTag(LocalDate von, LocalDate bis, BigDecimal wert) {
        Map<LocalDate, BigDecimal> map = new LinkedHashMap<>();
        for (LocalDate d = von; !d.isAfter(bis); d = d.plusDays(1)) {
            map.put(d, wert);
        }
        return map;
    }

    @Test
    void approveAntrag_montagBisFreitag_bucht8StundenJeTagUeberTagesSollService() {
        LocalDate von = LocalDate.of(2026, 6, 1);
        LocalDate bis = LocalDate.of(2026, 6, 5);
        stubApproveGrunddaten();
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag(von, bis)));
        when(feiertagService.istFeiertag(any(LocalDate.class))).thenReturn(false);
        when(tagesSollService.arbeitsSollJeTag(MITARBEITER_ID, von, bis))
                .thenReturn(jeTag(von, bis, new BigDecimal("8.00")));

        urlaubsantragService.approveAntrag(ANTRAG_ID);

        ArgumentCaptor<Abwesenheit> captor = ArgumentCaptor.forClass(Abwesenheit.class);
        verify(abwesenheitRepository, times(5)).save(captor.capture());
        for (Abwesenheit a : captor.getAllValues()) {
            assertEquals(0, new BigDecimal("8.00").compareTo(a.getStunden()),
                    "Tag " + a.getDatum() + " hatte " + a.getStunden() + " Stunden");
        }
    }

    @Test
    void approveAntrag_waehrendWiedereingliederung_buchtStufenplanStundenStattVollemSoll() {
        LocalDate von = LocalDate.of(2026, 6, 1);
        LocalDate bis = LocalDate.of(2026, 6, 5);
        stubApproveGrunddaten();
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag(von, bis)));
        when(feiertagService.istFeiertag(any(LocalDate.class))).thenReturn(false);
        when(tagesSollService.arbeitsSollJeTag(MITARBEITER_ID, von, bis))
                .thenReturn(jeTag(von, bis, new BigDecimal("2.00")));

        urlaubsantragService.approveAntrag(ANTRAG_ID);

        ArgumentCaptor<Abwesenheit> captor = ArgumentCaptor.forClass(Abwesenheit.class);
        verify(abwesenheitRepository, times(5)).save(captor.capture());
        for (Abwesenheit a : captor.getAllValues()) {
            assertEquals(0, new BigDecimal("2.00").compareTo(a.getStunden()),
                    "Tag " + a.getDatum() + " hatte " + a.getStunden() + " Stunden");
        }
    }

    @Test
    void approveAntrag_feiertagWirdUebersprungenTrotzWertInDerMap() {
        // Mo 2026-06-01 bis Fr 2026-06-05, Mittwoch (2026-06-03) ist Feiertag.
        // Die Map liefert fuer JEDEN Tag inkl. Feiertag einen Wert > 0 - die
        // Sperre muss also wirklich am feiertagService.istFeiertag()-Check in
        // approveAntrag haengen, nicht zufaellig an einer leeren Map.
        LocalDate von = LocalDate.of(2026, 6, 1);
        LocalDate bis = LocalDate.of(2026, 6, 5);
        LocalDate feiertag = LocalDate.of(2026, 6, 3);
        stubApproveGrunddaten();
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag(von, bis)));
        when(feiertagService.istFeiertag(any(LocalDate.class))).thenAnswer(inv -> inv.getArgument(0).equals(feiertag));
        when(tagesSollService.arbeitsSollJeTag(MITARBEITER_ID, von, bis))
                .thenReturn(jeTag(von, bis, new BigDecimal("8.00")));

        urlaubsantragService.approveAntrag(ANTRAG_ID);

        ArgumentCaptor<Abwesenheit> captor = ArgumentCaptor.forClass(Abwesenheit.class);
        verify(abwesenheitRepository, times(4)).save(captor.capture());
        assertFalse(captor.getAllValues().stream().anyMatch(a -> a.getDatum().equals(feiertag)),
                "Der Feiertag selbst darf keine Abwesenheit erzeugen");
    }

    @Test
    void approveAntrag_samstagMitSollKostetUrlaub_sonntagOhneSollNicht() {
        // Fr 2026-06-05 bis Mo 2026-06-08: Samstag hat Soll (Samstagsarbeit), Sonntag nicht (Issue #270).
        LocalDate von = LocalDate.of(2026, 6, 5);
        LocalDate bis = LocalDate.of(2026, 6, 8);
        stubApproveGrunddaten();
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag(von, bis)));
        when(feiertagService.istFeiertag(any(LocalDate.class))).thenReturn(false);
        Map<LocalDate, BigDecimal> soll = jeTag(von, bis, new BigDecimal("8.00"));
        soll.put(LocalDate.of(2026, 6, 6), new BigDecimal("4.00"));
        soll.put(LocalDate.of(2026, 6, 7), BigDecimal.ZERO);
        when(tagesSollService.arbeitsSollJeTag(MITARBEITER_ID, von, bis)).thenReturn(soll);

        urlaubsantragService.approveAntrag(ANTRAG_ID);

        ArgumentCaptor<Abwesenheit> captor = ArgumentCaptor.forClass(Abwesenheit.class);
        verify(abwesenheitRepository, times(3)).save(captor.capture());
        assertEquals(List.of(LocalDate.of(2026, 6, 5), LocalDate.of(2026, 6, 6), LocalDate.of(2026, 6, 8)),
                captor.getAllValues().stream().map(Abwesenheit::getDatum).toList());
        assertEquals(new BigDecimal("4.00"), captor.getAllValues().get(1).getStunden());
    }

    @Test
    void approveAntrag_wochenendeOhneArbeitszeitIstKeinFehler() {
        // Arbeitszeit endet Freitag, die nächste beginnt Montag: das Wochenende dazwischen sperrt nichts.
        LocalDate von = LocalDate.of(2026, 6, 5);
        LocalDate bis = LocalDate.of(2026, 6, 8);
        testZeitkonto.setGueltigBis(LocalDate.of(2026, 6, 5));
        ZeitkontoVersion naechste = new ZeitkontoVersion();
        naechste.setGueltigVon(LocalDate.of(2026, 6, 8));
        when(zeitkontoService.versionenImZeitraum(MITARBEITER_ID, von, bis)).thenReturn(List.of(testZeitkonto, naechste));
        when(abwesenheitRepository.existsByMitarbeiterIdAndDatumAndTyp(anyLong(), any(), any())).thenReturn(false);
        when(abwesenheitRepository.save(any(Abwesenheit.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.save(any(Urlaubsantrag.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag(von, bis)));
        when(feiertagService.istFeiertag(any(LocalDate.class))).thenReturn(false);
        Map<LocalDate, BigDecimal> soll = jeTag(von, bis, BigDecimal.ZERO);
        soll.put(von, new BigDecimal("8.00"));
        soll.put(bis, new BigDecimal("8.00"));
        when(tagesSollService.arbeitsSollJeTag(MITARBEITER_ID, von, bis)).thenReturn(soll);

        assertDoesNotThrow(() -> urlaubsantragService.approveAntrag(ANTRAG_ID));
        verify(abwesenheitRepository, times(2)).save(any(Abwesenheit.class));
    }

    @Test
    void approveAntrag_tagFehltInDerMap_erzeugtKeineNPEUndKeineAbwesenheit() {
        // Sicherheitsnetz aus der Nachbesserung (Befund 2): liefert die Map fuer
        // einen Werktag keinen Eintrag (z.B. weil TagesSollService ihn als 0
        // behandelt und gar nicht erst einfuegt), darf approveAntrag NICHT mit
        // NPE abbrechen, sondern muss den Tag wie "kein Soll" behandeln.
        LocalDate von = LocalDate.of(2026, 6, 1);
        LocalDate bis = LocalDate.of(2026, 6, 5);
        LocalDate luecke = LocalDate.of(2026, 6, 3);
        stubApproveGrunddaten();
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag(von, bis)));
        when(feiertagService.istFeiertag(any(LocalDate.class))).thenReturn(false);
        Map<LocalDate, BigDecimal> map = jeTag(von, bis, new BigDecimal("8.00"));
        map.remove(luecke);
        when(tagesSollService.arbeitsSollJeTag(MITARBEITER_ID, von, bis)).thenReturn(map);

        assertDoesNotThrow(() -> urlaubsantragService.approveAntrag(ANTRAG_ID));

        ArgumentCaptor<Abwesenheit> captor = ArgumentCaptor.forClass(Abwesenheit.class);
        verify(abwesenheitRepository, times(4)).save(captor.capture());
        assertFalse(captor.getAllValues().stream().anyMatch(a -> a.getDatum().equals(luecke)),
                "Fuer den Tag ohne Map-Eintrag darf keine Abwesenheit entstehen");
    }

    @Test
    void approveAntrag_laedtVersionenGenauEinmal_keinN1InDerSchleife() {
        LocalDate von = LocalDate.of(2026, 6, 1);
        LocalDate bis = LocalDate.of(2026, 6, 5);
        stubApproveGrunddaten();
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag(von, bis)));
        when(feiertagService.istFeiertag(any(LocalDate.class))).thenReturn(false);
        when(tagesSollService.arbeitsSollJeTag(MITARBEITER_ID, von, bis))
                .thenReturn(jeTag(von, bis, new BigDecimal("8.00")));

        urlaubsantragService.approveAntrag(ANTRAG_ID);

        verify(zeitkontoService, times(1)).versionenImZeitraum(MITARBEITER_ID, von, bis);
    }

    @Test
    void approveAntrag_ruftArbeitsSollJeTagGenauEinmalFuerDenGesamtenZeitraumAuf_keinN1ProTag() {
        // Befund 2 (Abschnitt 4, Nachbesserung): vorher ein arbeitsSoll-Aufruf
        // je Werktag (hier 5), jetzt genau ein arbeitsSollJeTag-Aufruf fuer den
        // kompletten Zeitraum - arbeitsSoll darf gar nicht mehr aufgerufen werden.
        LocalDate von = LocalDate.of(2026, 6, 1);
        LocalDate bis = LocalDate.of(2026, 6, 5);
        stubApproveGrunddaten();
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag(von, bis)));
        when(feiertagService.istFeiertag(any(LocalDate.class))).thenReturn(false);
        when(tagesSollService.arbeitsSollJeTag(MITARBEITER_ID, von, bis))
                .thenReturn(jeTag(von, bis, new BigDecimal("8.00")));

        urlaubsantragService.approveAntrag(ANTRAG_ID);

        verify(tagesSollService, times(1)).arbeitsSollJeTag(MITARBEITER_ID, von, bis);
        verify(tagesSollService, never()).arbeitsSoll(any(), any());
    }

    @Test
    void pruefeHinweise_delegiertAnLangzeitkrankmeldungService() {
        LocalDate von = LocalDate.of(2026, 3, 1);
        LocalDate bis = LocalDate.of(2026, 3, 5);
        List<String> erwartet = List.of(
                "In diesem Zeitraum läuft eine Krankmeldung (seit 2026-02-01). Bitte prüfen, ob der Urlaub wirklich passt.");
        when(langzeitkrankmeldungService.pruefeUrlaubsHinweise(MITARBEITER_ID, von, bis)).thenReturn(erwartet);

        List<String> ergebnis = urlaubsantragService.pruefeHinweise(MITARBEITER_ID, von, bis);

        assertEquals(erwartet, ergebnis);
        verify(langzeitkrankmeldungService).pruefeUrlaubsHinweise(MITARBEITER_ID, von, bis);
    }

    @Test
    void pruefeHinweise_ohneUeberlappendeKrankmeldung_liefertLeereListe() {
        LocalDate von = LocalDate.of(2026, 3, 1);
        LocalDate bis = LocalDate.of(2026, 3, 5);
        when(langzeitkrankmeldungService.pruefeUrlaubsHinweise(MITARBEITER_ID, von, bis)).thenReturn(List.of());

        List<String> ergebnis = urlaubsantragService.pruefeHinweise(MITARBEITER_ID, von, bis);

        assertTrue(ergebnis.isEmpty());
    }
    @Test
    void genehmigungUeberVertragswechsel_buchtHistorischeUndNeueStunden_trotzHeuteOhneKonto() {
        LocalDate von = LocalDate.of(2026, 6, 1);
        LocalDate bis = von.plusDays(4);
        LocalDate wechsel = von.plusDays(2);
        testMitarbeiter.setFuehrtZeitkonto(false);
        testZeitkonto.setGueltigBis(wechsel.minusDays(1));
        ZeitkontoVersion neu = new ZeitkontoVersion();
        neu.setGueltigVon(wechsel);
        neu.setGueltigBis(bis);
        neu.setMittwochStunden(new BigDecimal("6.00"));
        neu.setDonnerstagStunden(new BigDecimal("6.00"));
        neu.setFreitagStunden(new BigDecimal("6.00"));
        List<ZeitkontoVersion> versionen = List.of(testZeitkonto, neu);
        ZeitkontoVersionRepository versionRepository = mock(ZeitkontoVersionRepository.class);
        when(versionRepository.findImZeitraum(MITARBEITER_ID, von, bis)).thenReturn(versionen);
        when(zeitkontoService.versionenImZeitraum(MITARBEITER_ID, von, bis)).thenReturn(versionen);
        TagesSollService realesSoll = new TagesSollService(feiertagService,
                mock(LangzeitkrankmeldungPhaseRepository.class), versionRepository);
        UrlaubsantragService service = service(realesSoll);
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag(von, bis)));

        service.approveAntrag(ANTRAG_ID);

        ArgumentCaptor<Abwesenheit> captor = ArgumentCaptor.forClass(Abwesenheit.class);
        verify(abwesenheitRepository, times(5)).save(captor.capture());
        assertEquals(List.of(new BigDecimal("8.00"), new BigDecimal("8.00"), new BigDecimal("6.00"),
                new BigDecimal("6.00"), new BigDecimal("6.00")),
                captor.getAllValues().stream().map(Abwesenheit::getStunden).toList());
    }

    @Test
    void fehlendeVersionMittenImUrlaub_keineTeilbuchungUndAntragBleibtOffen() {
        LocalDate von = LocalDate.of(2026, 6, 1);
        LocalDate bis = von.plusDays(4);
        testZeitkonto.setGueltigBis(von.plusDays(1));
        Urlaubsantrag antrag = antrag(von, bis);
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag));
        when(zeitkontoService.versionenImZeitraum(MITARBEITER_ID, von, bis)).thenReturn(List.of(testZeitkonto));

        IllegalStateException fehler = assertThrows(IllegalStateException.class,
                () -> urlaubsantragService.approveAntrag(ANTRAG_ID));

        assertTrue(fehler.getMessage().contains("noch keine Arbeitszeit hinterlegt"));
        assertTrue(fehler.getMessage().contains(von.plusDays(2).toString()));
        assertEquals(Urlaubsantrag.Status.OFFEN, antrag.getStatus());
        verify(abwesenheitRepository, never()).save(any());
        verify(repository, never()).save(any());
        verifyNoInteractions(monatsSaldoService, tagesSollService);
    }

    @Test
    void genehmigung_bestehendeUrlaubsgutschriftWirdNichtUmgeschrieben() {
        LocalDate tag = LocalDate.of(2026, 6, 1);
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag(tag, tag)));
        when(zeitkontoService.versionenImZeitraum(MITARBEITER_ID, tag, tag)).thenReturn(List.of(testZeitkonto));
        when(tagesSollService.arbeitsSollJeTag(MITARBEITER_ID, tag, tag))
                .thenReturn(Map.of(tag, new BigDecimal("6.00")));
        when(abwesenheitRepository.existsByMitarbeiterIdAndDatumAndTyp(MITARBEITER_ID, tag, AbwesenheitsTyp.URLAUB))
                .thenReturn(true);

        urlaubsantragService.approveAntrag(ANTRAG_ID);

        verify(abwesenheitRepository, never()).save(any());
    }


    // ==================== Entscheidung, Benachrichtigung (Issue #270, Punkt 3) ====================

    @Test
    void genehmigung_merktEntscheidungUndSchicktPushAnMitarbeiter() {
        LocalDate tag = LocalDate.of(2026, 8, 3);
        Urlaubsantrag antrag = antrag(tag, tag);
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag));
        when(zeitkontoService.versionenImZeitraum(MITARBEITER_ID, tag, tag)).thenReturn(List.of(testZeitkonto));
        when(tagesSollService.arbeitsSollJeTag(MITARBEITER_ID, tag, tag)).thenReturn(Map.of(tag, new BigDecimal("8.00")));
        when(repository.save(any(Urlaubsantrag.class))).thenAnswer(inv -> inv.getArgument(0));

        Urlaubsantrag ergebnis = urlaubsantragService.approveAntrag(ANTRAG_ID);

        assertEquals(Urlaubsantrag.Status.GENEHMIGT, ergebnis.getStatus());
        assertNotNull(ergebnis.getEntschiedenAm());
        assertFalse(ergebnis.isEntscheidungGesehen());
        verify(webPushService).notifyMitarbeiter(eq(MITARBEITER_ID), eq("Antrag genehmigt"),
                eq("Dein Urlaub vom 03.08.2026 bis 03.08.2026 wurde genehmigt."), eq("/zeiterfassung/urlaub"));
    }

    @Test
    void genehmigung_imFestgeschriebenenMonat_wirft409OhneBuchung() {
        LocalDate tag = LocalDate.of(2026, 8, 3);
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag(tag, tag)));
        when(zeitkontoService.versionenImZeitraum(MITARBEITER_ID, tag, tag)).thenReturn(List.of(testZeitkonto));
        when(monatsSaldoService.isMonatFestgeschrieben(MITARBEITER_ID, 2026, 8)).thenReturn(true);

        org.springframework.web.server.ResponseStatusException fehler = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> urlaubsantragService.approveAntrag(ANTRAG_ID));

        assertEquals(409, fehler.getStatusCode().value());
        assertTrue(fehler.getReason().contains("08/2026"));
        verify(abwesenheitRepository, never()).save(any());
        verifyNoInteractions(webPushService);
    }

    @Test
    void ablehnung_nurOffenerAntrag_merktEntscheidungUndSchicktPush() {
        Urlaubsantrag antrag = antrag(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 7));
        antrag.setTyp(Urlaubsantrag.Typ.ZEITAUSGLEICH);
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag));
        when(repository.save(any(Urlaubsantrag.class))).thenAnswer(inv -> inv.getArgument(0));

        Urlaubsantrag ergebnis = urlaubsantragService.rejectAntrag(ANTRAG_ID);

        assertEquals(Urlaubsantrag.Status.ABGELEHNT, ergebnis.getStatus());
        assertFalse(ergebnis.isEntscheidungGesehen());
        verify(webPushService).notifyMitarbeiter(eq(MITARBEITER_ID), eq("Antrag abgelehnt"),
                eq("Dein Zeitausgleich vom 03.08.2026 bis 07.08.2026 wurde abgelehnt."), any());

        antrag.setStatus(Urlaubsantrag.Status.GENEHMIGT);
        IllegalStateException fehler = assertThrows(IllegalStateException.class,
                () -> urlaubsantragService.rejectAntrag(ANTRAG_ID));
        assertTrue(fehler.getMessage().contains("Nur offene"));
    }

    @Test
    void antragMitArtikel_kenntAlleTypen() {
        assertEquals("Dein Urlaub", UrlaubsantragService.antragMitArtikel(null));
        assertEquals("Deine Krankmeldung", UrlaubsantragService.antragMitArtikel(Urlaubsantrag.Typ.KRANKHEIT));
        assertEquals("Deine Fortbildung", UrlaubsantragService.antragMitArtikel(Urlaubsantrag.Typ.FORTBILDUNG));
        assertEquals("Dein Zeitausgleich", UrlaubsantragService.antragMitArtikel(Urlaubsantrag.Typ.ZEITAUSGLEICH));
        assertEquals("Dein Antrag", UrlaubsantragService.antragMitArtikel(Urlaubsantrag.Typ.ARBEIT));
    }

    @Test
    void neueEntscheidungen_liefernDtoOhneMitarbeiter_undFragenAuchStornoAb() {
        Urlaubsantrag entschieden = antrag(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 4));
        entschieden.setStatus(Urlaubsantrag.Status.STORNIERT);
        when(repository.findUngeseheneEntscheidungen(eq(MITARBEITER_ID), any())).thenReturn(List.of(entschieden));

        List<org.example.kalkulationsprogramm.dto.UrlaubsentscheidungDto> dtos =
                urlaubsantragService.neueEntscheidungen(MITARBEITER_ID);

        assertEquals(1, dtos.size());
        assertEquals(ANTRAG_ID, dtos.get(0).id());
        assertEquals(Urlaubsantrag.Status.STORNIERT, dtos.get(0).status());
        assertEquals(LocalDate.of(2026, 8, 4), dtos.get(0).bisDatum());
        verify(repository).findUngeseheneEntscheidungen(eq(MITARBEITER_ID),
                eq(java.util.EnumSet.of(Urlaubsantrag.Status.GENEHMIGT, Urlaubsantrag.Status.ABGELEHNT,
                        Urlaubsantrag.Status.STORNIERT)));
    }

    // ==================== Storno (Issue #270, Punkt 1) ====================

    @Test
    void storno_genehmigterAntrag_loeschtTageUndSetztStorniert() {
        Urlaubsantrag antrag = antrag(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 7));
        antrag.setStatus(Urlaubsantrag.Status.GENEHMIGT);
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag));
        when(repository.save(any(Urlaubsantrag.class))).thenAnswer(inv -> inv.getArgument(0));

        Urlaubsantrag ergebnis = urlaubsantragService.stornoAntrag(ANTRAG_ID);

        assertEquals(Urlaubsantrag.Status.STORNIERT, ergebnis.getStatus());
        assertFalse(ergebnis.isEntscheidungGesehen());
        assertNotNull(ergebnis.getEntschiedenAm());
        verify(abwesenheitRepository).deleteByUrlaubsantragId(ANTRAG_ID);
        verify(monatsSaldoService).invalidiereMonat(MITARBEITER_ID, 2026, 8);
        verify(webPushService).notifyMitarbeiter(eq(MITARBEITER_ID), eq("Antrag vom Büro storniert"),
                eq("Dein Urlaub vom 03.08.2026 bis 07.08.2026 wurde vom Büro storniert."), any());
    }

    @Test
    void storno_genehmigterAntragImFestgeschriebenenMonat_wirft409() {
        Urlaubsantrag antrag = antrag(LocalDate.of(2026, 7, 30), LocalDate.of(2026, 8, 3));
        antrag.setStatus(Urlaubsantrag.Status.GENEHMIGT);
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag));
        when(monatsSaldoService.isMonatFestgeschrieben(MITARBEITER_ID, 2026, 7)).thenReturn(true);

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> urlaubsantragService.stornoAntrag(ANTRAG_ID));

        verify(abwesenheitRepository, never()).deleteByUrlaubsantragId(any());
        assertEquals(Urlaubsantrag.Status.GENEHMIGT, antrag.getStatus());
    }

    @Test
    void storno_bereitsStorniertOderAbgelehnt_wirftKlartext() {
        Urlaubsantrag antrag = antrag(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 3));
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag));

        antrag.setStatus(Urlaubsantrag.Status.STORNIERT);
        assertEquals("Dieser Antrag ist bereits storniert.", assertThrows(IllegalStateException.class,
                () -> urlaubsantragService.stornoAntrag(ANTRAG_ID)).getMessage());

        antrag.setStatus(Urlaubsantrag.Status.ABGELEHNT);
        assertEquals("Ein abgelehnter Antrag kann nicht storniert werden.", assertThrows(IllegalStateException.class,
                () -> urlaubsantragService.stornoAntrag(ANTRAG_ID)).getMessage());
    }

    @Test
    void zurueckziehen_eigenerOffenerAntrag_ohneMonatspruefung() {
        Urlaubsantrag antrag = antrag(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 3));
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag));
        when(repository.save(any(Urlaubsantrag.class))).thenAnswer(inv -> inv.getArgument(0));

        Urlaubsantrag ergebnis = urlaubsantragService.ziehEigenenAntragZurueck(ANTRAG_ID, MITARBEITER_ID);

        assertEquals(Urlaubsantrag.Status.STORNIERT, ergebnis.getStatus());
        assertTrue(ergebnis.isEntscheidungGesehen(), "Selbst zurückgezogen – keine Nachricht in der Glocke");
        verify(monatsSaldoService, never()).isMonatFestgeschrieben(anyLong(), anyInt(), anyInt());
        verifyNoInteractions(webPushService);
    }

    @Test
    void zurueckziehen_fremderAntrag403_genehmigterAntragNurImBuero() {
        Urlaubsantrag antrag = antrag(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 3));
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag));

        org.springframework.web.server.ResponseStatusException fremd = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> urlaubsantragService.ziehEigenenAntragZurueck(ANTRAG_ID, 99L));
        assertEquals(403, fremd.getStatusCode().value());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> urlaubsantragService.ziehEigenenAntragZurueck(ANTRAG_ID, null));

        antrag.setStatus(Urlaubsantrag.Status.GENEHMIGT);
        IllegalStateException genehmigt = assertThrows(IllegalStateException.class,
                () -> urlaubsantragService.ziehEigenenAntragZurueck(ANTRAG_ID, MITARBEITER_ID));
        assertTrue(genehmigt.getMessage().contains("Büro"));
        verify(abwesenheitRepository, never()).deleteByUrlaubsantragId(any());
    }

    @Test
    void storno_unbekannterAntrag_wirftIllegalArgument() {
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> urlaubsantragService.stornoAntrag(ANTRAG_ID));
        assertThrows(IllegalArgumentException.class,
                () -> urlaubsantragService.ziehEigenenAntragZurueck(ANTRAG_ID, MITARBEITER_ID));
    }

    // ==================== Resturlaub mit halben Tagen (Issue #269, Punkt 5) ====================

    @Test
    void resturlaub_halberTagZaehltHalb_undKorrekturNichtAbgeschnitten() {
        testMitarbeiter.setJahresUrlaub(30);
        LocalDate montag = LocalDate.of(2026, 3, 2);
        LocalDate dienstag = montag.plusDays(1);
        Abwesenheit ganz = new Abwesenheit();
        ganz.setTyp(AbwesenheitsTyp.URLAUB);
        ganz.setDatum(montag);
        ganz.setStunden(new BigDecimal("8.00"));
        Abwesenheit halb = new Abwesenheit();
        halb.setTyp(AbwesenheitsTyp.URLAUB);
        halb.setDatum(dienstag);
        halb.setStunden(new BigDecimal("4.00"));
        when(mitarbeiterRepository.findById(MITARBEITER_ID)).thenReturn(Optional.of(testMitarbeiter));
        when(abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(eq(MITARBEITER_ID), any(), any()))
                .thenReturn(List.of(ganz, halb));
        when(tagesSollService.arbeitsSollJeTag(eq(MITARBEITER_ID), any(), any()))
                .thenReturn(Map.of(montag, new BigDecimal("8.00"), dienstag, new BigDecimal("8.00")));
        when(zeitkontoKorrekturService.summiereAktiveUrlaubsKorrekturen(MITARBEITER_ID, 2026))
                .thenReturn(new BigDecimal("0.5"));

        BigDecimal rest = urlaubsantragService.getResturlaub(MITARBEITER_ID, 2026);

        assertEquals(0, new BigDecimal("29").compareTo(rest), "30 - 1,5 + 0,5 = 29");
    }

    @Test
    void antrag_mehrTageAlsHalbeRestTage_wirdMitKommaMeldungAbgelehnt() {
        // Unter dem gesetzlichen Minimum geht nicht: 20 Tage, davon 19 per Korrektur abgezogen → 1 Tag.
        testMitarbeiter.setJahresUrlaub(20);
        when(zeitkontoKorrekturService.summiereAktiveUrlaubsKorrekturen(MITARBEITER_ID, 2026))
                .thenReturn(new BigDecimal("-19"));
        LocalDate montag = LocalDate.of(2026, 3, 2);
        Abwesenheit halb = new Abwesenheit();
        halb.setTyp(AbwesenheitsTyp.URLAUB);
        halb.setDatum(montag);
        halb.setStunden(new BigDecimal("4.00"));
        when(mitarbeiterRepository.findById(MITARBEITER_ID)).thenReturn(Optional.of(testMitarbeiter));
        when(abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(eq(MITARBEITER_ID), any(), any()))
                .thenReturn(List.of(halb));
        when(tagesSollService.arbeitsSollJeTag(eq(MITARBEITER_ID), any(), any()))
                .thenReturn(Map.of(montag, new BigDecimal("8.00")));

        LocalDate mittwoch = LocalDate.of(2026, 3, 4);
        IllegalStateException fehler = assertThrows(IllegalStateException.class,
                () -> urlaubsantragService.createAntrag(MITARBEITER_ID, mittwoch, mittwoch.plusDays(1), null,
                        Urlaubsantrag.Typ.URLAUB));

        assertEquals("Nicht genügend Urlaubstage. Verbleibend: 0,5, Beantragt: 2", fehler.getMessage());
    }

    @Test
    void antrag_ueberDenVerfallstagHinweg_nutztDenVorjahresrestNurBisZumVerfall() {
        // 2 Tage Anspruch (20 − 18 per Korrektur) + 5 Tage aus dem Vorjahr = 7 verfügbar. Antrag
        // 31.03.–07.04.2027 = 6 Arbeitstage: nur der 31.03. darf den Vorjahresrest nutzen, die
        // übrigen 4 Tage Rest verfallen danach → 2 + 5 − 4 − 6 = −3, also abgelehnt.
        testMitarbeiter.setJahresUrlaub(20);
        when(zeitkontoKorrekturService.summiereAktiveUrlaubsKorrekturen(MITARBEITER_ID, 2027))
                .thenReturn(new BigDecimal("-18"));
        UrlaubUebertrag rest = new UrlaubUebertrag();
        rest.setTage(new BigDecimal("5"));
        when(mitarbeiterRepository.findById(MITARBEITER_ID)).thenReturn(Optional.of(testMitarbeiter));
        when(uebertragRepository.findByMitarbeiterIdAndJahr(MITARBEITER_ID, 2027)).thenReturn(Optional.of(rest));

        IllegalStateException fehler = assertThrows(IllegalStateException.class,
                () -> urlaubsantragService.createAntrag(MITARBEITER_ID, LocalDate.of(2027, 3, 31),
                        LocalDate.of(2027, 4, 7), null, Urlaubsantrag.Typ.URLAUB));

        assertEquals("Nicht genügend Urlaubstage. Verbleibend: 7, Beantragt: 6. "
                + "Resturlaub aus dem Vorjahr kann nur bis 31.03.2027 genommen werden.", fehler.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void einzelneEntscheidungGesehen_nurEigene_undNurEinmalSpeichern() {
        Urlaubsantrag antrag = antrag(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 3));
        antrag.setEntscheidungGesehen(false);
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag));

        org.springframework.web.server.ResponseStatusException fremd = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> urlaubsantragService.eigeneEntscheidungGesehen(ANTRAG_ID, 99L));
        assertEquals(403, fremd.getStatusCode().value());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> urlaubsantragService.eigeneEntscheidungGesehen(ANTRAG_ID, null));
        assertFalse(antrag.isEntscheidungGesehen());

        urlaubsantragService.eigeneEntscheidungGesehen(ANTRAG_ID, MITARBEITER_ID);
        urlaubsantragService.entscheidungGesehenImBuero(ANTRAG_ID);

        assertTrue(antrag.isEntscheidungGesehen());
        verify(repository, times(1)).save(antrag);
    }

    @Test
    void einzelneEntscheidungGesehen_unbekannterAntrag() {
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> urlaubsantragService.eigeneEntscheidungGesehen(ANTRAG_ID, MITARBEITER_ID));
        assertThrows(IllegalArgumentException.class,
                () -> urlaubsantragService.entscheidungGesehenImBuero(ANTRAG_ID));
    }

    @Test
    void push_imLaufenderTransaktion_gehtErstNachDemCommitRaus() {
        Urlaubsantrag antrag = antrag(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 3));
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag));
        when(repository.save(any(Urlaubsantrag.class))).thenAnswer(inv -> inv.getArgument(0));
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            urlaubsantragService.rejectAntrag(ANTRAG_ID);
            verifyNoInteractions(webPushService);

            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

            verify(webPushService).notifyMitarbeiter(eq(MITARBEITER_ID), eq("Antrag abgelehnt"), any(), any());
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void resturlaub_geschaeftsfuehrer_zaehltGanzeTageOhneSoll() {
        testMitarbeiter.setIstGeschaeftsfuehrer(true);
        testMitarbeiter.setJahresUrlaub(30);
        Abwesenheit gf = new Abwesenheit();
        gf.setTyp(AbwesenheitsTyp.URLAUB);
        gf.setDatum(LocalDate.of(2026, 3, 2));
        gf.setStunden(BigDecimal.ZERO);
        when(mitarbeiterRepository.findById(MITARBEITER_ID)).thenReturn(Optional.of(testMitarbeiter));
        when(abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(eq(MITARBEITER_ID), any(), any()))
                .thenReturn(List.of(gf));

        BigDecimal rest = urlaubsantragService.getResturlaub(MITARBEITER_ID, 2026);

        assertEquals(0, new BigDecimal("29").compareTo(rest));
        verifyNoInteractions(tagesSollService);
    }

    @Test
    void push_fehlerNachDemCommit_wirdGeschlucktUndGeloggt() {
        Urlaubsantrag antrag = antrag(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 3));
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag));
        when(repository.save(any(Urlaubsantrag.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Commit der Push-Transaktion gescheitert"))
                .when(webPushService).notifyMitarbeiter(any(), any(), any(), any());
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            urlaubsantragService.rejectAntrag(ANTRAG_ID);

            assertDoesNotThrow(() -> org.springframework.transaction.support.TransactionSynchronizationManager
                    .getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit));
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ==================== Vorschau für die Handy-App (Issue #270) ====================

    @Test
    void vorschau_samstagMitSollZaehlt_feiertagUndTagOhneSollNicht() {
        // Mi 03.03. bis So 07.03.2027: Do ist Feiertag, Sa hat 4 h Soll, So keins.
        LocalDate von = LocalDate.of(2027, 3, 3);
        LocalDate bis = LocalDate.of(2027, 3, 7);
        testMitarbeiter.setJahresUrlaub(30);
        when(mitarbeiterRepository.findById(MITARBEITER_ID)).thenReturn(Optional.of(testMitarbeiter));
        when(zeitkontoService.versionenImZeitraum(MITARBEITER_ID, von, bis)).thenReturn(List.of(testZeitkonto));
        Map<LocalDate, BigDecimal> soll = jeTag(von, bis, new BigDecimal("8.00"));
        soll.put(LocalDate.of(2027, 3, 6), new BigDecimal("4.00"));
        soll.put(LocalDate.of(2027, 3, 7), BigDecimal.ZERO);
        when(tagesSollService.arbeitsSollJeTag(eq(MITARBEITER_ID), any(), any())).thenReturn(soll);
        when(feiertagService.getFeiertageZwischen(von, bis))
                .thenReturn(List.of(new Feiertag(LocalDate.of(2027, 3, 4), "Testfeiertag", "BY")));

        UrlaubsantragService.UrlaubsVorschau vorschau = urlaubsantragService.vorschau(MITARBEITER_ID, von, bis);

        assertEquals(0, new BigDecimal("3").compareTo(vorschau.arbeitstage()), "Mi, Fr und Sa kosten Urlaub");
        assertNotNull(vorschau.verbleibend());
        assertTrue(vorschau.reicht());
        verify(repository, never()).save(any());
    }

    @Test
    void vorschau_reichtNicht_wennMehrAlsDerResturlaub() {
        // Zwei Monate Mo–Fr (ohne Arbeitszeit-Version gilt Mo–Fr) sind mehr als ein Jahresurlaub.
        LocalDate von = LocalDate.of(2027, 3, 1);
        LocalDate bis = LocalDate.of(2027, 4, 30);
        testMitarbeiter.setJahresUrlaub(20);
        when(mitarbeiterRepository.findById(MITARBEITER_ID)).thenReturn(Optional.of(testMitarbeiter));

        UrlaubsantragService.UrlaubsVorschau vorschau = urlaubsantragService.vorschau(MITARBEITER_ID, von, bis);

        assertFalse(vorschau.reicht(), vorschau.toString());
        assertEquals(0, new BigDecimal("45").compareTo(vorschau.arbeitstage()), vorschau.toString());
        assertTrue(vorschau.arbeitstage().compareTo(vorschau.verbleibend()) > 0);
    }

    @Test
    void vorschau_geschaeftsfuehrer_ohneKontingent() {
        testMitarbeiter.setIstGeschaeftsfuehrer(true);
        when(mitarbeiterRepository.findById(MITARBEITER_ID)).thenReturn(Optional.of(testMitarbeiter));

        UrlaubsantragService.UrlaubsVorschau vorschau = urlaubsantragService.vorschau(MITARBEITER_ID,
                LocalDate.of(2027, 3, 5), LocalDate.of(2027, 3, 8));

        assertEquals(0, new BigDecimal("2").compareTo(vorschau.arbeitstage()));
        assertNull(vorschau.verbleibend());
        assertTrue(vorschau.reicht());
        verifyNoInteractions(tagesSollService);
    }

    @Test
    void vorschau_ungueltigeZeitraeume() {
        LocalDate tag = LocalDate.of(2027, 3, 5);
        assertEquals("Das Ende darf nicht vor dem Beginn liegen.", assertThrows(IllegalArgumentException.class,
                () -> urlaubsantragService.vorschau(MITARBEITER_ID, tag, tag.minusDays(1))).getMessage());
        assertEquals("Ein Antrag darf höchstens ein Jahr umfassen.", assertThrows(IllegalArgumentException.class,
                () -> urlaubsantragService.vorschau(MITARBEITER_ID, tag, tag.plusYears(2))).getMessage());
        assertThrows(IllegalArgumentException.class, () -> urlaubsantragService.vorschau(MITARBEITER_ID, null, tag));
        when(mitarbeiterRepository.findById(Long.MAX_VALUE)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> urlaubsantragService.vorschau(Long.MAX_VALUE, tag, tag));
    }
}
