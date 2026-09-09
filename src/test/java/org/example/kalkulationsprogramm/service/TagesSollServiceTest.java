package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Feiertag;
import org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungPhase;
import org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungPhaseTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Zeitkonto;
import org.example.kalkulationsprogramm.repository.LangzeitkrankmeldungPhaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testet {@link TagesSollService} - die Buendelung der drei bisher
 * dupliziert vorliegenden Feiertags-/Sollstunden-Berechnungen (siehe E2 im
 * Plan "Langzeitkrankmeldung").
 *
 * Fixture identisch zu den Charakterisierungstests aus Task 2
 * ({@code TagesSollCharakterisierung*}): Zeitkonto Mo-Fr 8,00 h, Sa/So
 * 0,00 h; dieselben fuenf Stichtage. Ohne laufende Wiedereingliederung muss
 * {@code periodenSoll} bitgleich zum heutigen {@code ZeitkontoService}-
 * Bestand sein, {@code feiertagsGutschrift} bitgleich zum heutigen
 * {@code MonatsSaldoService}/{@code ZeiterfassungApiService}-Bestand.
 *
 * Dummy-Daten (DSGVO): Max Mustermann, ID 1.
 */
@ExtendWith(MockitoExtension.class)
class TagesSollServiceTest {

    @Mock
    private FeiertagService feiertagService;

    @Mock
    private LangzeitkrankmeldungPhaseRepository phaseRepository;

    @Mock
    private org.example.kalkulationsprogramm.repository.ZeitkontoVersionRepository versionRepository;

    @InjectMocks
    private TagesSollService tagesSollService;

    private Zeitkonto zeitkonto;

    private static final Long MITARBEITER_ID = 1L;

    // Gemeinsame Fixture aus Task 2: Mo-Fr 8,00 h, Sa/So 0,00 h.
    private static final LocalDate MONTAG_NORMAL = LocalDate.of(2026, 6, 1);
    private static final LocalDate SAMSTAG_WOCHENENDE = LocalDate.of(2026, 6, 6);
    private static final LocalDate VOLLER_FEIERTAG = LocalDate.of(2026, 1, 1); // Donnerstag, Neujahr
    private static final LocalDate HALBER_FEIERTAG = LocalDate.of(2026, 12, 24); // Donnerstag, Heiligabend
    private static final LocalDate FEIERTAG_AM_WOCHENENDE = LocalDate.of(2026, 12, 26); // Samstag

    @BeforeEach
    void setUp() {
        Mitarbeiter mitarbeiter = new Mitarbeiter();
        mitarbeiter.setId(MITARBEITER_ID);
        mitarbeiter.setVorname("Max");
        mitarbeiter.setNachname("Mustermann");

        zeitkonto = new Zeitkonto(mitarbeiter);
        zeitkonto.setMontagStunden(new BigDecimal("8.00"));
        zeitkonto.setDienstagStunden(new BigDecimal("8.00"));
        zeitkonto.setMittwochStunden(new BigDecimal("8.00"));
        zeitkonto.setDonnerstagStunden(new BigDecimal("8.00"));
        zeitkonto.setFreitagStunden(new BigDecimal("8.00"));
        zeitkonto.setSamstagStunden(new BigDecimal("0.00"));
        zeitkonto.setSonntagStunden(new BigDecimal("0.00"));
    }

    private static LangzeitkrankmeldungPhase wiedereingliederungsPhase(BigDecimal stundenProTag, LocalDate tag) {
        LangzeitkrankmeldungPhase phase = new LangzeitkrankmeldungPhase();
        phase.setTyp(LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG);
        phase.setStundenProTag(stundenProTag);
        phase.setVonDatum(tag.minusDays(10));
        phase.setBisDatum(null); // laeuft noch
        return phase;
    }

    private void stubOhnePhase(LocalDate tag) {
        when(phaseRepository.findImZeitraum(MITARBEITER_ID, tag, tag)).thenReturn(Collections.emptyList());
    }

