package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.ErfassungsQuelle;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.mapper.ArbeitsgangMapper;
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Regression 29.07.2026: Zwei Mitarbeiter hatten am Handy Feierabend gestempelt,
 * der Stop erreichte den Server aber nicht rechtzeitig. Der Auto-Stop schloss die
 * Buchungen um 20:00. Als der echte Stop nachkam, lehnte der Server ihn mit
 * "Keine aktive Buchung gefunden" ab - die tatsaechlich gestempelte Zeit landete
 * in der Reparatur-Liste am Handy, waehrend die erfundene 20:00 im Zeitkonto
 * stehen blieb.
 *
 * Dummy-Daten gemaess DSGVO: "Max Mustermann".
 */
@ExtendWith(MockitoExtension.class)
class ZeiterfassungApiServiceVerspaeteterStopTest {

    @Mock private ProjektRepository projektRepository;
    @Mock private MitarbeiterRepository mitarbeiterRepository;
    @Mock private ArbeitsgangRepository arbeitsgangRepository;
    @Mock private ZeitbuchungRepository zeitbuchungRepository;
    @Mock private AbwesenheitRepository abwesenheitRepository;
    @Mock private ProduktkategorieRepository produktkategorieRepository;
    @Mock private ArbeitsgangStundensatzRepository arbeitsgangStundensatzRepository;
    @Mock private ArbeitsgangMapper arbeitsgangMapper;
    @Mock private DateiSpeicherService dateiSpeicherService;
    @Mock private LieferantenRepository lieferantenRepository;
    @Mock private FeiertagService feiertagService;
    @Mock private ZeitbuchungAuditService auditService;
    @Mock private MonatsSaldoService monatsSaldoService;

    private ZeiterfassungApiService service;

    private static final String TOKEN = "test-token-max-mustermann";
    private static final Long MITARBEITER_ID = 42L;

    /** Der Arbeitstag aus dem Bug: Start 16:48, echter Feierabend 17:00. */
    private static final LocalDateTime START = LocalDateTime.of(2026, 7, 29, 16, 48, 0);
    private static final LocalDateTime ECHTER_FEIERABEND = LocalDateTime.of(2026, 7, 29, 17, 0, 0);
    private static final LocalDateTime AUTO_STOP = LocalDateTime.of(2026, 7, 29, 20, 0, 0);

    @BeforeEach
    void setUp() {
        service = new ZeiterfassungApiService(
                projektRepository, mitarbeiterRepository, arbeitsgangRepository,
                zeitbuchungRepository, abwesenheitRepository, produktkategorieRepository,
                arbeitsgangStundensatzRepository, arbeitsgangMapper, dateiSpeicherService,
                lieferantenRepository, feiertagService, auditService);
        ReflectionTestUtils.setField(service, "monatsSaldoService", monatsSaldoService);
    }

    private Mitarbeiter dummyMitarbeiter() {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(MITARBEITER_ID);
        m.setVorname("Max");
        m.setNachname("Mustermann");
        return m;
    }

    /** Eine bereits vom Auto-Stop auf 20:00 geschlossene Buchung. */
    private Zeitbuchung automatischBeendeteBuchung(Mitarbeiter mitarbeiter) {
        Zeitbuchung b = new Zeitbuchung();
        b.setId(1903L);
        b.setMitarbeiter(mitarbeiter);
        b.setStartZeit(START);
        b.setEndeZeit(AUTO_STOP);
        b.setAnzahlInStunden(new BigDecimal("3.20"));
        b.setAutomatischBeendet(true);
        b.setVersion(2);
        return b;
    }

    /** Kein offener Eintrag mehr - der Auto-Stop war schneller. */
    private void keineAktiveBuchung(Mitarbeiter mitarbeiter) {
        when(mitarbeiterRepository.findByLoginTokenAndAktivTrueForUpdate(TOKEN))
                .thenReturn(Optional.of(mitarbeiter));
        when(zeitbuchungRepository.findFirstByMitarbeiterIdAndEndeZeitIsNullOrderByStartZeitDesc(MITARBEITER_ID))
                .thenReturn(Optional.empty());
    }

    @Test
    void traegtVerspaetetenFeierabendNachUndErsetztGeschaetzteEndezeit() {
        Mitarbeiter mitarbeiter = dummyMitarbeiter();
        Zeitbuchung buchung = automatischBeendeteBuchung(mitarbeiter);
        keineAktiveBuchung(mitarbeiter);
        when(zeitbuchungRepository
                .findFirstByMitarbeiterIdAndAutomatischBeendetTrueAndStartZeitBetweenOrderByStartZeitDesc(
                        eq(MITARBEITER_ID), any(), any()))
                .thenReturn(Optional.of(buchung));

        Map<String, Object> result = service.stopZeiterfassung(TOKEN, ECHTER_FEIERABEND, "stop-key-1");

        assertThat(buchung.getEndeZeit()).isEqualTo(ECHTER_FEIERABEND);
        // 16:48 bis 17:00 = 12 Minuten = 0.20 Stunden (statt der erfundenen 3.20)
        assertThat(buchung.getAnzahlInStunden()).isEqualByComparingTo(new BigDecimal("0.20"));
        assertThat(result.get("status")).isEqualTo("nachgetragen");
        verify(zeitbuchungRepository).save(buchung);
        verify(monatsSaldoService).invalidiereFuerDateTime(MITARBEITER_ID, START);
    }

