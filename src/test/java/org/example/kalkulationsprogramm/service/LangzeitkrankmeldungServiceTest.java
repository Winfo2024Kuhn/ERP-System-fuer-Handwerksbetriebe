package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.Langzeitkrankmeldung.LangzeitkrankmeldungDto;
import org.example.kalkulationsprogramm.dto.Langzeitkrankmeldung.StufenplanTagDto;
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
import static org.mockito.Mockito.when;

/**
 * Unit-Tests fuer {@link LangzeitkrankmeldungService}. Dummy-Daten: Max
 * Mustermann, Mitarbeiter-ID 1. Alle festen Test-Daten liegen bewusst in der
 * Vergangenheit (Jahr 2020), damit die {@code LocalDate.now()}-Aufrufe im
 * Service (Verknuepfung von Abwesenheiten, "aktuelle Phase" in toDto) nicht
 * vom tatsaechlichen Testausfuehrungsdatum abhaengen.
 */
@ExtendWith(MockitoExtension.class)
class LangzeitkrankmeldungServiceTest {

    @Mock
    private LangzeitkrankmeldungRepository repository;
    @Mock
    private LangzeitkrankmeldungPhaseRepository phaseRepository;
    @Mock
    private MitarbeiterRepository mitarbeiterRepository;
    @Mock
    private AbwesenheitRepository abwesenheitRepository;
    @Mock
    private ZeitbuchungRepository zeitbuchungRepository;
    @Mock
    private ZeitkontoService zeitkontoService;
    @Mock
    private TagesSollService tagesSollService;
    @Mock
    private MonatsSaldoService monatsSaldoService;

    @InjectMocks
    private LangzeitkrankmeldungService service;

    private static final Long MITARBEITER_ID = 1L;

    private Mitarbeiter mitarbeiter;
    private Zeitkonto zeitkonto;

    @BeforeEach
    void setUp() {
        mitarbeiter = new Mitarbeiter();
        mitarbeiter.setId(MITARBEITER_ID);
        mitarbeiter.setVorname("Max");
        mitarbeiter.setNachname("Mustermann");

        zeitkonto = new Zeitkonto(mitarbeiter);
        zeitkonto.setMontagStunden(new BigDecimal("8.00"));
        zeitkonto.setDienstagStunden(new BigDecimal("8.00"));
        zeitkonto.setMittwochStunden(new BigDecimal("8.00"));
        zeitkonto.setDonnerstagStunden(new BigDecimal("8.00"));
        zeitkonto.setFreitagStunden(new BigDecimal("8.00"));
        zeitkonto.setSamstagStunden(BigDecimal.ZERO);
        zeitkonto.setSonntagStunden(BigDecimal.ZERO);
    }

    // ==================== Hilfsmethoden ====================

    private LangzeitkrankmeldungPhase neuePhase(Langzeitkrankmeldung meldung, LangzeitkrankmeldungPhaseTyp typ,
            LocalDate von, LocalDate bis, BigDecimal stundenProTag) {
        LangzeitkrankmeldungPhase phase = new LangzeitkrankmeldungPhase();
        phase.setLangzeitkrankmeldung(meldung);
        phase.setTyp(typ);
        phase.setVonDatum(von);
        phase.setBisDatum(bis);
        phase.setStundenProTag(stundenProTag);
        return phase;
    }

    private Langzeitkrankmeldung meldungMitLohnfortzahlung(LocalDate beginn, LocalDate lohnfortzahlungBis) {
        Langzeitkrankmeldung meldung = new Langzeitkrankmeldung();
        meldung.setId(1L);
        meldung.setMitarbeiter(mitarbeiter);
        meldung.setBeginn(beginn);
        meldung.setLohnfortzahlungBis(lohnfortzahlungBis);
        meldung.setStatus(LangzeitkrankmeldungStatus.LAUFEND);
        meldung.getPhasen()
                .add(neuePhase(meldung, LangzeitkrankmeldungPhaseTyp.LOHNFORTZAHLUNG, beginn, lohnfortzahlungBis, null));
        return meldung;
    }

    private Langzeitkrankmeldung meldungMitStatus(LangzeitkrankmeldungStatus status, LocalDate ende) {
        Langzeitkrankmeldung meldung = new Langzeitkrankmeldung();
        meldung.setId(1L);
        meldung.setMitarbeiter(mitarbeiter);
        meldung.setBeginn(LocalDate.of(2020, 3, 1));
        meldung.setLohnfortzahlungBis(LocalDate.of(2020, 4, 11));
        meldung.setStatus(status);
        meldung.setEnde(ende);
        meldung.getPhasen().add(neuePhase(meldung, LangzeitkrankmeldungPhaseTyp.LOHNFORTZAHLUNG,
                LocalDate.of(2020, 3, 1), ende, null));
        return meldung;
    }

    private void stubAnlegenGrunddaten() {
        when(mitarbeiterRepository.findById(MITARBEITER_ID)).thenReturn(Optional.of(mitarbeiter));
        when(repository.findUeberlappende(eq(MITARBEITER_ID), any(), any())).thenReturn(List.of());
        when(repository.save(any(Langzeitkrankmeldung.class))).thenAnswer(inv -> inv.getArgument(0));
        when(abwesenheitRepository.findByMitarbeiterIdAndTypAndDatumBetween(eq(MITARBEITER_ID),
                eq(AbwesenheitsTyp.KRANKHEIT), any(), any())).thenReturn(List.of());
    }

    // ==================== anlegen ====================