    private void stubMitPhase(LocalDate tag, LangzeitkrankmeldungPhase phase) {
        when(phaseRepository.findImZeitraum(MITARBEITER_ID, tag, tag)).thenReturn(List.of(phase));
    }

    /**
     * Stubt {@code getFeiertagInfo} statt der beiden getrennten Methoden
     * {@code istFeiertag}/{@code istHalberFeiertag} - seit Befund 2 (Abschnitt 4)
     * fragt {@code berechneEinzeltag} beides in EINEM Zugriff ab.
     */
    private void stubFeiertag(LocalDate tag, boolean istFeiertag, boolean istHalberFeiertag) {
        Optional<Feiertag> ergebnis = istFeiertag
                ? Optional.of(new Feiertag(tag, "Test-Feiertag", "BY", istHalberFeiertag))
                : Optional.empty();
        when(feiertagService.getFeiertagInfo(tag)).thenReturn(ergebnis);
    }

    private void assertWerte(LocalDate tag, String erwartetPeriodenSoll, String erwartetFeiertagsGutschrift,
            String erwartetArbeitsSoll) {
        assertEquals(0, new BigDecimal(erwartetPeriodenSoll)
                .compareTo(tagesSollService.periodenSoll(MITARBEITER_ID, zeitkonto, tag)), "periodenSoll");
        assertEquals(0, new BigDecimal(erwartetFeiertagsGutschrift)
                .compareTo(tagesSollService.feiertagsGutschrift(MITARBEITER_ID, zeitkonto, tag)),
                "feiertagsGutschrift");
        assertEquals(0, new BigDecimal(erwartetArbeitsSoll)
                .compareTo(tagesSollService.arbeitsSoll(MITARBEITER_ID, zeitkonto, tag)), "arbeitsSoll");
    }

    // ---- (a) ohne Phase = heutiger Bestand fuer alle fuenf Tagestypen ----

    @Test
    void montag_ohnePhase_normalerArbeitstag() {
        stubOhnePhase(MONTAG_NORMAL);
        stubFeiertag(MONTAG_NORMAL, false, false);

        assertWerte(MONTAG_NORMAL, "8.00", "0", "8.00");
    }

    @Test
    void samstag_ohnePhase_wochenende_allesNull() {
        stubOhnePhase(SAMSTAG_WOCHENENDE);

        assertWerte(SAMSTAG_WOCHENENDE, "0", "0", "0");
    }

    @Test
    void vollerFeiertag_ohnePhase_zaehltAlsBezahlterArbeitstagMitVollerGutschrift() {
        stubOhnePhase(VOLLER_FEIERTAG);
        stubFeiertag(VOLLER_FEIERTAG, true, false);

        assertWerte(VOLLER_FEIERTAG, "8.00", "8.00", "0.00");
    }

    @Test
    void halberFeiertag_ohnePhase_periodenSollUndGutschriftHalbiert() {
        stubOhnePhase(HALBER_FEIERTAG);
        stubFeiertag(HALBER_FEIERTAG, true, true);

        assertWerte(HALBER_FEIERTAG, "4.00", "4.00", "0.00");
    }

    @Test
    void feiertagAmWochenende_ohnePhase_allesNull() {
        stubOhnePhase(FEIERTAG_AM_WOCHENENDE);

        assertWerte(FEIERTAG_AM_WOCHENENDE, "0", "0", "0");
    }

    // ---- (b)-(d) Wiedereingliederung ----

    @Test
    void wiedereingliederung_zweiStunden_amNormalenMontag() {
        stubMitPhase(MONTAG_NORMAL, wiedereingliederungsPhase(new BigDecimal("2.00"), MONTAG_NORMAL));
        stubFeiertag(MONTAG_NORMAL, false, false);

        assertWerte(MONTAG_NORMAL, "2.00", "0", "2.00");
    }

