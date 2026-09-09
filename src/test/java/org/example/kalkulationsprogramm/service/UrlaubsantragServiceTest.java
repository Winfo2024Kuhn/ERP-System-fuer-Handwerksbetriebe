package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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

    @InjectMocks
    private UrlaubsantragService urlaubsantragService;

    private static final Long MITARBEITER_ID = 1L;
    private static final Long ANTRAG_ID = 100L;

    private Mitarbeiter testMitarbeiter;
    private ZeitkontoVersion testZeitkonto;

    @BeforeEach
    void setUp() {
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
    void approveAntrag_wochenendeWirdUebersprungenTrotzWertInDerMap() {
        // Fr 2026-06-05 bis Mo 2026-06-08: dazwischen liegt ein ganzes Wochenende.
        LocalDate von = LocalDate.of(2026, 6, 5);
        LocalDate bis = LocalDate.of(2026, 6, 8);
        stubApproveGrunddaten();
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag(von, bis)));
        when(feiertagService.istFeiertag(any(LocalDate.class))).thenReturn(false);
        when(tagesSollService.arbeitsSollJeTag(MITARBEITER_ID, von, bis))
                .thenReturn(jeTag(von, bis, new BigDecimal("8.00")));

        urlaubsantragService.approveAntrag(ANTRAG_ID);

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
        UrlaubsantragService service = new UrlaubsantragService(repository, mitarbeiterRepository,
                abwesenheitRepository, feiertagService, zeitkontoService, monatsSaldoService,
                zeitkontoKorrekturService, realesSoll, langzeitkrankmeldungService);
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

}