    @Test
    void anlegen_BerechnetLohnfortzahlungAlsBeginnPlus41Tage() {
        stubAnlegenGrunddaten();

        Langzeitkrankmeldung ergebnis = service.anlegen(MITARBEITER_ID, LocalDate.of(2020, 3, 1), null, null);

        // 42 Tage inklusive Beginn = Beginn + 41 Tage
        assertEquals(LocalDate.of(2020, 4, 11), ergebnis.getLohnfortzahlungBis());
        assertEquals(LangzeitkrankmeldungStatus.LAUFEND, ergebnis.getStatus());
        assertEquals(1, ergebnis.getPhasen().size());
        LangzeitkrankmeldungPhase phase = ergebnis.getPhasen().get(0);
        assertEquals(LangzeitkrankmeldungPhaseTyp.LOHNFORTZAHLUNG, phase.getTyp());
        assertEquals(LocalDate.of(2020, 3, 1), phase.getVonDatum());
        assertEquals(LocalDate.of(2020, 4, 11), phase.getBisDatum());
    }

    @Test
    void anlegen_LohnfortzahlungsdatumIstUeberschreibbar() {
        stubAnlegenGrunddaten();

        Langzeitkrankmeldung ergebnis = service.anlegen(MITARBEITER_ID, LocalDate.of(2020, 3, 1),
                LocalDate.of(2020, 5, 1), null);

        assertEquals(LocalDate.of(2020, 5, 1), ergebnis.getLohnfortzahlungBis());
    }

    @Test
    void anlegen_MitarbeiterNichtGefunden_wirftFehler() {
        when(mitarbeiterRepository.findById(MITARBEITER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.anlegen(MITARBEITER_ID, LocalDate.of(2020, 3, 1), null, null));
    }

    @Test
    void anlegen_LehntUeberlappendeMeldungAb() {
        when(mitarbeiterRepository.findById(MITARBEITER_ID)).thenReturn(Optional.of(mitarbeiter));
        Langzeitkrankmeldung bestehende = meldungMitStatus(LangzeitkrankmeldungStatus.LAUFEND, null);
        when(repository.findUeberlappende(eq(MITARBEITER_ID), any(), any())).thenReturn(List.of(bestehende));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.anlegen(MITARBEITER_ID, LocalDate.of(2020, 3, 15), null, null));

        assertTrue(ex.getMessage().contains("bereits eine Krankmeldung"));
    }

    @Test
    void anlegen_ABGEBROCHENblockiertNichtDenSelbenZeitraum() {
        // findUeberlappende schliesst ABGEBROCHEN bereits auf Repository-Ebene aus
        // (siehe LangzeitkrankmeldungRepositoryTest, Task 1). Hier simuliert durch
        // eine leere Ueberlappungsliste trotz zeitlicher Ueberschneidung mit einer
        // (nicht mehr gelieferten) abgebrochenen Alt-Meldung.
        stubAnlegenGrunddaten();

        Langzeitkrankmeldung ergebnis = service.anlegen(MITARBEITER_ID, LocalDate.of(2020, 3, 1), null, null);

        assertEquals(LangzeitkrankmeldungStatus.LAUFEND, ergebnis.getStatus());
    }

    @Test
    void anlegen_UebergibtOffenesEndeInnerhalbDesMysqlDateBereichsAnUeberlappungspruefung() {
        // Befund 1 (Abschnitt-4-Review, Issue #91): bisher ging LocalDate.MAX
        // (Jahr 999999999) als "bis"-Parameter an findUeberlappende. MySQL
        // kennt DATE nur bis 9999-12-31 - je nach Servermodus ein Fehler oder,
        // schlimmer, still keine Treffer, wodurch die Ueberlappungspruefung
        // nichts mehr prueft. Mutationsprobe: baut man die Konstante zurueck
        // auf LocalDate.MAX, ist der hier abgefangene Wert nicht mehr
        // LocalDate.of(9999, 12, 31) und der Test wird rot.
        stubAnlegenGrunddaten();

        service.anlegen(MITARBEITER_ID, LocalDate.of(2020, 3, 1), null, null);

        ArgumentCaptor<LocalDate> bisCaptor = ArgumentCaptor.forClass(LocalDate.class);
        org.mockito.Mockito.verify(repository).findUeberlappende(eq(MITARBEITER_ID), any(), bisCaptor.capture());
        LocalDate uebergebenesBis = bisCaptor.getValue();

        assertEquals(LocalDate.of(9999, 12, 31), uebergebenesBis);
        assertTrue(uebergebenesBis.getYear() <= 9999, "MySQL DATE erlaubt hoechstens das Jahr 9999");
    }

    @Test
    void anlegen_NotizUeber500ZeichenWirdAbgelehnt() {
        when(mitarbeiterRepository.findById(MITARBEITER_ID)).thenReturn(Optional.of(mitarbeiter));
        when(repository.findUeberlappende(eq(MITARBEITER_ID), any(), any())).thenReturn(List.of());
        String zuLang = "a".repeat(501);

        assertThrows(IllegalArgumentException.class,
                () -> service.anlegen(MITARBEITER_ID, LocalDate.of(2020, 3, 1), null, zuLang));
    }

