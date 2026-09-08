package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Zeitkonto;
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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Charakterisierungstest (Langzeitkrankmeldung, Abschnitt 1 / Task 2).
 *
 * <p>Testsubjekt ist {@code ZeiterfassungApiService} - nicht {@code TagesSollService},
 * die hat ihre eigene Testklasse ({@code TagesSollServiceTest}, Task 3). Dieser Test
 * sichert etwas anderes ab: dass der Aufrufer nach der Umstellung in Task 10 noch
 * richtig rechnet. {@code TagesSollService} wird deshalb gemockt und liefert die
 * bekannten Werte zurueck; geprueft wird (a) dass diese Werte unveraendert im
 * Ergebnis von {@code ZeiterfassungApiService} ankommen und (b) dass
 * {@code feiertagsGutschriftSumme} mit dem richtigen Zeitraum aufgerufen wird -
 * genau dieser Parameter ist bei einer Umstellung die typische Fehlerquelle.
 *
 * <p><b>Abweichung vom Plan-Text (im Kontext-Log unter "Bedenken" vermerkt):</b>
 * der Plan nennt als Zugriffsweg "getGesamtSaldo(token) mit einem Randmonat".
 * Diese Methode existiert nicht - die tatsaechliche oeffentliche Methode heisst
 * {@code getSaldo(String, Integer, Integer, Boolean)}. Deren "Randmonat"-Zweig
 * (istErsterMonat || istLetzterMonat) haengt aber zusaetzlich unkontrollierbar
 * am echten Kalenderdatum ({@code LocalDate.now()}): je nachdem, ob das
 * angefragte Jahr mit dem "tatsaechlichen" Jahr uebereinstimmt, wird das
 * Enddatum entweder "heute" oder der 31.12. des angefragten Jahres - beides
 * lässt sich nicht so setzen, dass exakt unsere Fixture-Feiertage (01.01. und
 * 24./26.12.2026) in einem Randmonat-Zeitraum landen, ohne die Zusicherung an
 * das reale Testdatum zu koppeln (siehe Rechnung im Report an den Auftraggeber).
 * Stattdessen wird die private Methode {@code berechneAnteiligenMonatIst} - die
 * einzige Stelle, die die Feiertagsberechnung aufruft - direkt per
 * {@link ReflectionTestUtils} angesprochen. Das prueft exakt denselben Pfad,
 * bleibt aber unabhaengig vom Tag, an dem der Test laeuft.
 *
 * <p><b>Task 10, Abschnitt 3:</b> Die frueher hier gepruefte private Methode
 * {@code berechneFeiertagsStunden} ist ersatzlos geloescht;
 * {@code berechneAnteiligenMonatIst} ruft seither
 * {@code tagesSollService.feiertagsGutschriftSumme(...)} auf. Erste Fassung
 * dieses Umbaus hatte faelschlich {@code TagesSollService} selbst zum
 * Testsubjekt gemacht (dupliziert {@code TagesSollServiceTest} und haette
 * einen falsch verkabelten Aufrufer nicht mehr bemerkt) - korrigiert auf
 * Rueckfrage des Koordinators: Testsubjekt bleibt {@code ZeiterfassungApiService},
 * {@code TagesSollService} ist jetzt Mock. Erwartete Zahlen unveraendert:
 * voller Feiertag 8, halber Feiertag 4.00, Feiertag am Wochenende 0.
 *
 * Dummy-Daten (DSGVO): Max Mustermann, ID 1.
 */
@ExtendWith(MockitoExtension.class)
class TagesSollCharakterisierungZeiterfassungApiTest {

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
    @Mock private ZeitkontoKorrekturService zeitkontoKorrekturService;

    private ZeiterfassungApiService service;

    private Zeitkonto zeitkonto;

    private static final Long MITARBEITER_ID = 1L;
    private static final LocalDate VOLLER_FEIERTAG = LocalDate.of(2026, 1, 1);
    private static final LocalDate HALBER_FEIERTAG = LocalDate.of(2026, 12, 24);
    private static final LocalDate FEIERTAG_AM_WOCHENENDE = LocalDate.of(2026, 12, 26);