    @Test
    void wiedereingliederung_zweiStunden_amVollenFeiertag_bautKeineMinusstundenAuf() {
        // E3 im Plan: der Stufenplan-Wert ist die Basis, der Feiertag reduziert
        // ihn zusaetzlich - kein fester Override.
        stubMitPhase(VOLLER_FEIERTAG, wiedereingliederungsPhase(new BigDecimal("2.00"), VOLLER_FEIERTAG));
        stubFeiertag(VOLLER_FEIERTAG, true, false);

        assertWerte(VOLLER_FEIERTAG, "2.00", "2.00", "0");
    }

    @Test
    void wiedereingliederung_zweiStunden_amHalbenFeiertag() {
        stubMitPhase(HALBER_FEIERTAG, wiedereingliederungsPhase(new BigDecimal("2.00"), HALBER_FEIERTAG));
        stubFeiertag(HALBER_FEIERTAG, true, true);

        assertWerte(HALBER_FEIERTAG, "1.00", "1.00", "0");
    }

    @Test
    void wiedereingliederung_amSamstag_zeitkontoNull_allesNull() {
        stubMitPhase(SAMSTAG_WOCHENENDE, wiedereingliederungsPhase(new BigDecimal("2.00"), SAMSTAG_WOCHENENDE));

        assertWerte(SAMSTAG_WOCHENENDE, "0", "0", "0");
    }

    // ---- (f) Sicherheitsnetz: Stufenplan darf das Zeitkonto-Soll nie erhoehen ----

    @Test
    void wiedereingliederung_stundenProTagUeberSchreitetZeitkonto_wirdAufZeitkontoGedeckelt() {
        stubMitPhase(MONTAG_NORMAL, wiedereingliederungsPhase(new BigDecimal("10.00"), MONTAG_NORMAL));
        stubFeiertag(MONTAG_NORMAL, false, false);

        assertWerte(MONTAG_NORMAL, "8.00", "0", "8.00");
    }

    // ---- (g) andere Phasentypen aendern nichts ----

    @Test
    void lohnfortzahlungsPhase_aendertNichtsAmTagessoll() {
        LangzeitkrankmeldungPhase phase = new LangzeitkrankmeldungPhase();
        phase.setTyp(LangzeitkrankmeldungPhaseTyp.LOHNFORTZAHLUNG);
        phase.setVonDatum(MONTAG_NORMAL.minusDays(10));
        phase.setBisDatum(null);
        stubMitPhase(MONTAG_NORMAL, phase);
        stubFeiertag(MONTAG_NORMAL, false, false);

        assertWerte(MONTAG_NORMAL, "8.00", "0", "8.00");
    }

    @Test
    void krankengeldPhase_aendertNichtsAmTagessoll() {
        LangzeitkrankmeldungPhase phase = new LangzeitkrankmeldungPhase();
        phase.setTyp(LangzeitkrankmeldungPhaseTyp.KRANKENGELD);
        phase.setVonDatum(MONTAG_NORMAL.minusDays(10));
        phase.setBisDatum(null);
        stubMitPhase(MONTAG_NORMAL, phase);
        stubFeiertag(MONTAG_NORMAL, false, false);

        assertWerte(MONTAG_NORMAL, "8.00", "0", "8.00");
    }

    @Test
    void krankengeldPhaseMitGesetztemStundenProTag_wirdIgnoriert_zeitkontoWertGilt() {
        // Nachbesserung Abschnitt 2, Befund 3: stunden_pro_tag ist in der
        // Datenbank nur nullable, NICHT auf den Typ WIEDEREINGLIEDERUNG
        // eingeschraenkt. Die bisherigen Tests fuer LOHNFORTZAHLUNG/KRANKENGELD
        // setzen stundenProTag nie, weshalb in Wahrheit nur die Null-Pruefung
        // in tagesBasis() trug -- der Typfilter selbst war ungetestet. Dieser
        // Test setzt stundenProTag bewusst auf einer KRANKENGELD-Phase, um genau
        // den Typfilter scharf zu stellen.
        LangzeitkrankmeldungPhase phase = new LangzeitkrankmeldungPhase();
        phase.setTyp(LangzeitkrankmeldungPhaseTyp.KRANKENGELD);
        phase.setVonDatum(MONTAG_NORMAL.minusDays(10));
        phase.setBisDatum(null);
        phase.setStundenProTag(new BigDecimal("2.00"));
        stubMitPhase(MONTAG_NORMAL, phase);
        stubFeiertag(MONTAG_NORMAL, false, false);

        // Erwartung: der Stufenplan-Wert (2.00) wird ignoriert, es zaehlt der
        // Zeitkonto-Wert (8.00) -- eine Krankengeld-Phase ist kein Stufenplan.
        assertWerte(MONTAG_NORMAL, "8.00", "0", "8.00");
    }

