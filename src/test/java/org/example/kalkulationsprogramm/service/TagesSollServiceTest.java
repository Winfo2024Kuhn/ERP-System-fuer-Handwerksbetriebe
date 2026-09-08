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
        when(feiertagService.istFeiertag(MONTAG_NORMAL)).thenReturn(false);
        when(feiertagService.istHalberFeiertag(MONTAG_NORMAL)).thenReturn(false);

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
        when(feiertagService.istFeiertag(VOLLER_FEIERTAG)).thenReturn(true);
        when(feiertagService.istHalberFeiertag(VOLLER_FEIERTAG)).thenReturn(false);

        assertWerte(VOLLER_FEIERTAG, "8.00", "8.00", "0.00");
    }

    @Test
    void halberFeiertag_ohnePhase_periodenSollUndGutschriftHalbiert() {
        stubOhnePhase(HALBER_FEIERTAG);
        when(feiertagService.istFeiertag(HALBER_FEIERTAG)).thenReturn(true);
        when(feiertagService.istHalberFeiertag(HALBER_FEIERTAG)).thenReturn(true);

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
        when(feiertagService.istFeiertag(MONTAG_NORMAL)).thenReturn(false);
        when(feiertagService.istHalberFeiertag(MONTAG_NORMAL)).thenReturn(false);

        assertWerte(MONTAG_NORMAL, "2.00", "0", "2.00");
    }

    @Test
    void wiedereingliederung_zweiStunden_amVollenFeiertag_bautKeineMinusstundenAuf() {
        // E3 im Plan: der Stufenplan-Wert ist die Basis, der Feiertag reduziert
        // ihn zusaetzlich - kein fester Override.
        stubMitPhase(VOLLER_FEIERTAG, wiedereingliederungsPhase(new BigDecimal("2.00"), VOLLER_FEIERTAG));
        when(feiertagService.istFeiertag(VOLLER_FEIERTAG)).thenReturn(true);
        when(feiertagService.istHalberFeiertag(VOLLER_FEIERTAG)).thenReturn(false);

        assertWerte(VOLLER_FEIERTAG, "2.00", "2.00", "0");
    }

    @Test
    void wiedereingliederung_zweiStunden_amHalbenFeiertag() {
        stubMitPhase(HALBER_FEIERTAG, wiedereingliederungsPhase(new BigDecimal("2.00"), HALBER_FEIERTAG));
        when(feiertagService.istFeiertag(HALBER_FEIERTAG)).thenReturn(true);
        when(feiertagService.istHalberFeiertag(HALBER_FEIERTAG)).thenReturn(true);

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
        when(feiertagService.istFeiertag(MONTAG_NORMAL)).thenReturn(false);
        when(feiertagService.istHalberFeiertag(MONTAG_NORMAL)).thenReturn(false);

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
        when(feiertagService.istFeiertag(MONTAG_NORMAL)).thenReturn(false);
        when(feiertagService.istHalberFeiertag(MONTAG_NORMAL)).thenReturn(false);

        assertWerte(MONTAG_NORMAL, "8.00", "0", "8.00");
    }

    @Test
    void krankengeldPhase_aendertNichtsAmTagessoll() {
        LangzeitkrankmeldungPhase phase = new LangzeitkrankmeldungPhase();
        phase.setTyp(LangzeitkrankmeldungPhaseTyp.KRANKENGELD);
        phase.setVonDatum(MONTAG_NORMAL.minusDays(10));
        phase.setBisDatum(null);
        stubMitPhase(MONTAG_NORMAL, phase);
        when(feiertagService.istFeiertag(MONTAG_NORMAL)).thenReturn(false);
        when(feiertagService.istHalberFeiertag(MONTAG_NORMAL)).thenReturn(false);

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
        when(feiertagService.istFeiertag(MONTAG_NORMAL)).thenReturn(false);
        when(feiertagService.istHalberFeiertag(MONTAG_NORMAL)).thenReturn(false);

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
        when(feiertagService.istFeiertag(MONTAG_NORMAL)).thenReturn(false);
        when(feiertagService.istHalberFeiertag(MONTAG_NORMAL)).thenReturn(false);

        assertWerte(MONTAG_NORMAL, "8.00", "0", "8.00");
    }

    // ---- (h) Invariante ----

    @Test
    void arbeitsSoll_istImmerPeriodenSollMinusFeiertagsGutschrift() {
        stubOhnePhase(MONTAG_NORMAL);
        when(feiertagService.istFeiertag(MONTAG_NORMAL)).thenReturn(false);
        when(feiertagService.istHalberFeiertag(MONTAG_NORMAL)).thenReturn(false);

        stubMitPhase(VOLLER_FEIERTAG, wiedereingliederungsPhase(new BigDecimal("2.00"), VOLLER_FEIERTAG));
        when(feiertagService.istFeiertag(VOLLER_FEIERTAG)).thenReturn(true);
        when(feiertagService.istHalberFeiertag(VOLLER_FEIERTAG)).thenReturn(false);

        stubMitPhase(HALBER_FEIERTAG, wiedereingliederungsPhase(new BigDecimal("2.00"), HALBER_FEIERTAG));
        when(feiertagService.istFeiertag(HALBER_FEIERTAG)).thenReturn(true);
        when(feiertagService.istHalberFeiertag(HALBER_FEIERTAG)).thenReturn(true);

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
}