    @BeforeEach
    void setUp() {
        // Vorbild: ZeiterfassungApiServiceConcurrencyTest:76-84 (expliziter
        // Konstruktor statt @InjectMocks, Field-Injection-Felder per
        // ReflectionTestUtils.setField nachgezogen - die Klasse mischt beide
        // Injection-Arten).
        service = new ZeiterfassungApiService(
                projektRepository, mitarbeiterRepository, arbeitsgangRepository,
                zeitbuchungRepository, abwesenheitRepository, produktkategorieRepository,
                arbeitsgangStundensatzRepository, arbeitsgangMapper, dateiSpeicherService,
                lieferantenRepository, feiertagService, auditService, tagesSollService);
        ReflectionTestUtils.setField(service, "zeitkontoService", zeitkontoService);
        ReflectionTestUtils.setField(service, "zeitkontoKorrekturService", zeitkontoKorrekturService);

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

        when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID)).thenReturn(zeitkonto);
        // Keine Zeitbuchungen/Abwesenheiten/Korrekturen im Fixture-Zeitraum - isoliert
        // die Zusicherung auf die Feiertagsberechnung. any() fuer den Zeitraum ist hier
        // bewusst: diese Nebenabhaengigkeit ist nicht Testgegenstand. Der Zeitraum, der
        // tatsaechlich geprueft wird, ist der an tagesSollService.feiertagsGutschriftSumme -
        // dort exakt gestubbt und per verify() bestaetigt (siehe die drei Testfaelle).
        when(zeitkontoKorrekturService.summiereAktiveKorrekturenImZeitraum(eq(MITARBEITER_ID), any(), any()))
                .thenReturn(BigDecimal.ZERO);
    }

    private BigDecimal berechneAnteiligenMonatIst(LocalDate von, LocalDate bis) {
        return ReflectionTestUtils.invokeMethod(service, "berechneAnteiligenMonatIst", MITARBEITER_ID, von, bis);
    }

    @Test
    void vollerFeiertag_ergibtAchtStunden() {
        when(tagesSollService.feiertagsGutschriftSumme(MITARBEITER_ID, zeitkonto, VOLLER_FEIERTAG, VOLLER_FEIERTAG))
                .thenReturn(new BigDecimal("8"));

        BigDecimal result = berechneAnteiligenMonatIst(VOLLER_FEIERTAG, VOLLER_FEIERTAG);

        assertEquals(0, new BigDecimal("8").compareTo(result));
        verify(tagesSollService).feiertagsGutschriftSumme(MITARBEITER_ID, zeitkonto, VOLLER_FEIERTAG, VOLLER_FEIERTAG);
    }

    @Test
    void halberFeiertag_ergibtVierStunden() {
        when(tagesSollService.feiertagsGutschriftSumme(MITARBEITER_ID, zeitkonto, HALBER_FEIERTAG, HALBER_FEIERTAG))
                .thenReturn(new BigDecimal("4.00"));

        BigDecimal result = berechneAnteiligenMonatIst(HALBER_FEIERTAG, HALBER_FEIERTAG);

        assertEquals(0, new BigDecimal("4.00").compareTo(result));
        verify(tagesSollService).feiertagsGutschriftSumme(MITARBEITER_ID, zeitkonto, HALBER_FEIERTAG, HALBER_FEIERTAG);
    }

    @Test
    void feiertagAmWochenende_ergibtNull() {
        when(tagesSollService.feiertagsGutschriftSumme(
                MITARBEITER_ID, zeitkonto, FEIERTAG_AM_WOCHENENDE, FEIERTAG_AM_WOCHENENDE))
                .thenReturn(BigDecimal.ZERO);

        BigDecimal result = berechneAnteiligenMonatIst(FEIERTAG_AM_WOCHENENDE, FEIERTAG_AM_WOCHENENDE);

        assertEquals(0, BigDecimal.ZERO.compareTo(result));
        verify(tagesSollService).feiertagsGutschriftSumme(
                MITARBEITER_ID, zeitkonto, FEIERTAG_AM_WOCHENENDE, FEIERTAG_AM_WOCHENENDE);
    }
}