    @Test
    void wiedereingliederung_stundenProTagNull_phaseWirdIgnoriertZeitkontoWertGilt() {
        // Nachbesserung Abschnitt 2, Befund 3: Plan-Vorgabe fuer eine
        // WIEDEREINGLIEDERUNG-Phase ohne gesetzten Stufenplan-Wert -- die Phase
        // wird ignoriert, es wird mit dem Zeitkonto-Wert weitergerechnet. War
        // bisher ebenfalls ungetestet.
        stubMitPhase(MONTAG_NORMAL, wiedereingliederungsPhase(null, MONTAG_NORMAL));
        stubFeiertag(MONTAG_NORMAL, false, false);

        assertWerte(MONTAG_NORMAL, "8.00", "0", "8.00");
    }

    // ---- (h) Invariante ----

    @Test
    void arbeitsSoll_istImmerPeriodenSollMinusFeiertagsGutschrift() {
        stubOhnePhase(MONTAG_NORMAL);
        stubFeiertag(MONTAG_NORMAL, false, false);

        stubMitPhase(VOLLER_FEIERTAG, wiedereingliederungsPhase(new BigDecimal("2.00"), VOLLER_FEIERTAG));
        stubFeiertag(VOLLER_FEIERTAG, true, false);

        stubMitPhase(HALBER_FEIERTAG, wiedereingliederungsPhase(new BigDecimal("2.00"), HALBER_FEIERTAG));
        stubFeiertag(HALBER_FEIERTAG, true, true);

        stubOhnePhase(SAMSTAG_WOCHENENDE);

        for (LocalDate tag : List.of(MONTAG_NORMAL, VOLLER_FEIERTAG, HALBER_FEIERTAG, SAMSTAG_WOCHENENDE)) {
            BigDecimal periodenSoll = tagesSollService.periodenSoll(MITARBEITER_ID, zeitkonto, tag);
            BigDecimal feiertagsGutschrift = tagesSollService.feiertagsGutschrift(MITARBEITER_ID, zeitkonto, tag);
            BigDecimal arbeitsSoll = tagesSollService.arbeitsSoll(MITARBEITER_ID, zeitkonto, tag);

            assertEquals(0, periodenSoll.subtract(feiertagsGutschrift).compareTo(arbeitsSoll),
                    "Invariante arbeitsSoll == periodenSoll - feiertagsGutschrift verletzt fuer " + tag);
        }
    }

    // ---- (i) kein N+1 ----

    @Test
    void periodenSollSumme_ueberEinenMonat_ladeElementeGenauEinmal() {
        LocalDate von = LocalDate.of(2026, 1, 1);
        LocalDate bis = LocalDate.of(2026, 1, 31);
        when(phaseRepository.findImZeitraum(MITARBEITER_ID, von, bis)).thenReturn(Collections.emptyList());
        when(feiertagService.getFeiertageZwischen(von, bis)).thenReturn(Collections.emptyList());

        tagesSollService.periodenSollSumme(MITARBEITER_ID, zeitkonto, von, bis);

        verify(phaseRepository, times(1)).findImZeitraum(MITARBEITER_ID, von, bis);
        verify(feiertagService, times(1)).getFeiertageZwischen(von, bis);
        verify(feiertagService, never()).istFeiertag(any());
        verify(feiertagService, never()).istHalberFeiertag(any());
    }

