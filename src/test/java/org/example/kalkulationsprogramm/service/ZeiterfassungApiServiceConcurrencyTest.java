package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Race-Condition-Tests fuer ZeiterfassungApiService.
 *
 * Dummy-Daten gemaess DSGVO: "Max Mustermann".
 *
 * Fokus: Verifikation dass der Service den pessimistic locked Finder
 * (findByLoginTokenAndAktivTrueForUpdate) verwendet, nicht den nicht
 * gesperrten Finder. Das ist die Anwendungs-Layer-Garantie gegen
 * konkurrierende Start/Stop-Operationen pro Mitarbeiter. Die DB-Garantie
 * kommt zusaetzlich ueber den Unique-Index in V321.
 */
@ExtendWith(MockitoExtension.class)
class ZeiterfassungApiServiceConcurrencyTest {

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
    private static final Long PROJEKT_ID = 100L;
    private static final Long ARBEITSGANG_ID = 200L;

    @BeforeEach
    void setUp() {
        service = new ZeiterfassungApiService(
                projektRepository, mitarbeiterRepository, arbeitsgangRepository,
                zeitbuchungRepository, abwesenheitRepository, produktkategorieRepository,
                arbeitsgangStundensatzRepository, arbeitsgangMapper, dateiSpeicherService,
                lieferantenRepository, feiertagService, auditService);
        // @Autowired-Felder ueber Reflection setzen (Mix aus Constructor- und
        // Field-Injection im Service - hier Field-Injection nachstellen).
        ReflectionTestUtils.setField(service, "monatsSaldoService", monatsSaldoService);
    }

    private Mitarbeiter dummyMitarbeiter() {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(MITARBEITER_ID);
        m.setVorname("Max");
        m.setNachname("Mustermann");
        m.setAktiv(true);
        m.setLoginToken(TOKEN);
        return m;
    }

    private Projekt dummyProjekt() {
        Projekt p = new Projekt();
        p.setId(PROJEKT_ID);
        p.setBauvorhaben("Dummy-Bauvorhaben");
        return p;
    }

    private Arbeitsgang dummyArbeitsgang() {
        Arbeitsgang a = new Arbeitsgang();
        a.setId(ARBEITSGANG_ID);
        a.setBeschreibung("Dummy-Taetigkeit");
        return a;
    }

    private ArbeitsgangStundensatz dummyStundensatz() {
        ArbeitsgangStundensatz s = new ArbeitsgangStundensatz();
        s.setId(300L);
        return s;
    }

    @Test
    void startZeiterfassung_verwendetPessimisticLockedFinder() {
        when(mitarbeiterRepository.findByLoginTokenAndAktivTrueForUpdate(TOKEN))
                .thenReturn(Optional.of(dummyMitarbeiter()));
        when(zeitbuchungRepository.findByMitarbeiterIdAndEndeZeitIsNull(MITARBEITER_ID))
                .thenReturn(List.of());
        when(projektRepository.findById(PROJEKT_ID)).thenReturn(Optional.of(dummyProjekt()));
        when(arbeitsgangRepository.findById(ARBEITSGANG_ID)).thenReturn(Optional.of(dummyArbeitsgang()));
        when(arbeitsgangStundensatzRepository.findTopByArbeitsgangIdAndJahrOrderByIdDesc(eq(ARBEITSGANG_ID), anyInt()))
                .thenReturn(Optional.of(dummyStundensatz()));
        when(zeitbuchungRepository.save(any(Zeitbuchung.class))).thenAnswer(inv -> {
            Zeitbuchung b = inv.getArgument(0);
            b.setId(999L);
            return b;
        });

        service.startZeiterfassung(TOKEN, PROJEKT_ID, ARBEITSGANG_ID, null, null, null);

        // KERN-Assertion: Der gesperrte Finder muss aufgerufen worden sein,
        // NICHT der nicht-gesperrte. Sonst koennen zwei parallele Requests
        // beide "keine aktive Buchung" sehen und beide eine Buchung anlegen.
        verify(mitarbeiterRepository, times(1)).findByLoginTokenAndAktivTrueForUpdate(TOKEN);
        verify(mitarbeiterRepository, never()).findByLoginTokenAndAktivTrue(TOKEN);
    }