    @Test
    void raeumtPruefKennzeichenNachDemNachtragWiederAb() {
        Mitarbeiter mitarbeiter = dummyMitarbeiter();
        Zeitbuchung buchung = automatischBeendeteBuchung(mitarbeiter);
        keineAktiveBuchung(mitarbeiter);
        when(zeitbuchungRepository
                .findFirstByMitarbeiterIdAndAutomatischBeendetTrueAndStartZeitBetweenOrderByStartZeitDesc(
                        eq(MITARBEITER_ID), any(), any()))
                .thenReturn(Optional.of(buchung));

        service.stopZeiterfassung(TOKEN, ECHTER_FEIERABEND, "stop-key-1");

        assertThat(buchung.isAutomatischBeendet()).isFalse();
    }

    @Test
    void protokolliertNachtragGoBDKonformMitVerweisAufDieAlteZeit() {
        Mitarbeiter mitarbeiter = dummyMitarbeiter();
        Zeitbuchung buchung = automatischBeendeteBuchung(mitarbeiter);
        keineAktiveBuchung(mitarbeiter);
        when(zeitbuchungRepository
                .findFirstByMitarbeiterIdAndAutomatischBeendetTrueAndStartZeitBetweenOrderByStartZeitDesc(
                        eq(MITARBEITER_ID), any(), any()))
                .thenReturn(Optional.of(buchung));

        service.stopZeiterfassung(TOKEN, ECHTER_FEIERABEND, "stop-key-1");

        ArgumentCaptor<String> grund = ArgumentCaptor.forClass(String.class);
        verify(auditService).protokolliereAenderung(
                eq(buchung), eq(mitarbeiter), eq(ErfassungsQuelle.MOBILE_APP), grund.capture());
        assertThat(grund.getValue()).contains("Nachgetragener Feierabend");
        assertThat(grund.getValue()).contains(AUTO_STOP.toString());
        // Version muss VOR dem Audit hochgezaehlt sein, sonst Unique-Constraint
        assertThat(buchung.getVersion()).isEqualTo(3);
    }

    // GoBD: Ein Nachtrag darf eine Buchung nur verkuerzen. Sonst koennte ein
    // fehlgeleiteter Offline-Eintrag Arbeitszeit hinzuerfinden.
    @Test
    void verlaengertBuchungNiemalsUeberDieAutomatischeEndezeitHinaus() {
        Mitarbeiter mitarbeiter = dummyMitarbeiter();
        Zeitbuchung buchung = automatischBeendeteBuchung(mitarbeiter);
        keineAktiveBuchung(mitarbeiter);
        when(zeitbuchungRepository
                .findFirstByMitarbeiterIdAndAutomatischBeendetTrueAndStartZeitBetweenOrderByStartZeitDesc(
                        eq(MITARBEITER_ID), any(), any()))
                .thenReturn(Optional.of(buchung));

        assertThatThrownBy(() -> service.stopZeiterfassung(
                TOKEN, LocalDateTime.of(2026, 7, 29, 21, 30), "stop-key-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Keine aktive Buchung gefunden");

        assertThat(buchung.getEndeZeit()).isEqualTo(AUTO_STOP);
        verify(zeitbuchungRepository, never()).save(any());
    }

    @Test
    void weistNachtragVorDerStartzeitAb() {
        Mitarbeiter mitarbeiter = dummyMitarbeiter();
        Zeitbuchung buchung = automatischBeendeteBuchung(mitarbeiter);
        keineAktiveBuchung(mitarbeiter);
        when(zeitbuchungRepository
                .findFirstByMitarbeiterIdAndAutomatischBeendetTrueAndStartZeitBetweenOrderByStartZeitDesc(
                        eq(MITARBEITER_ID), any(), any()))
                .thenReturn(Optional.of(buchung));

        assertThatThrownBy(() -> service.stopZeiterfassung(
                TOKEN, LocalDateTime.of(2026, 7, 29, 15, 0), "stop-key-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Keine aktive Buchung gefunden");

        assertThat(buchung.getEndeZeit()).isEqualTo(AUTO_STOP);
        verify(zeitbuchungRepository, never()).save(any());
    }

    // Ohne Original-Zeitstempel wissen wir es nicht besser als der Auto-Stop -
    // dann darf gar nichts angefasst werden.
    @Test
    void suchtOhneOriginalZeitstempelGarKeinenNachtragsKandidaten() {
        Mitarbeiter mitarbeiter = dummyMitarbeiter();
        keineAktiveBuchung(mitarbeiter);

        assertThatThrownBy(() -> service.stopZeiterfassung(TOKEN, null, "stop-key-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Keine aktive Buchung gefunden");

        verify(zeitbuchungRepository, never())
                .findFirstByMitarbeiterIdAndAutomatischBeendetTrueAndStartZeitBetweenOrderByStartZeitDesc(
                        any(), any(), any());
        verify(zeitbuchungRepository, never()).save(any());
    }

    @Test
    void meldetWeiterhinFehlerWennKeineAutomatischBeendeteBuchungExistiert() {
        Mitarbeiter mitarbeiter = dummyMitarbeiter();
        keineAktiveBuchung(mitarbeiter);
        when(zeitbuchungRepository
                .findFirstByMitarbeiterIdAndAutomatischBeendetTrueAndStartZeitBetweenOrderByStartZeitDesc(
                        eq(MITARBEITER_ID), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.stopZeiterfassung(TOKEN, ECHTER_FEIERABEND, "stop-key-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Keine aktive Buchung gefunden");

        verify(zeitbuchungRepository, never()).save(any());
    }
}