    @Test
    void feiertagsGutschriftSumme_ueberEinenMonat_ladeElementeGenauEinmal() {
        LocalDate von = LocalDate.of(2026, 1, 1);
        LocalDate bis = LocalDate.of(2026, 1, 31);
        when(phaseRepository.findImZeitraum(MITARBEITER_ID, von, bis)).thenReturn(Collections.emptyList());
        when(feiertagService.getFeiertageZwischen(von, bis)).thenReturn(Collections.emptyList());

        tagesSollService.feiertagsGutschriftSumme(MITARBEITER_ID, zeitkonto, von, bis);

        verify(phaseRepository, times(1)).findImZeitraum(MITARBEITER_ID, von, bis);
        verify(feiertagService, times(1)).getFeiertageZwischen(von, bis);
        verify(feiertagService, never()).istFeiertag(any());
        verify(feiertagService, never()).istHalberFeiertag(any());
    }

    // ---- Zeitraum-Methoden: Korrektheit ----

    @Test
    void periodenSollSumme_volleWocheOhneFeiertag_gibtVierzig() {
        LocalDate von = LocalDate.of(2026, 6, 1);
        LocalDate bis = LocalDate.of(2026, 6, 7);
        when(phaseRepository.findImZeitraum(MITARBEITER_ID, von, bis)).thenReturn(Collections.emptyList());
        when(feiertagService.getFeiertageZwischen(von, bis)).thenReturn(Collections.emptyList());

        BigDecimal result = tagesSollService.periodenSollSumme(MITARBEITER_ID, zeitkonto, von, bis);

        assertEquals(0, new BigDecimal("40").compareTo(result));
    }

    @Test
    void feiertagsGutschriftSumme_volleWocheMitVollemFeiertag_gibtAcht() {
        LocalDate von = LocalDate.of(2026, 12, 28);
        LocalDate bis = LocalDate.of(2027, 1, 3);
        LocalDate neujahr = LocalDate.of(2027, 1, 1); // Freitag
        when(phaseRepository.findImZeitraum(MITARBEITER_ID, von, bis)).thenReturn(Collections.emptyList());
        when(feiertagService.getFeiertageZwischen(von, bis))
                .thenReturn(List.of(new Feiertag(neujahr, "Neujahr", "BY")));

        BigDecimal periodenSoll = tagesSollService.periodenSollSumme(MITARBEITER_ID, zeitkonto, von, bis);
        BigDecimal feiertagsGutschrift = tagesSollService.feiertagsGutschriftSumme(MITARBEITER_ID, zeitkonto, von,
                bis);

        assertEquals(0, new BigDecimal("40").compareTo(periodenSoll),
                "Voller Feiertag zaehlt als bezahlter Arbeitstag - Wochensumme bleibt 40");
        assertEquals(0, new BigDecimal("8.00").compareTo(feiertagsGutschrift));
    }

    @Test
    void periodenSollSumme_feiertagAusAnderemBundesland_wirdIgnoriert() {
        // FeiertagService.getFeiertageZwischen filtert (anders als
        // istFeiertag/istHalberFeiertag) NICHT nach Bundesland - ohne den
        // Filter in TagesSollService wuerde dieser "halbe Feiertag" aus NRW
        // die Sollstunden am Montag faelschlich halbieren.
        when(phaseRepository.findImZeitraum(MITARBEITER_ID, MONTAG_NORMAL, MONTAG_NORMAL))
                .thenReturn(Collections.emptyList());
        when(feiertagService.getFeiertageZwischen(MONTAG_NORMAL, MONTAG_NORMAL))
                .thenReturn(List.of(new Feiertag(MONTAG_NORMAL, "Fronleichnam", "NW", true)));

        BigDecimal periodenSoll = tagesSollService.periodenSollSumme(MITARBEITER_ID, zeitkonto, MONTAG_NORMAL,
                MONTAG_NORMAL);
        BigDecimal feiertagsGutschrift = tagesSollService.feiertagsGutschriftSumme(MITARBEITER_ID, zeitkonto,
                MONTAG_NORMAL, MONTAG_NORMAL);

        assertEquals(0, new BigDecimal("8.00").compareTo(periodenSoll),
                "Feiertag aus einem anderen Bundesland darf die Sollstunden nicht halbieren");
        assertEquals(0, BigDecimal.ZERO.compareTo(feiertagsGutschrift));
    }