    @Test
    void startZeiterfassung_wirftFehlerWennBereitsAktiveBuchungExistiert() {
        Zeitbuchung aktiveBuchung = new Zeitbuchung();
        aktiveBuchung.setId(123L);
        aktiveBuchung.setMitarbeiter(dummyMitarbeiter());
        aktiveBuchung.setStartZeit(LocalDateTime.now().minusHours(1));

        when(mitarbeiterRepository.findByLoginTokenAndAktivTrueForUpdate(TOKEN))
                .thenReturn(Optional.of(dummyMitarbeiter()));
        when(zeitbuchungRepository.findByMitarbeiterIdAndEndeZeitIsNull(MITARBEITER_ID))
                .thenReturn(List.of(aktiveBuchung));

        assertThatThrownBy(() -> service.startZeiterfassung(TOKEN, PROJEKT_ID, ARBEITSGANG_ID, null, null, null))
                .hasMessageContaining("bereits eine Buchung");

        verify(zeitbuchungRepository, never()).save(any(Zeitbuchung.class));
    }

    @Test
    void startZeiterfassung_idempotencyKey_gibtBestehendeBuchungZurueckOhneLock() {
        String idempotencyKey = "uuid-max-mustermann-retry";
        Zeitbuchung bestehend = new Zeitbuchung();
        bestehend.setId(555L);
        bestehend.setMitarbeiter(dummyMitarbeiter());
        bestehend.setProjekt(dummyProjekt());
        bestehend.setArbeitsgang(dummyArbeitsgang());
        bestehend.setStartZeit(LocalDateTime.now().minusMinutes(30));
        bestehend.setIdempotencyKey(idempotencyKey);

        when(zeitbuchungRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(bestehend));

        Map<String, Object> result = service.startZeiterfassung(
                TOKEN, PROJEKT_ID, ARBEITSGANG_ID, null, null, idempotencyKey);

        assertThat(result.get("id")).isEqualTo(555L);
        assertThat(result.get("idempotent")).isEqualTo(true);
        assertThat(result.get("status")).isEqualTo("already_exists");

        // Bei Idempotency-Treffer darf KEIN Lock gehalten werden - sonst wuerde
        // ein retry storm unnoetig die DB-Connections blockieren.
        verify(mitarbeiterRepository, never()).findByLoginTokenAndAktivTrueForUpdate(TOKEN);
        verify(zeitbuchungRepository, never()).save(any(Zeitbuchung.class));
    }

    @Test
    void stopZeiterfassung_verwendetPessimisticLockedFinder() {
        Zeitbuchung aktiv = new Zeitbuchung();
        aktiv.setId(777L);
        aktiv.setMitarbeiter(dummyMitarbeiter());
        aktiv.setProjekt(dummyProjekt());
        aktiv.setArbeitsgang(dummyArbeitsgang());
        aktiv.setStartZeit(LocalDateTime.now().minusHours(2));
        aktiv.setVersion(1);

        when(mitarbeiterRepository.findByLoginTokenAndAktivTrueForUpdate(TOKEN))
                .thenReturn(Optional.of(dummyMitarbeiter()));
        when(zeitbuchungRepository.findFirstByMitarbeiterIdAndEndeZeitIsNullOrderByStartZeitDesc(MITARBEITER_ID))
                .thenReturn(Optional.of(aktiv));
        when(zeitbuchungRepository.save(any(Zeitbuchung.class))).thenAnswer(inv -> inv.getArgument(0));

        service.stopZeiterfassung(TOKEN, null, null);

        verify(mitarbeiterRepository, times(1)).findByLoginTokenAndAktivTrueForUpdate(TOKEN);
        verify(mitarbeiterRepository, never()).findByLoginTokenAndAktivTrue(TOKEN);
    }

    @Test
    void stopZeiterfassung_stopIdempotencyKey_gibtBestehendeBuchungZurueckOhneLock() {
        String stopKey = "uuid-stop-retry";
        Zeitbuchung bereitsGestoppt = new Zeitbuchung();
        bereitsGestoppt.setId(888L);
        bereitsGestoppt.setMitarbeiter(dummyMitarbeiter());
        bereitsGestoppt.setProjekt(dummyProjekt());
        bereitsGestoppt.setArbeitsgang(dummyArbeitsgang());
        bereitsGestoppt.setStartZeit(LocalDateTime.now().minusHours(3));
        bereitsGestoppt.setEndeZeit(LocalDateTime.now().minusHours(1));
        bereitsGestoppt.setStopIdempotencyKey(stopKey);

        when(zeitbuchungRepository.findByStopIdempotencyKey(stopKey))
                .thenReturn(Optional.of(bereitsGestoppt));

        Map<String, Object> result = service.stopZeiterfassung(TOKEN, null, stopKey);

        assertThat(result.get("id")).isEqualTo(888L);
        assertThat(result.get("idempotent")).isEqualTo(true);
        assertThat(result.get("status")).isEqualTo("already_stopped");

        verify(mitarbeiterRepository, never()).findByLoginTokenAndAktivTrueForUpdate(TOKEN);
        verify(zeitbuchungRepository, never()).save(any(Zeitbuchung.class));
    }