    @Test
    @SuppressWarnings("unchecked")
    void anlegen_VerknuepftAbwesenheitenMitPhaseUndSetztFkFuerTageAusserhalbAufNull() {
        when(mitarbeiterRepository.findById(MITARBEITER_ID)).thenReturn(Optional.of(mitarbeiter));
        when(repository.findUeberlappende(eq(MITARBEITER_ID), any(), any())).thenReturn(List.of());
        when(repository.save(any(Langzeitkrankmeldung.class))).thenAnswer(inv -> inv.getArgument(0));

        Abwesenheit innerhalb = new Abwesenheit();
        innerhalb.setMitarbeiter(mitarbeiter);
        innerhalb.setDatum(LocalDate.of(2020, 3, 5));
        innerhalb.setTyp(AbwesenheitsTyp.KRANKHEIT);
        innerhalb.setStunden(new BigDecimal("8.00"));

        // Bereits an eine andere Phase gekoppelt - liegt nach lohnfortzahlungBis
        // (4/11) und muss deshalb entkoppelt werden.
        Abwesenheit ausserhalb = new Abwesenheit();
        ausserhalb.setMitarbeiter(mitarbeiter);
        ausserhalb.setDatum(LocalDate.of(2020, 4, 20));
        ausserhalb.setTyp(AbwesenheitsTyp.KRANKHEIT);
        ausserhalb.setStunden(new BigDecimal("8.00"));
        ausserhalb.setLangzeitkrankmeldung(new Langzeitkrankmeldung());
        ausserhalb.setLangzeitkrankmeldungPhase(new LangzeitkrankmeldungPhase());

        when(abwesenheitRepository.findByMitarbeiterIdAndTypAndDatumBetween(eq(MITARBEITER_ID),
                eq(AbwesenheitsTyp.KRANKHEIT), any(), any())).thenReturn(List.of(innerhalb, ausserhalb));

        Langzeitkrankmeldung ergebnis = service.anlegen(MITARBEITER_ID, LocalDate.of(2020, 3, 1),
                LocalDate.of(2020, 4, 11), null);

        assertEquals(ergebnis, innerhalb.getLangzeitkrankmeldung());
        assertEquals(ergebnis.getPhasen().get(0), innerhalb.getLangzeitkrankmeldungPhase());
        assertNull(ausserhalb.getLangzeitkrankmeldung());
        assertNull(ausserhalb.getLangzeitkrankmeldungPhase());

        ArgumentCaptor<List<Abwesenheit>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(abwesenheitRepository).saveAll(captor.capture());
        assertEquals(2, captor.getValue().size());
    }

    // ==================== aendern ====================

    @Test
    void aendern_AktualisiertLohnfortzahlungsdatumUndNotiz() {
        Langzeitkrankmeldung meldung = meldungMitLohnfortzahlung(LocalDate.of(2020, 3, 1), LocalDate.of(2020, 4, 11));
        when(repository.findMitPhasenById(1L)).thenReturn(Optional.of(meldung));
        when(repository.findUeberlappende(eq(MITARBEITER_ID), any(), any())).thenReturn(List.of());
        when(repository.save(any(Langzeitkrankmeldung.class))).thenAnswer(inv -> inv.getArgument(0));
        when(abwesenheitRepository.findByMitarbeiterIdAndTypAndDatumBetween(eq(MITARBEITER_ID),
                eq(AbwesenheitsTyp.KRANKHEIT), any(), any())).thenReturn(List.of());

        Langzeitkrankmeldung ergebnis = service.aendern(1L, LocalDate.of(2020, 3, 1), LocalDate.of(2020, 4, 20),
                "Neue Notiz");

        assertEquals(LocalDate.of(2020, 4, 20), ergebnis.getLohnfortzahlungBis());
        assertEquals("Neue Notiz", ergebnis.getNotiz());
    }

    // ==================== Phasen-Regeln ====================

    @Test
    void phaseHinzufuegen_UeberlappendePhase_wirftFehler() {
        Langzeitkrankmeldung meldung = meldungMitLohnfortzahlung(LocalDate.of(2020, 3, 1), LocalDate.of(2020, 4, 11));
        when(repository.findMitPhasenById(1L)).thenReturn(Optional.of(meldung));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.phaseHinzufuegen(1L, LangzeitkrankmeldungPhaseTyp.KRANKENGELD,
                        LocalDate.of(2020, 4, 5), null, null));