    // ---- (j) Einzeltag: istFeiertag/istHalberFeiertag zusammengelegt ----

    @Test
    void arbeitsSoll_einzeltag_fragtFeiertagsdatenNurEinmalAb() {
        // Befund 2 (Abschnitt 4): berechneEinzeltag fragte istFeiertag UND
        // istHalberFeiertag getrennt ab - zwei FeiertagService-Aufrufe (und
        // darunter vier FeiertagRepository-Abfragen) fuer eine Information,
        // die getFeiertagInfo in einem Zugriff liefert.
        stubOhnePhase(VOLLER_FEIERTAG);
        stubFeiertag(VOLLER_FEIERTAG, true, false);

        tagesSollService.arbeitsSoll(MITARBEITER_ID, zeitkonto, VOLLER_FEIERTAG);

        verify(feiertagService, times(1)).getFeiertagInfo(VOLLER_FEIERTAG);
        verify(feiertagService, never()).istFeiertag(any());
        verify(feiertagService, never()).istHalberFeiertag(any());
    }

    // ---- (k) Zeitraum-Variante je Tag: eine Ladung, Werte pro Tag ----

    @Test
    void arbeitsSollJeTag_ladeElementeGenauEinmal_undFragtNieEinzeltagFeiertagsdatenAb() {
        LocalDate von = LocalDate.of(2026, 1, 1);
        LocalDate bis = LocalDate.of(2026, 1, 31);
        when(phaseRepository.findImZeitraum(MITARBEITER_ID, von, bis)).thenReturn(Collections.emptyList());
        when(feiertagService.getFeiertageZwischen(von, bis)).thenReturn(Collections.emptyList());

        tagesSollService.arbeitsSollJeTag(MITARBEITER_ID, zeitkonto, von, bis);

        verify(phaseRepository, times(1)).findImZeitraum(MITARBEITER_ID, von, bis);
        verify(feiertagService, times(1)).getFeiertageZwischen(von, bis);
        verify(feiertagService, never()).istFeiertag(any());
        verify(feiertagService, never()).istHalberFeiertag(any());
        verify(feiertagService, never()).getFeiertagInfo(any());
    }

    @Test
    void periodenSollJeTagUndFeiertagsGutschriftJeTag_ladenElementeGenauEinmal() {
        LocalDate von = LocalDate.of(2026, 1, 1);
        LocalDate bis = LocalDate.of(2026, 1, 31);
        when(phaseRepository.findImZeitraum(MITARBEITER_ID, von, bis)).thenReturn(Collections.emptyList());
        when(feiertagService.getFeiertageZwischen(von, bis)).thenReturn(Collections.emptyList());

        tagesSollService.periodenSollJeTag(MITARBEITER_ID, zeitkonto, von, bis);
        tagesSollService.feiertagsGutschriftJeTag(MITARBEITER_ID, zeitkonto, von, bis);

        // Zwei Aufrufe (einer je Methode), aber jeweils genau EINE Ladung pro
        // Aufruf - keine Ladung pro Tag.
        verify(phaseRepository, times(2)).findImZeitraum(MITARBEITER_ID, von, bis);
        verify(feiertagService, times(2)).getFeiertageZwischen(von, bis);
    }

