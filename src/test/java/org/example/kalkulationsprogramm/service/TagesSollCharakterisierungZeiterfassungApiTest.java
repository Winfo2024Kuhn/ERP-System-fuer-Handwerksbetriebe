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
import static org.mockito.Mockito.when;

/**
 * Charakterisierungstest (Langzeitkrankmeldung, Abschnitt 1 / Task 2).
 *
 * Friert die private Methode {@code ZeiterfassungApiService.berechneFeiertagsStunden}
 * zahlengenau ein - Task 3 ersetzt sie durch
 * {@code TagesSollService.feiertagsGutschrift} (siehe E2 im Plan).
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
 * Stattdessen wird die private Methode direkt per {@link ReflectionTestUtils}
 * aufgerufen - das prueft exakt dieselbe Berechnung, bleibt aber unabhaengig
 * vom Tag, an dem der Test laeuft.
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

    private ZeiterfassungApiService service;

    private Zeitkonto zeitkonto;

    private static final LocalDate VOLLER_FEIERTAG = LocalDate.of(2026, 1, 1);
    private static final LocalDate HALBER_FEIERTAG = LocalDate.of(2026, 12, 24);
    private static final LocalDate FEIERTAG_AM_WOCHENENDE = LocalDate.of(2026, 12, 26);

    @BeforeEach
    void setUp() {
        // Vorbild: ZeiterfassungApiServiceConcurrencyTest:74-83 (expliziter
        // Konstruktor statt @InjectMocks).
        service = new ZeiterfassungApiService(
                projektRepository, mitarbeiterRepository, arbeitsgangRepository,
                zeitbuchungRepository, abwesenheitRepository, produktkategorieRepository,
                arbeitsgangStundensatzRepository, arbeitsgangMapper, dateiSpeicherService,
                lieferantenRepository, feiertagService, auditService);

        Mitarbeiter mitarbeiter = new Mitarbeiter();
        mitarbeiter.setId(1L);
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

    private BigDecimal berechneFeiertagsStunden(LocalDate von, LocalDate bis) {
        return ReflectionTestUtils.invokeMethod(service, "berechneFeiertagsStunden", zeitkonto, von, bis);
    }

    @Test
    void vollerFeiertag_ergibtAchtStunden() {
        when(feiertagService.istFeiertag(VOLLER_FEIERTAG)).thenReturn(true);
        when(feiertagService.istHalberFeiertag(VOLLER_FEIERTAG)).thenReturn(false);

        BigDecimal result = berechneFeiertagsStunden(VOLLER_FEIERTAG, VOLLER_FEIERTAG);

        assertEquals(0, new BigDecimal("8").compareTo(result));
    }

    @Test
    void halberFeiertag_ergibtVierStunden() {
        when(feiertagService.istFeiertag(HALBER_FEIERTAG)).thenReturn(true);
        when(feiertagService.istHalberFeiertag(HALBER_FEIERTAG)).thenReturn(true);

        BigDecimal result = berechneFeiertagsStunden(HALBER_FEIERTAG, HALBER_FEIERTAG);

        assertEquals(0, new BigDecimal("4.00").compareTo(result));
    }

    @Test
    void feiertagAmWochenende_ergibtNull() {
        // Kein Stubbing von feiertagService.istFeiertag(...) hier: die Bedingung
        // "tagesSoll > 0 && istFeiertag(tag)" wertet tagesSoll ZUERST aus und
        // schneidet per Kurzschluss ab - feiertagService wird an einem Tag mit
        // Sollstunden 0 (Samstag) gar nicht erst befragt.

        BigDecimal result = berechneFeiertagsStunden(FEIERTAG_AM_WOCHENENDE, FEIERTAG_AM_WOCHENENDE);

        assertEquals(0, BigDecimal.ZERO.compareTo(result));
    }
}
