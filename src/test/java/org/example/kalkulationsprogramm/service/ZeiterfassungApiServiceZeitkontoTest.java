package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.mapper.ArbeitsgangMapper;
import org.example.kalkulationsprogramm.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ZeiterfassungApiServiceZeitkontoTest {
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
    @Mock private TagesSollService tagesSollService;
    @Mock private ZeitkontoService zeitkontoService;
    @Mock private UrlaubsverfallService urlaubsverfallService;
    @Mock private ZeitkontoKorrekturService zeitkontoKorrekturService;
    @Mock private MonatsSaldoService monatsSaldoService;
    @InjectMocks private ZeiterfassungApiService service;

    private static final String TOKEN = "test-token-max-mustermann";
    private Mitarbeiter mitarbeiter;

    @BeforeEach
    void setUp() {
        mitarbeiter = new Mitarbeiter();
        mitarbeiter.setId(1L);
        mitarbeiter.setVorname("Max");
        mitarbeiter.setNachname("Mustermann");
        mitarbeiter.setEintrittsdatum(LocalDate.of(2020, 10, 1));
        mitarbeiter.setJahresUrlaub(28);
    }

    private void leseZugang() {
        when(mitarbeiterRepository.findByLoginTokenAndAktivTrue(TOKEN)).thenReturn(Optional.of(mitarbeiter));
    }

    private void sperreZugang() {
        when(mitarbeiterRepository.findByLoginTokenAndAktivTrueForUpdate(TOKEN)).thenReturn(Optional.of(mitarbeiter));
    }

    private MonatsSaldo monat(boolean geschlossen) {
        MonatsSaldo saldo = new MonatsSaldo();
        saldo.setSollStunden(new BigDecimal("160"));
        saldo.setIstStunden(new BigDecimal("168"));
        saldo.setFestgeschrieben(geschlossen);
        return saldo;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> teil(Map<String, Object> result, String key) {
        return (Map<String, Object>) result.get(key);
    }

    @Test
    void getBuchungszeitfensterOhneEinrichtungBleibtSchreibfrei() {
        leseZugang();
        assertThat(service.getBuchungszeitfenster(TOKEN))
                .containsEntry("fuehrtZeitkonto", true).containsEntry("eingerichtet", false)
                .containsEntry("buchungStartZeit", null).containsEntry("buchungEndeZeit", null)
                .containsEntry("hinweis", "Noch keine Arbeitszeit hinterlegt.");
        verify(zeitkontoService).versionAm(1L, LocalDate.now());
        verifyNoMoreInteractions(zeitkontoService);
        verifyNoInteractions(monatsSaldoService, tagesSollService);
    }

    @Test
    void ausgeschaltetLiefertKeinZeitfensterAuchMitVersion() {
        leseZugang();
        mitarbeiter.setFuehrtZeitkonto(false);
        ZeitkontoVersion version = new ZeitkontoVersion();
        version.setBuchungStartZeit(LocalTime.of(6, 0));
        version.setBuchungEndeZeit(LocalTime.of(20, 0));
        when(zeitkontoService.versionAm(1L, LocalDate.now())).thenReturn(Optional.of(version));
        assertThat(service.getBuchungszeitfenster(TOKEN)).containsEntry("buchungStartZeit", null)
                .containsEntry("buchungEndeZeit", null).containsEntry("fuehrtZeitkonto", false);
    }

    @Test
    void eingerichtetesZeitfensterKommtAusHeutigerVersion() {
        leseZugang();
        ZeitkontoVersion version = new ZeitkontoVersion();
        version.setBuchungStartZeit(LocalTime.of(6, 0));
        version.setBuchungEndeZeit(LocalTime.of(20, 0));
        when(zeitkontoService.versionAm(1L, LocalDate.now())).thenReturn(Optional.of(version));
        assertThat(service.getBuchungszeitfenster(TOKEN)).containsEntry("buchungStartZeit", "06:00")
                .containsEntry("buchungEndeZeit", "20:00").containsEntry("eingerichtet", true)
                .containsEntry("hinweis", null);
    }

    @Test
    void geschaeftsfuehrungBekommtWeederSollNochGesamtsaldo() {
        leseZugang();
        mitarbeiter.setIstGeschaeftsfuehrer(true);
        when(monatsSaldoService.getOrBerechne(eq(1L), eq(2020), anyInt())).thenReturn(monat(false));

        Map<String, Object> result = service.getSaldo(TOKEN, 2020, 10, false);

        // Die Geschaeftsfuehrung erfasst Projektzeiten ohne Arbeitszeitkonto:
        // Soll, Differenz, Monatsabschluss und Gesamtsaldo entfallen komplett.
        assertThat(teil(result, "monat")).containsEntry("istStunden", new BigDecimal("168"))
                .doesNotContainKeys("sollStunden", "differenz", "festgeschrieben");
        assertThat(teil(result, "urlaub")).containsKeys("genommen", "geplant")
                .doesNotContainKeys("jahresanspruch", "verbleibend", "korrektur");
        assertThat(result).doesNotContainKey("gesamt");
    }

    @Test
    void ohneGeschaeftsfuehrungBleibenSollUndGesamtsaldoErhalten() {
        leseZugang();
        when(monatsSaldoService.getOrBerechne(eq(1L), eq(2020), anyInt())).thenReturn(monat(true));

        Map<String, Object> result = service.getSaldo(TOKEN, 2020, 10, false);

        assertThat(teil(result, "monat")).containsKeys("sollStunden", "differenz", "festgeschrieben");
        assertThat(teil(result, "urlaub")).containsKeys("jahresanspruch", "verbleibend", "korrektur");
        assertThat(result).containsKey("gesamt");
    }

    @Test
    void buchungszeitfensterMeldetGeschaeftsfuehrungAlsEingerichtet() {
        leseZugang();
        mitarbeiter.setIstGeschaeftsfuehrer(true);

        assertThat(service.getBuchungszeitfenster(TOKEN))
                .containsEntry("fuehrtZeitkonto", true)
                .containsEntry("istGeschaeftsfuehrer", true)
                .containsEntry("kontenGefuehrt", false)
                .containsEntry("eingerichtet", true)
                .containsEntry("hinweis", "Als Geschäftsführung erfassen Sie Projektzeiten ohne Arbeitszeitkonto.");
    }

    @Test
    void festerRandmonatWirdNichtAnteiligNeuBerechnet() {
        leseZugang();
        mitarbeiter.setEintrittsdatum(LocalDate.of(2020, 10, 15));
        when(monatsSaldoService.getOrBerechne(eq(1L), eq(2020), anyInt())).thenReturn(monat(true));
        Map<String, Object> result = service.getSaldo(TOKEN, 2020, 10, false);
        assertThat(teil(result, "monat")).containsEntry("festgeschrieben", true);
        assertThat(teil(result, "gesamt")).containsEntry("istStunden", new BigDecimal("504"))
                .containsEntry("sollStunden", new BigDecimal("480"))
                .containsEntry("geprueftBis", "2020-12-31").containsEntry("vorlaeufig", false);
        verifyNoInteractions(tagesSollService);
        verify(zeitbuchungRepository, never()).findByMitarbeiterIdAndStartZeitBetween(any(), any(), any());
    }

    @Test
    void ersterOffenerMonatVerhindertGeprueftBisTrotzSpaetererAbschluesse() {
        leseZugang();
        when(monatsSaldoService.getOrBerechne(1L, 2020, 10)).thenReturn(monat(false));
        when(monatsSaldoService.getOrBerechne(1L, 2020, 11)).thenReturn(monat(true));
        when(monatsSaldoService.getOrBerechne(1L, 2020, 12)).thenReturn(monat(true));
        Map<String, Object> result = service.getSaldo(TOKEN, 2020, 10, false);
        assertThat(teil(result, "gesamt")).containsEntry("geprueftBis", null)
                .containsEntry("vorlaeufig", true).containsEntry("saldo", new BigDecimal("24"));
        assertThat(teil(result, "monat")).containsEntry("festgeschrieben", false);
        assertThat(teil(result, "urlaub")).containsEntry("verbleibend", 28);
    }

    @Test
    void abschlusslueckeBeendetGeprueftBisVorSpaeteremAbschluss() {
        leseZugang();
        when(monatsSaldoService.getOrBerechne(1L, 2020, 10)).thenReturn(monat(true));
        when(monatsSaldoService.getOrBerechne(1L, 2020, 11)).thenReturn(monat(false));
        when(monatsSaldoService.getOrBerechne(1L, 2020, 12)).thenReturn(monat(true));
        assertThat(teil(service.getSaldo(TOKEN, 2020, 10, false), "gesamt"))
                .containsEntry("geprueftBis", "2020-10-31").containsEntry("vorlaeufig", true);
    }

    @Test
    void historischerOffenerRandmonatUndUrlaubBleibenBeiAusgeschaltetemKontoSichtbar() {
        leseZugang();
        mitarbeiter.setFuehrtZeitkonto(false);
        LocalDate von = LocalDate.of(2020, 12, 15);
        LocalDate bis = LocalDate.of(2020, 12, 31);
        mitarbeiter.setEintrittsdatum(von);
        when(monatsSaldoService.getOrBerechne(1L, 2020, 12)).thenReturn(monat(false));
        when(tagesSollService.periodenSollSumme(1L, von, bis)).thenReturn(new BigDecimal("80"));
        when(tagesSollService.feiertagsGutschriftSumme(1L, von, bis)).thenReturn(new BigDecimal("8"));
        when(zeitkontoKorrekturService.summiereAktiveKorrekturenImZeitraum(1L, von, bis)).thenReturn(BigDecimal.ZERO);
        Map<String, Object> result = service.getSaldo(TOKEN, 2020, 12, false);
        assertThat(result).containsEntry("fuehrtZeitkonto", false).containsEntry("eingerichtet", false);
        assertThat(teil(result, "gesamt")).containsEntry("sollStunden", new BigDecimal("80"))
                .containsEntry("istStunden", new BigDecimal("8")).containsEntry("vorlaeufig", true);
        assertThat(teil(result, "urlaub")).containsEntry("verbleibend", 28);
        verify(zeitkontoService).versionAm(1L, LocalDate.now());
        verifyNoMoreInteractions(zeitkontoService);
    }

    @Test
    void ausgeschaltetVerhindertStartAberErlaubtStopDerBestehendenBuchung() {
        sperreZugang();
        mitarbeiter.setFuehrtZeitkonto(false);
        assertThatThrownBy(() -> service.startZeiterfassung(TOKEN, 2L, 3L, null, null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("ausgeschaltet");
        Zeitbuchung buchung = buchung(mitarbeiter);
        when(zeitbuchungRepository.findFirstByMitarbeiterIdAndEndeZeitIsNullOrderByStartZeitDesc(1L))
                .thenReturn(Optional.of(buchung));
        when(zeitbuchungRepository.save(buchung)).thenReturn(buchung);
        service.stopZeiterfassung(TOKEN, null, null);
        assertThat(buchung.getEndeZeit()).isNotNull();
        verifyNoInteractions(zeitkontoService);
    }

    @Test
    void offlineStartOhneVersionAmBuchungstagWirdAbgelehnt() {
        sperreZugang();
        when(zeitkontoService.versionAm(1L, LocalDate.now())).thenReturn(Optional.of(new ZeitkontoVersion()));
        LocalDateTime offline = LocalDate.now().minusDays(10).atTime(8, 0);
        assertThatThrownBy(() -> service.startZeiterfassung(TOKEN, 2L, 3L, null, offline, "offline-test"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("Buchungstag");
        verify(zeitkontoService).versionAm(1L, offline.toLocalDate());
        verify(zeitbuchungRepository, never()).save(any());
    }

    @Test
    void offlineStartOhneHeutigeVersionWirdAbgelehnt() {
        sperreZugang();
        assertThatThrownBy(() -> service.startZeiterfassung(TOKEN, 2L, 3L, null,
                LocalDate.now().minusDays(10).atTime(8, 0), null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("heute");
        verify(zeitbuchungRepository, never()).save(any());
    }

    @Test
    void pauseOhneKontoVeraendertKeineLaufendenBuchungen() {
        sperreZugang();
        mitarbeiter.setFuehrtZeitkonto(false);
        assertThatThrownBy(() -> service.startPause(TOKEN, null, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("ausgeschaltet");
        verifyNoInteractions(zeitbuchungRepository, auditService, monatsSaldoService);
    }

    @Test
    void eigenerStartRetryBleibtNachAusschaltenErlaubt() {
        leseZugang();
        mitarbeiter.setFuehrtZeitkonto(false);
        when(zeitbuchungRepository.findByIdempotencyKey("retry")).thenReturn(Optional.of(buchung(mitarbeiter)));
        assertThat(service.startZeiterfassung(TOKEN, 2L, 3L, null, null, "retry"))
                .containsEntry("id", 5L).containsEntry("idempotent", true);
        verifyNoInteractions(zeitkontoService);
        verify(zeitbuchungRepository, never()).save(any());
    }

    @Test
    void fremdeIdempotenzKeysGebenKeineBuchungPreis() {
        leseZugang();
        Mitarbeiter anderer = new Mitarbeiter();
        anderer.setId(2L);
        Zeitbuchung fremd = buchung(anderer);
        when(zeitbuchungRepository.findByIdempotencyKey("fremd")).thenReturn(Optional.of(fremd));
        when(zeitbuchungRepository.findByStopIdempotencyKey("fremd")).thenReturn(Optional.of(fremd));
        assertThatThrownBy(() -> service.startZeiterfassung(TOKEN, 2L, 3L, null, null, "fremd"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("gehört nicht");
        assertThatThrownBy(() -> service.startPause(TOKEN, null, "fremd"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("gehört nicht");
        assertThatThrownBy(() -> service.stopZeiterfassung(TOKEN, null, "fremd"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("gehört nicht");
        verify(zeitbuchungRepository, never()).save(any());
    }

    @Test
    void zweiterIdempotenzCheckNachLockPrueftEbenfallsDenEigentuemer() {
        leseZugang();
        sperreZugang();
        Mitarbeiter anderer = new Mitarbeiter();
        anderer.setId(2L);
        when(zeitbuchungRepository.findByIdempotencyKey("race"))
                .thenReturn(Optional.empty(), Optional.of(buchung(anderer)));
        assertThatThrownBy(() -> service.startZeiterfassung(TOKEN, 2L, 3L, null, null, "race"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("gehört nicht");
        verify(mitarbeiterRepository).findByLoginTokenAndAktivTrueForUpdate(TOKEN);
        verify(zeitbuchungRepository, never()).save(any());
    }

    @Test
    void retryMitUngueltigemTokenWirdNichtAusgeliefert() {
        when(zeitbuchungRepository.findByIdempotencyKey("retry"))
                .thenReturn(Optional.of(buchung(mitarbeiter)));
        assertThatThrownBy(() -> service.startZeiterfassung("ungueltig", 2L, 3L, null, null, "retry"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verifyNoInteractions(zeitkontoService);
    }

    @Test
    void offlineStartMitBeidenVersionenSpeichertDenOriginalzeitpunkt() {
        sperreZugang();
        LocalDateTime offline = LocalDate.now().minusDays(10).atTime(8, 0);
        when(zeitkontoService.versionAm(1L, LocalDate.now())).thenReturn(Optional.of(new ZeitkontoVersion()));
        when(zeitkontoService.versionAm(1L, offline.toLocalDate())).thenReturn(Optional.of(new ZeitkontoVersion()));
        Projekt projekt = new Projekt();
        projekt.setId(2L);
        Arbeitsgang arbeitsgang = new Arbeitsgang();
        arbeitsgang.setId(3L);
        when(projektRepository.findById(2L)).thenReturn(Optional.of(projekt));
        when(arbeitsgangRepository.findById(3L)).thenReturn(Optional.of(arbeitsgang));
        when(arbeitsgangStundensatzRepository.findTopByArbeitsgangIdAndJahrOrderByIdDesc(eq(3L), anyInt()))
                .thenReturn(Optional.of(new ArbeitsgangStundensatz()));
        when(zeitbuchungRepository.save(any())).thenAnswer(invocation -> {
            Zeitbuchung gespeichert = invocation.getArgument(0);
            gespeichert.setId(5L);
            assertThat(gespeichert.getStartZeit()).isEqualTo(offline);
            return gespeichert;
        });
        assertThat(service.startZeiterfassung(TOKEN, 2L, 3L, null, offline, "offline"))
                .containsEntry("startZeit", offline.toString()).containsEntry("status", "gestartet");
        verify(zeitkontoService).versionAm(1L, LocalDate.now());
        verify(zeitkontoService).versionAm(1L, offline.toLocalDate());
    }

    private Zeitbuchung buchung(Mitarbeiter eigentuemer) {
        Zeitbuchung buchung = new Zeitbuchung();
        buchung.setId(5L);
        buchung.setMitarbeiter(eigentuemer);
        buchung.setStartZeit(LocalDateTime.now().minusHours(1));
        buchung.setVersion(1);
        return buchung;
    }
}