    @Test
    void arbeitsSollJeTag_stimmtMitEinzeltagWertenUeberein_vollerUndHalberFeiertagPlusWochenende() {
        LocalDate von = LocalDate.of(2026, 12, 28); // Montag
        LocalDate bis = LocalDate.of(2027, 1, 3); // Sonntag
        LocalDate neujahr = LocalDate.of(2027, 1, 1); // Freitag, voller Feiertag
        when(phaseRepository.findImZeitraum(MITARBEITER_ID, von, bis)).thenReturn(Collections.emptyList());
        when(feiertagService.getFeiertageZwischen(von, bis))
                .thenReturn(List.of(new Feiertag(neujahr, "Neujahr", "BY")));

        Map<LocalDate, BigDecimal> periodenSollJeTag = tagesSollService.periodenSollJeTag(MITARBEITER_ID, zeitkonto,
                von, bis);
        Map<LocalDate, BigDecimal> feiertagsGutschriftJeTag = tagesSollService.feiertagsGutschriftJeTag(
                MITARBEITER_ID, zeitkonto, von, bis);
        Map<LocalDate, BigDecimal> arbeitsSollJeTag = tagesSollService.arbeitsSollJeTag(MITARBEITER_ID, zeitkonto,
                von, bis);

        assertEquals(7, periodenSollJeTag.size(), "ein Karteneintrag pro Tag im Zeitraum");
        assertEquals(0, new BigDecimal("8.00").compareTo(periodenSollJeTag.get(neujahr)));
        assertEquals(0, new BigDecimal("8.00").compareTo(feiertagsGutschriftJeTag.get(neujahr)));
        assertEquals(0, BigDecimal.ZERO.compareTo(arbeitsSollJeTag.get(neujahr)));

        LocalDate samstag = LocalDate.of(2027, 1, 2);
        assertEquals(0, BigDecimal.ZERO.compareTo(periodenSollJeTag.get(samstag)));

        LocalDate normalerMontag = LocalDate.of(2026, 12, 28);
        assertEquals(0, new BigDecimal("8.00").compareTo(periodenSollJeTag.get(normalerMontag)));
        assertEquals(0, BigDecimal.ZERO.compareTo(feiertagsGutschriftJeTag.get(normalerMontag)));
        assertEquals(0, new BigDecimal("8.00").compareTo(arbeitsSollJeTag.get(normalerMontag)));

        for (LocalDate tag : periodenSollJeTag.keySet()) {
            assertEquals(0, periodenSollJeTag.get(tag).subtract(feiertagsGutschriftJeTag.get(tag))
                    .compareTo(arbeitsSollJeTag.get(tag)), "Invariante verletzt fuer " + tag);
        }
    }

    @Test
    void versionen38komma5Und7Ergeben45komma5UndHistorieBleibtBeiDeaktivierung() {
        LocalDate von = LocalDate.of(2026, 6, 1);
        LocalDate wechsel = von.plusWeeks(1);
        LocalDate bis = von.plusDays(13);
        var alt = version(von, wechsel.minusDays(1));
        alt.setMontagStunden(new BigDecimal("8"));
        alt.setDienstagStunden(new BigDecimal("8"));
        alt.setMittwochStunden(new BigDecimal("8"));
        alt.setDonnerstagStunden(new BigDecimal("8"));
        alt.setFreitagStunden(new BigDecimal("6.5"));
        var neu = version(wechsel, null);
        neu.setMontagStunden(new BigDecimal("7"));
        when(versionRepository.findImZeitraum(1L, von, bis)).thenReturn(List.of(alt, neu));
        assertEquals(0, new BigDecimal("45.5").compareTo(tagesSollService.periodenSollSumme(1L, von, bis)));
        verify(versionRepository, times(1)).findImZeitraum(1L, von, bis);
        verify(phaseRepository, times(1)).findImZeitraum(1L, von, bis);
        verify(feiertagService, times(1)).getFeiertageZwischen(von, bis);
        alt.getMitarbeiter().setFuehrtZeitkonto(false);
        when(versionRepository.findImZeitraum(1L, von, wechsel.minusDays(1))).thenReturn(List.of(alt));
        assertEquals(0, new BigDecimal("38.5").compareTo(
                tagesSollService.periodenSollSumme(1L, von, wechsel.minusDays(1))));
        verify(versionRepository, never()).save(any());
    }