        assertTrue(ex.getMessage().toLowerCase().contains("überlapp"));
    }

    @Test
    void phaseHinzufuegen_LueckeZwischenPhasen_wirftFehler() {
        Langzeitkrankmeldung meldung = meldungMitLohnfortzahlung(LocalDate.of(2020, 3, 1), LocalDate.of(2020, 4, 11));
        when(repository.findMitPhasenById(1L)).thenReturn(Optional.of(meldung));

        // Luecke: 12.4. fehlt
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.phaseHinzufuegen(1L, LangzeitkrankmeldungPhaseTyp.KRANKENGELD,
                        LocalDate.of(2020, 4, 13), null, null));

        assertTrue(ex.getMessage().toLowerCase().contains("lücke"));
    }

    @Test
    void phaseHinzufuegen_ZweiOffenePhasen_wirftFehler() {
        Langzeitkrankmeldung meldung = meldungMitLohnfortzahlung(LocalDate.of(2020, 3, 1), LocalDate.of(2020, 4, 11));
        LangzeitkrankmeldungPhase bereitsOffen = neuePhase(meldung, LangzeitkrankmeldungPhaseTyp.KRANKENGELD,
                LocalDate.of(2020, 4, 12), null, null);
        meldung.getPhasen().add(bereitsOffen);
        when(repository.findMitPhasenById(1L)).thenReturn(Optional.of(meldung));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.phaseHinzufuegen(1L, LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG,
                        LocalDate.of(2020, 5, 1), null, new BigDecimal("4.00")));

        assertTrue(ex.getMessage().toLowerCase().contains("letzte"));
    }

    @Test
    void phaseHinzufuegen_WiedereingliederungOhneStundenProTag_wirftFehler() {
        Langzeitkrankmeldung meldung = meldungMitLohnfortzahlung(LocalDate.of(2020, 3, 1), LocalDate.of(2020, 4, 11));
        when(repository.findMitPhasenById(1L)).thenReturn(Optional.of(meldung));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.phaseHinzufuegen(1L, LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG,
                        LocalDate.of(2020, 4, 12), null, null));

        assertTrue(ex.getMessage().contains("Stunden pro Tag"));
    }

    @Test
    void phaseHinzufuegen_NichtWiedereingliederungMitStundenProTag_wirftFehler() {
        Langzeitkrankmeldung meldung = meldungMitLohnfortzahlung(LocalDate.of(2020, 3, 1), LocalDate.of(2020, 4, 11));
        when(repository.findMitPhasenById(1L)).thenReturn(Optional.of(meldung));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.phaseHinzufuegen(1L, LangzeitkrankmeldungPhaseTyp.KRANKENGELD,
                        LocalDate.of(2020, 4, 12), null, new BigDecimal("4.00")));

        assertTrue(ex.getMessage().contains("Stunden pro Tag"));
    }

    @Test
    void phaseHinzufuegen_StundenProTagUeberZeitkontoSoll_wirftFehler() {
        // Lohnfortzahlung Mo 30.3. - So 12.4., neue Phase ab Mo 13.4. (luecken- und ueberlappungsfrei)
        Langzeitkrankmeldung meldung = meldungMitLohnfortzahlung(LocalDate.of(2020, 3, 30), LocalDate.of(2020, 4, 12));
        when(repository.findMitPhasenById(1L)).thenReturn(Optional.of(meldung));
        when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID)).thenReturn(zeitkonto);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.phaseHinzufuegen(1L, LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG,
                        LocalDate.of(2020, 4, 13), null, new BigDecimal("10.00")));

        assertTrue(ex.getMessage().contains("Tagessoll"));
    }

    @Test
    void phaseHinzufuegen_GueltigeWiedereingliederung_WirdGespeichert() {
        Langzeitkrankmeldung meldung = meldungMitLohnfortzahlung(LocalDate.of(2020, 3, 30), LocalDate.of(2020, 4, 12));
        when(repository.findMitPhasenById(1L)).thenReturn(Optional.of(meldung));
        when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID)).thenReturn(zeitkonto);
        when(repository.save(any(Langzeitkrankmeldung.class))).thenAnswer(inv -> inv.getArgument(0));
        when(abwesenheitRepository.findByMitarbeiterIdAndTypAndDatumBetween(eq(MITARBEITER_ID),
                eq(AbwesenheitsTyp.KRANKHEIT), any(), any())).thenReturn(List.of());

        Langzeitkrankmeldung ergebnis = service.phaseHinzufuegen(1L, LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG,
                LocalDate.of(2020, 4, 13), null, new BigDecimal("4.00"));

        assertEquals(2, ergebnis.getPhasen().size());
    }

    @Test
    void phaseLoeschen_EntferntLetztePhaseUndSpeichert() {
        Langzeitkrankmeldung meldung = meldungMitLohnfortzahlung(LocalDate.of(2020, 3, 1), LocalDate.of(2020, 4, 11));
        LangzeitkrankmeldungPhase phase = meldung.getPhasen().get(0);
        phase.setId(99L);
        when(repository.findMitPhasenById(1L)).thenReturn(Optional.of(meldung));
        when(repository.save(any(Langzeitkrankmeldung.class))).thenAnswer(inv -> inv.getArgument(0));
        when(abwesenheitRepository.findByMitarbeiterIdAndTypAndDatumBetween(eq(MITARBEITER_ID),
                eq(AbwesenheitsTyp.KRANKHEIT), any(), any())).thenReturn(List.of());

        Langzeitkrankmeldung ergebnis = service.phaseLoeschen(1L, 99L);

        assertTrue(ergebnis.getPhasen().isEmpty());
    }

    @Test
    void phaseLoeschen_UnbekanntePhasenId_wirftFehler() {
        Langzeitkrankmeldung meldung = meldungMitLohnfortzahlung(LocalDate.of(2020, 3, 1), LocalDate.of(2020, 4, 11));
        meldung.getPhasen().get(0).setId(99L);
        when(repository.findMitPhasenById(1L)).thenReturn(Optional.of(meldung));

        assertThrows(IllegalArgumentException.class, () -> service.phaseLoeschen(1L, 123L));
    }

    // ==================== Lesemethoden ====================

    @Test
    void finde_DelegiertMitAngegebenemStatusAlsEinzelneListe() {
        when(repository.findMitPhasen(List.of(LangzeitkrankmeldungStatus.LAUFEND))).thenReturn(List.of());

        List<Langzeitkrankmeldung> ergebnis = service.finde(LangzeitkrankmeldungStatus.LAUFEND);

        assertTrue(ergebnis.isEmpty());
    }

    @Test
    void findeMitPhasen_LiefertMeldungOderWirftFehlerBeiUnbekannterId() {
        Langzeitkrankmeldung meldung = meldungMitLohnfortzahlung(LocalDate.of(2020, 3, 1), LocalDate.of(2020, 4, 11));
        when(repository.findMitPhasenById(1L)).thenReturn(Optional.of(meldung));
        when(repository.findMitPhasenById(999L)).thenReturn(Optional.empty());

        assertEquals(meldung, service.findeMitPhasen(1L));
        assertThrows(IllegalArgumentException.class, () -> service.findeMitPhasen(999L));
    }

    // ==================== Statusuebergaenge ====================

    @Test
    void beenden_AusLAUFEND_SchliesstOffenePhaseUndSetztStatus() {
        Langzeitkrankmeldung meldung = meldungMitStatus(LangzeitkrankmeldungStatus.LAUFEND, null);
        when(repository.findMitPhasenById(1L)).thenReturn(Optional.of(meldung));
        when(repository.save(any(Langzeitkrankmeldung.class))).thenAnswer(inv -> inv.getArgument(0));
        when(abwesenheitRepository.findByMitarbeiterIdAndTypAndDatumBetween(eq(MITARBEITER_ID),
                eq(AbwesenheitsTyp.KRANKHEIT), any(), any())).thenReturn(List.of());

        Langzeitkrankmeldung ergebnis = service.beenden(1L, LocalDate.of(2020, 5, 1));

        assertEquals(LangzeitkrankmeldungStatus.BEENDET, ergebnis.getStatus());
        assertEquals(LocalDate.of(2020, 5, 1), ergebnis.getEnde());
        assertEquals(LocalDate.of(2020, 5, 1), ergebnis.getPhasen().get(0).getBisDatum());
    }

    @Test
    void beenden_AusBEENDET_wirftFehler() {
        Langzeitkrankmeldung meldung = meldungMitStatus(LangzeitkrankmeldungStatus.BEENDET, LocalDate.of(2020, 5, 1));
        when(repository.findMitPhasenById(1L)).thenReturn(Optional.of(meldung));

        assertThrows(IllegalStateException.class, () -> service.beenden(1L, LocalDate.of(2020, 5, 2)));
    }

    @Test
    void beenden_AusABGEBROCHEN_wirftFehler() {
        Langzeitkrankmeldung meldung = meldungMitStatus(LangzeitkrankmeldungStatus.ABGEBROCHEN, null);
        when(repository.findMitPhasenById(1L)).thenReturn(Optional.of(meldung));

        assertThrows(IllegalStateException.class, () -> service.beenden(1L, LocalDate.of(2020, 5, 2)));
    }

    @Test
    void wiederEroeffnen_AusBEENDET_OeffnetLetztePhaseWieder() {
        Langzeitkrankmeldung meldung = meldungMitStatus(LangzeitkrankmeldungStatus.BEENDET, LocalDate.of(2020, 5, 1));
        when(repository.findMitPhasenById(1L)).thenReturn(Optional.of(meldung));
        when(repository.findUeberlappende(eq(MITARBEITER_ID), any(), any())).thenReturn(List.of());
        when(repository.save(any(Langzeitkrankmeldung.class))).thenAnswer(inv -> inv.getArgument(0));
        when(abwesenheitRepository.findByMitarbeiterIdAndTypAndDatumBetween(eq(MITARBEITER_ID),
                eq(AbwesenheitsTyp.KRANKHEIT), any(), any())).thenReturn(List.of());

        Langzeitkrankmeldung ergebnis = service.wiederEroeffnen(1L);

        assertEquals(LangzeitkrankmeldungStatus.LAUFEND, ergebnis.getStatus());
        assertNull(ergebnis.getEnde());
        assertNull(ergebnis.getPhasen().get(0).getBisDatum());
    }

    @Test
    void wiederEroeffnen_SchliesstSichSelbstAusDerUeberlappungspruefungAus() {
        Langzeitkrankmeldung meldung = meldungMitStatus(LangzeitkrankmeldungStatus.BEENDET, LocalDate.of(2020, 4, 11));
        when(repository.findMitPhasenById(1L)).thenReturn(Optional.of(meldung));
        // liefert die Meldung selbst zurueck - der Service muss sie herausfiltern
        when(repository.findUeberlappende(eq(MITARBEITER_ID), any(), any())).thenReturn(List.of(meldung));
        when(repository.save(any(Langzeitkrankmeldung.class))).thenAnswer(inv -> inv.getArgument(0));
        when(abwesenheitRepository.findByMitarbeiterIdAndTypAndDatumBetween(eq(MITARBEITER_ID),
                eq(AbwesenheitsTyp.KRANKHEIT), any(), any())).thenReturn(List.of());

        Langzeitkrankmeldung ergebnis = service.wiederEroeffnen(1L);

        assertEquals(LangzeitkrankmeldungStatus.LAUFEND, ergebnis.getStatus());
    }

    @Test
    void wiederEroeffnen_LehntAbWennAndereMeldungUeberlappt() {
        Langzeitkrankmeldung meldung = meldungMitStatus(LangzeitkrankmeldungStatus.BEENDET, LocalDate.of(2020, 5, 1));
        Langzeitkrankmeldung andere = meldungMitStatus(LangzeitkrankmeldungStatus.LAUFEND, null);
        andere.setId(2L);
        when(repository.findMitPhasenById(1L)).thenReturn(Optional.of(meldung));
        when(repository.findUeberlappende(eq(MITARBEITER_ID), any(), any())).thenReturn(List.of(andere));

        assertThrows(IllegalStateException.class, () -> service.wiederEroeffnen(1L));
    }

    @Test
    void wiederEroeffnen_AusLAUFEND_wirftFehler() {
        Langzeitkrankmeldung meldung = meldungMitStatus(LangzeitkrankmeldungStatus.LAUFEND, null);
        when(repository.findMitPhasenById(1L)).thenReturn(Optional.of(meldung));

        assertThrows(IllegalStateException.class, () -> service.wiederEroeffnen(1L));
    }

    @Test
    void wiederEroeffnen_AusABGEBROCHEN_NenntNeuAnlegenImFehlertext() {
        Langzeitkrankmeldung meldung = meldungMitStatus(LangzeitkrankmeldungStatus.ABGEBROCHEN, null);
        when(repository.findMitPhasenById(1L)).thenReturn(Optional.of(meldung));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.wiederEroeffnen(1L));

        assertTrue(ex.getMessage().toLowerCase().contains("neu anlegen"));
    }

    @Test
    void abbrechen_AusLAUFEND_SetztStatusUndEntkoppeltAbwesenheiten() {
        Langzeitkrankmeldung meldung = meldungMitStatus(LangzeitkrankmeldungStatus.LAUFEND, null);
        Abwesenheit verknuepft = new Abwesenheit();
        verknuepft.setMitarbeiter(mitarbeiter);
        verknuepft.setDatum(LocalDate.of(2020, 3, 5));
        verknuepft.setTyp(AbwesenheitsTyp.KRANKHEIT);
        verknuepft.setStunden(new BigDecimal("8.00"));
        verknuepft.setLangzeitkrankmeldung(meldung);
        verknuepft.setLangzeitkrankmeldungPhase(meldung.getPhasen().get(0));

        when(repository.findMitPhasenById(1L)).thenReturn(Optional.of(meldung));
        when(repository.save(any(Langzeitkrankmeldung.class))).thenAnswer(inv -> inv.getArgument(0));
        when(abwesenheitRepository.findByMitarbeiterIdAndTypAndDatumBetween(eq(MITARBEITER_ID),
                eq(AbwesenheitsTyp.KRANKHEIT), any(), any())).thenReturn(List.of(verknuepft));

        Langzeitkrankmeldung ergebnis = service.abbrechen(1L);

        assertEquals(LangzeitkrankmeldungStatus.ABGEBROCHEN, ergebnis.getStatus());
        assertNull(verknuepft.getLangzeitkrankmeldung());
        assertNull(verknuepft.getLangzeitkrankmeldungPhase());
    }

    @Test
    void abbrechen_AusBEENDET_wirftFehler() {
        Langzeitkrankmeldung meldung = meldungMitStatus(LangzeitkrankmeldungStatus.BEENDET, LocalDate.of(2020, 5, 1));
        when(repository.findMitPhasenById(1L)).thenReturn(Optional.of(meldung));

        assertThrows(IllegalStateException.class, () -> service.abbrechen(1L));
    }

    @Test
    void abbrechen_AusABGEBROCHEN_wirftFehler() {
        Langzeitkrankmeldung meldung = meldungMitStatus(LangzeitkrankmeldungStatus.ABGEBROCHEN, null);
        when(repository.findMitPhasenById(1L)).thenReturn(Optional.of(meldung));

        assertThrows(IllegalStateException.class, () -> service.abbrechen(1L));
    }

    // ==================== restTageLohnfortzahlung ====================

    @Test
    void restTageLohnfortzahlung_BerechnetTageBisZumStichtag() {
        Langzeitkrankmeldung meldung = meldungMitLohnfortzahlung(LocalDate.of(2020, 3, 1), LocalDate.of(2020, 4, 11));

        assertEquals(10, service.restTageLohnfortzahlung(meldung, LocalDate.of(2020, 4, 1)));
        assertEquals(0, service.restTageLohnfortzahlung(meldung, LocalDate.of(2020, 4, 11)));
        assertEquals(-3, service.restTageLohnfortzahlung(meldung, LocalDate.of(2020, 4, 14)));
    }

    // ==================== toDto ====================

    @Test
    void toDto_MapptGrundfelderUndPhasenUndLabels() {
        Langzeitkrankmeldung meldung = meldungMitLohnfortzahlung(LocalDate.of(2020, 3, 1), LocalDate.of(2020, 4, 11));
        meldung.setNotiz("Reha ab April geplant");
        meldung.setVersion(3L);

        LangzeitkrankmeldungDto dto = service.toDto(meldung, false);

        assertEquals(MITARBEITER_ID, dto.getMitarbeiterId());
        assertEquals("Mustermann, Max", dto.getMitarbeiterName());
        assertEquals(LangzeitkrankmeldungStatus.LAUFEND, dto.getStatus());
        assertEquals("Läuft noch", dto.getStatusLabel());
        assertEquals(1, dto.getPhasen().size());
        assertEquals("Lohnfortzahlung durch den Betrieb", dto.getPhasen().get(0).getLabel());
        assertNull(dto.getStufenplanTage());
    }

    @Test
    void toDto_HeuteGeplanteStunden_NurBeiLaufenderWiedereingliederung() {
        Langzeitkrankmeldung meldung = meldungMitStatus(LangzeitkrankmeldungStatus.LAUFEND, null);
        meldung.getPhasen().clear();
        meldung.getPhasen().add(neuePhase(meldung, LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG,
                LocalDate.of(2020, 1, 1), null, new BigDecimal("4.00")));

        when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID)).thenReturn(zeitkonto);
        when(tagesSollService.arbeitsSoll(eq(MITARBEITER_ID), eq(zeitkonto), any())).thenReturn(new BigDecimal("4.00"));

        LangzeitkrankmeldungDto dto = service.toDto(meldung, false);

        assertEquals(LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG, dto.getAktuellePhaseTyp());
        assertEquals(new BigDecimal("4.00"), dto.getHeuteGeplanteStunden());
    }

    @Test
    void toDto_MitStufenplanTagen_MarkiertUeberplanTage() {
        Langzeitkrankmeldung meldung = meldungMitStatus(LangzeitkrankmeldungStatus.BEENDET, LocalDate.of(2020, 4, 17));
        meldung.getPhasen().clear();
        meldung.getPhasen().add(neuePhase(meldung, LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG,
                LocalDate.of(2020, 4, 13), LocalDate.of(2020, 4, 17), new BigDecimal("4.00")));

        when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID)).thenReturn(zeitkonto);
        // baueStufenplanTage laedt die geplanten Stunden seit Abschnitt 4
        // (Befund 2) einmal fuer den Gesamtzeitraum statt einmal pro Tag -
        // arbeitsSollJeTag ersetzt hier den frueheren Einzeltag-Mock.
        Map<LocalDate, BigDecimal> geplantJeTag = new LinkedHashMap<>();
        for (LocalDate tag = LocalDate.of(2020, 4, 13); !tag.isAfter(LocalDate.of(2020, 4, 17)); tag = tag
                .plusDays(1)) {
            geplantJeTag.put(tag, new BigDecimal("4.00"));
        }
        when(tagesSollService.arbeitsSollJeTag(eq(MITARBEITER_ID), eq(zeitkonto), any(), any()))
                .thenReturn(geplantJeTag);

        Zeitbuchung buchungMontag = new Zeitbuchung();
        buchungMontag.setStartZeit(LocalDate.of(2020, 4, 13).atTime(8, 0));
        buchungMontag.setAnzahlInStunden(new BigDecimal("6.00"));
        buchungMontag.setTyp(BuchungsTyp.ARBEIT);

        when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(eq(MITARBEITER_ID), any(), any()))
                .thenReturn(List.of(buchungMontag));

        LangzeitkrankmeldungDto dto = service.toDto(meldung, true);

        assertEquals(5, dto.getStufenplanTage().size());
        StufenplanTagDto montag = dto.getStufenplanTage().get(0);
        assertEquals(LocalDate.of(2020, 4, 13), montag.getDatum());
        assertEquals(0, new BigDecimal("6.00").compareTo(montag.getGestempelteStunden()));
        assertTrue(montag.isUeberPlan());
        assertFalse(dto.getStufenplanTage().get(1).isUeberPlan());
    }

    @Test
    void toDto_MitStufenplanTagen_GeschlosseneZukuenftigePhaseGefolgtVonOffenerPhase_WirftKeineNullPointerException() {
        // Nachbesserung Abschnitt 4, Befund 1 (blockierend): bei einer OFFENEN
        // letzten Phase wurde "bis" bisher immer auf LocalDate.now() gesetzt.
        // Eine DAVOR liegende, geschlossene Phase mit Enddatum in der Zukunft
        // lag dann komplett hinter diesem Kartenende - von > bis,
        // arbeitsSollJeTag bekam einen rueckwaerts laufenden Zeitraum und
        // lieferte eine leere Map. geplantJeTag.get(tag) war fuer jeden Tag
        // der geschlossenen Phase null, gestempelt.compareTo(geplant) warf
        // eine NullPointerException. Reproduziert den vom Reviewer gemeldeten
        // Fall: eine im Voraus geplante geschlossene Phase (4h), gefolgt von
        // einer offenen (6h) - "heute" liegt vor beiden Phasen.
        LocalDate ersteVon = LocalDate.now().plusMonths(1).withDayOfMonth(1);
        LocalDate ersteBis = ersteVon.plusDays(13);
        LocalDate zweiteVon = ersteBis.plusDays(1);

        Langzeitkrankmeldung meldung = meldungMitStatus(LangzeitkrankmeldungStatus.LAUFEND, null);
        meldung.getPhasen().clear();
        meldung.getPhasen().add(neuePhase(meldung, LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG,
                ersteVon, ersteBis, new BigDecimal("4.00")));
        meldung.getPhasen().add(neuePhase(meldung, LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG,
                zweiteVon, null, new BigDecimal("6.00")));

        when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID)).thenReturn(zeitkonto);
        // Bildet die reale Zeitraum-Methode nach: liefert fuer jeden
        // angefragten Tag einen Wert, damit sich ein falscher (rueckwaerts
        // laufender oder zu kurzer) Zeitraum als leere/luckenhafte Map zeigt.
        when(tagesSollService.arbeitsSollJeTag(eq(MITARBEITER_ID), eq(zeitkonto), any(), any()))
                .thenAnswer(inv -> {
                    LocalDate von = inv.getArgument(2);
                    LocalDate bis = inv.getArgument(3);
                    Map<LocalDate, BigDecimal> ergebnis = new LinkedHashMap<>();
                    for (LocalDate tag = von; !tag.isAfter(bis); tag = tag.plusDays(1)) {
                        ergebnis.put(tag, new BigDecimal("4.00"));
                    }
                    return ergebnis;
                });
        when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(eq(MITARBEITER_ID), any(), any()))
                .thenReturn(List.of());

        LangzeitkrankmeldungDto dto = service.toDto(meldung, true);

        // Die geschlossene Phase (14 Tage) muss vollstaendig auftauchen; die
        // offene Phase liegt komplett nach "heute" und traegt hier nichts bei.
        assertEquals(14, dto.getStufenplanTage().size());

        // Und die Tage muessen echte Sollstunden tragen, nicht nur da sein.
        // Ohne diese Zusicherung haelt der Test nur das getOrDefault-Netz fest:
        // Baut man die bis-Berechnung zurueck, bleiben es 14 Tage, aber alle
        // zeigen still 0,00 h statt 4,00 h - falsche Zahlen im Buero, und kein
        // Test faellt. Im Review von Abschnitt 4 gemessen.
        dto.getStufenplanTage().forEach(tag -> assertEquals(
                0,
                new BigDecimal("4.00").compareTo(tag.getGeplanteStunden()),
                "Tag " + tag.getDatum() + " sollte 4,00 h geplant haben, hatte "
                        + tag.getGeplanteStunden()));
    }

    // ==================== getMobileStand ====================

    @Test
    void getMobileStand_UnbekanntesToken_LiefertLeereMap() {
        when(mitarbeiterRepository.findByLoginTokenAndAktivTrue("unbekannt")).thenReturn(Optional.empty());

        Map<String, Object> stand = service.getMobileStand("unbekannt", LocalDate.of(2020, 4, 1));

        assertTrue(stand.isEmpty());
    }

    @Test
    void getMobileStand_NutztDieAktivTrueVariante_DeaktivierterMitarbeiterVerliertZugriffAufAltesToken() {
        // Nachbesserung Abschnitt 4, Befund 3: findByLoginToken (statt
        // findByLoginTokenAndAktivTrue) wuerde einen deaktivierten Mitarbeiter
        // trotzdem finden - das Token wird beim Deaktivieren nirgends
        // geloescht, ein ausgeschiedener Mitarbeiter behielte Lesezugriff auf
        // seinen Krankenstand. Jeder andere unauthentifizierte Mobile-Lesepfad
        // (siehe BelegService) nutzt die Aktiv-Variante. Ruft NICHT die
        // Optional.empty()-Value allein, sondern die tatsaechlich aufgerufene
        // Repository-Methode - ein blosser Wertevergleich waere hier blind,
        // weil Mockito fuer eine unstubbte Optional-Methode ohnehin
        // Optional.empty() liefert.
        when(mitarbeiterRepository.findByLoginTokenAndAktivTrue("altes-token")).thenReturn(Optional.empty());

        Map<String, Object> stand = service.getMobileStand("altes-token", LocalDate.of(2020, 4, 1));

        assertTrue(stand.isEmpty());
        org.mockito.Mockito.verify(mitarbeiterRepository).findByLoginTokenAndAktivTrue("altes-token");
        org.mockito.Mockito.verify(mitarbeiterRepository, org.mockito.Mockito.never()).findByLoginToken(any());
    }

    @Test
    void getMobileStand_KeineLaufendePhase_LiefertLeereMap() {
        when(mitarbeiterRepository.findByLoginTokenAndAktivTrue("token")).thenReturn(Optional.of(mitarbeiter));
        when(phaseRepository.findImZeitraum(eq(MITARBEITER_ID), any(), any())).thenReturn(List.of());

        Map<String, Object> stand = service.getMobileStand("token", LocalDate.of(2020, 4, 1));

        assertTrue(stand.isEmpty());
    }

    @Test
    void getMobileStand_LaufendeWiedereingliederung_LiefertFuenfFelderOhneNameOderNotiz() {
        Langzeitkrankmeldung meldung = meldungMitStatus(LangzeitkrankmeldungStatus.LAUFEND, null);
        meldung.setNotiz("Vertraulich - darf nicht raus");
        meldung.getPhasen().clear();
        LangzeitkrankmeldungPhase wiedereingliederung = neuePhase(meldung, LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG,
                LocalDate.of(2020, 4, 1), null, new BigDecimal("4.00"));
        meldung.getPhasen().add(wiedereingliederung);

        when(mitarbeiterRepository.findByLoginTokenAndAktivTrue("token")).thenReturn(Optional.of(mitarbeiter));
        when(phaseRepository.findImZeitraum(eq(MITARBEITER_ID), any(), any())).thenReturn(List.of(wiedereingliederung));
        when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID)).thenReturn(zeitkonto);
        when(tagesSollService.arbeitsSoll(eq(MITARBEITER_ID), eq(zeitkonto), any())).thenReturn(new BigDecimal("4.00"));

        Map<String, Object> stand = service.getMobileStand("token", LocalDate.of(2020, 4, 6));

        assertEquals(5, stand.size());
        assertEquals(LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG, stand.get("phase"));
        assertEquals("Wiedereingliederung", stand.get("phaseLabel"));
        assertEquals(new BigDecimal("4.00"), stand.get("heuteGeplanteStunden"));
        assertEquals(LocalDate.of(2020, 3, 1), stand.get("seit"));
        assertNull(stand.get("bisDatum"));
        assertFalse(stand.toString().contains("Mustermann"), "DSGVO: kein Klarname im Mobile-Ergebnis");
        assertFalse(stand.toString().contains("Vertraulich"), "DSGVO: keine Notiz im Mobile-Ergebnis");
    }

    // ==================== pruefeUrlaubsHinweise ====================

    @Test
    void pruefeUrlaubsHinweise_LiefertHinweisBeiUeberlappenderKrankmeldung() {
        Langzeitkrankmeldung meldung = meldungMitStatus(LangzeitkrankmeldungStatus.LAUFEND, null);
        when(repository.findUeberlappende(eq(MITARBEITER_ID), any(), any())).thenReturn(List.of(meldung));

        List<String> hinweise = service.pruefeUrlaubsHinweise(MITARBEITER_ID, LocalDate.of(2020, 3, 10),
                LocalDate.of(2020, 3, 15));

        assertEquals(1, hinweise.size());
        assertTrue(hinweise.get(0).contains("2020-03-01"));
    }

    @Test
    void pruefeUrlaubsHinweise_KeineUeberlappung_LeereListe() {
        when(repository.findUeberlappende(eq(MITARBEITER_ID), any(), any())).thenReturn(List.of());

        List<String> hinweise = service.pruefeUrlaubsHinweise(MITARBEITER_ID, LocalDate.of(2020, 6, 10),
                LocalDate.of(2020, 6, 15));

        assertTrue(hinweise.isEmpty());
    }
}