    @Test
    void startPause_verwendetPessimisticLockedFinder() {
        when(mitarbeiterRepository.findByLoginTokenAndAktivTrueForUpdate(TOKEN))
                .thenReturn(Optional.of(dummyMitarbeiter()));
        when(zeitbuchungRepository.findByMitarbeiterIdAndEndeZeitIsNull(MITARBEITER_ID))
                .thenReturn(List.of());
        when(zeitbuchungRepository.save(any(Zeitbuchung.class))).thenAnswer(inv -> {
            Zeitbuchung b = inv.getArgument(0);
            b.setId(111L);
            return b;
        });

        service.startPause(TOKEN, null, null);

        verify(mitarbeiterRepository, times(1)).findByLoginTokenAndAktivTrueForUpdate(TOKEN);
        verify(mitarbeiterRepository, never()).findByLoginTokenAndAktivTrue(TOKEN);
    }

    /**
     * "Handy gewinnt": Wenn der Mitarbeiter mehr als eine offene Buchung hat
     * (z.B. Altdaten vor V321, manueller Stop am PC verpasst, Race zwischen
     * pending Sync-Wellen), muss startPause ALLE offenen Buchungen schliessen
     * - sonst krachte das spaetere save(pauseBuchung) am V321-Unique-Index
     * und das Handy bekaeme 4xx zurueck, obwohl der Mitarbeiter klar Pause
     * anstechen wollte.
     */
    @Test
    void startPause_schliesstAlleOffenenBuchungen_damitPauseAnlegbarBleibt() {
        Mitarbeiter mitarbeiter = dummyMitarbeiter();
        when(mitarbeiterRepository.findByLoginTokenAndAktivTrueForUpdate(TOKEN))
                .thenReturn(Optional.of(mitarbeiter));

        Zeitbuchung offen1 = new Zeitbuchung();
        offen1.setId(501L);
        offen1.setMitarbeiter(mitarbeiter);
        offen1.setStartZeit(LocalDateTime.now().minusHours(2));
        offen1.setVersion(1);

        Zeitbuchung offen2 = new Zeitbuchung();
        offen2.setId(502L);
        offen2.setMitarbeiter(mitarbeiter);
        offen2.setStartZeit(LocalDateTime.now().minusMinutes(30));
        offen2.setVersion(1);

        when(zeitbuchungRepository.findByMitarbeiterIdAndEndeZeitIsNull(MITARBEITER_ID))
                .thenReturn(List.of(offen1, offen2));
        when(zeitbuchungRepository.save(any(Zeitbuchung.class))).thenAnswer(inv -> {
            Zeitbuchung b = inv.getArgument(0);
            if (b.getId() == null) b.setId(999L);
            return b;
        });

        Map<String, Object> result = service.startPause(TOKEN, null, null);

        assertThat(offen1.getEndeZeit()).as("erste offene Buchung gestoppt").isNotNull();
        assertThat(offen2.getEndeZeit()).as("zweite offene Buchung gestoppt").isNotNull();
        assertThat(result.get("typ")).isEqualTo("PAUSE");
        assertThat(result.get("status")).isEqualTo("gestartet");

        // markiereAlsGeaendert() hat Version++ ausgefuehrt - genau das verhindert
        // den Unique-Constraint-Konflikt im zeitbuchung_audit (Version 1 existiert
        // bereits vom Start). Wer den Aufruf aus der Schleife entfernt, faellt
        // hier rein.
        assertThat(offen1.getVersion()).as("Version von Buchung 1 erhoeht").isEqualTo(2);
        assertThat(offen2.getVersion()).as("Version von Buchung 2 erhoeht").isEqualTo(2);

        // Audit-Trail GoBD-konform fuer jede gestoppte Buchung + die neue Pause
        verify(auditService, times(2)).protokolliereAenderung(
                any(Zeitbuchung.class), eq(mitarbeiter), any(), any(String.class));
        verify(auditService, times(1)).protokolliereErstellung(
                any(Zeitbuchung.class), eq(mitarbeiter), any());

        // MonatsSaldo-Cache muss fuer jede betroffene Buchung invalidiert werden
        // (2x gestoppt + 1x neue Pause = 3 Aufrufe). Sonst zeigt der PC-Saldo
        // bis zum naechsten Trigger veraltete Werte.
        verify(monatsSaldoService, times(3)).invalidiereFuerDateTime(eq(MITARBEITER_ID), any(LocalDateTime.class));
    }

    // TODO (Folge-PR): Integrationstest mit echter H2 + V321-Migration, der
    //   zwei offene Buchungen anlegt und sicherstellt, dass startPause vorm
    //   Fix mit DataIntegrityViolation gescheitert waere. Aktueller Mock-Test
    //   belegt nur die Service-Logik, nicht den DB-Constraint-Pfad.
}