    @Test
    void versionierteFeiertageUndStufenplanBehaltenAlleRechenregeln() {
        LocalDate von = LocalDate.of(2026, 12, 21);
        LocalDate bis = von.plusDays(6);
        var v = version(von, null);
        v.setMontagStunden(new BigDecimal("8"));
        v.setDienstagStunden(new BigDecimal("8"));
        v.setMittwochStunden(new BigDecimal("8"));
        v.setDonnerstagStunden(new BigDecimal("8"));
        v.setFreitagStunden(new BigDecimal("8"));
        when(versionRepository.findImZeitraum(1L, von, bis)).thenReturn(List.of(v));
        when(phaseRepository.findImZeitraum(1L, von, bis))
                .thenReturn(List.of(wiedereingliederungsPhase(new BigDecimal("3.5"), von)));
        Feiertag halb = new Feiertag(); halb.setDatum(von.plusDays(3)); halb.setBundesland("BY"); halb.setHalbTag(true);
        Feiertag voll = new Feiertag(); voll.setDatum(von.plusDays(4)); voll.setBundesland("BY");
        Feiertag fremd = new Feiertag(); fremd.setDatum(von); fremd.setBundesland("HE");
        Feiertag wochenende = new Feiertag(); wochenende.setDatum(bis); wochenende.setBundesland("BY");
        when(feiertagService.getFeiertageZwischen(von, bis)).thenReturn(List.of(halb, voll, fremd, wochenende));
        assertEquals(0, new BigDecimal("15.75").compareTo(tagesSollService.periodenSollSumme(1L, von, bis)));
        assertEquals(0, new BigDecimal("5.25").compareTo(tagesSollService.feiertagsGutschriftSumme(1L, von, bis)));
        assertEquals(0, new BigDecimal("10.50").compareTo(tagesSollService.arbeitsSollSumme(1L, von, bis)));
        when(versionRepository.findImZeitraum(1L, halb.getDatum(), halb.getDatum())).thenReturn(List.of(v));
        when(phaseRepository.findImZeitraum(1L, halb.getDatum(), halb.getDatum()))
                .thenReturn(List.of(wiedereingliederungsPhase(new BigDecimal("3.5"), von)));
        when(feiertagService.getFeiertageZwischen(halb.getDatum(), halb.getDatum())).thenReturn(List.of(halb));
        assertEquals(0, new BigDecimal("1.75").compareTo(tagesSollService.periodenSoll(1L, halb.getDatum())));
        assertEquals(0, new BigDecimal("1.75").compareTo(tagesSollService.feiertagsGutschrift(1L, halb.getDatum())));
        assertEquals(0, BigDecimal.ZERO.compareTo(tagesSollService.arbeitsSoll(1L, halb.getDatum())));
    }

    @Test
    void fehlendeVersionUndKontopauseLiefernNullOhneStillesStandardkonto() {
        LocalDate von = LocalDate.of(2026, 6, 1);
        var v = version(von.plusDays(7), null); v.setMontagStunden(new BigDecimal("7"));
        when(versionRepository.findImZeitraum(1L, von, von.plusDays(7))).thenReturn(List.of(v));
        var tage = tagesSollService.periodenSollJeTag(1L, von, von.plusDays(7));
        assertEquals(BigDecimal.ZERO, tage.get(von));
        assertEquals(new BigDecimal("7"), tage.get(von.plusDays(7)));
        assertEquals(BigDecimal.ZERO, tagesSollService.periodenSoll(1L, von));
        verify(versionRepository, never()).save(any());
    }

    private org.example.kalkulationsprogramm.domain.ZeitkontoVersion version(LocalDate von, LocalDate bis) {
        var v = new org.example.kalkulationsprogramm.domain.ZeitkontoVersion();
        v.setMitarbeiter(zeitkonto.getMitarbeiter()); v.setGueltigVon(von); v.setGueltigBis(bis);
        return v;
    }
}
