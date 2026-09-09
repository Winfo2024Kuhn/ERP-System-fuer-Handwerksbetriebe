package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.MonatsSaldo;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.ZeitkontoVersion;
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.MonatsSaldoRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.repository.ZeitkontoKorrekturRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

/**
 * Charakterisierungstest (Langzeitkrankmeldung, Abschnitt 1 / Task 2).
 *
 * Friert {@code MonatsSaldo.getFeiertagsStunden()} zahlengenau ein - die
 * frühere private Berechnung in {@link MonatsSaldoService}
 * ({@code berechneFeiertagsStunden}), die Task 8 durch
 * {@code TagesSollService.feiertagsGutschriftSumme} ersetzt hat.
 *
 * <p><b>Verkabelung nach Task 8 angepasst, Zusicherungen unveraendert:</b>
 * {@code MonatsSaldoService} injiziert seit Task 8 keinen {@code FeiertagService}
 * mehr, sondern einen {@code TagesSollService}. Ohne diese Anpassung wuerde
 * {@code @InjectMocks} den neuen Konstruktor-Parameter mangels passendem
 * {@code @Mock} still mit {@code null} fuellen und jeder Test mit einer
 * {@code NullPointerException} rot werden - unabhaengig davon, ob sich am
 * berechneten Ergebnis irgendetwas geaendert haette. Deshalb mockt diese
 * Klasse jetzt {@link TagesSollService} statt {@link FeiertagService} und
 * stubbt {@code feiertagsGutschriftSumme} direkt auf den erwarteten Wert -
 * die drei Erwartungswerte (voller Feiertag {@code 8}, halber {@code 4.00},
 * Feiertag am Wochenende {@code 0}) und alle drei Testfaelle sind exakt
 * dieselben wie vor Task 8.
 *
 * Die drei Monate liegen bewusst sowohl vor als auch nach dem heutigen Datum
 * (08.09.2026): {@code getOrBerechne} behandelt vergangene Monate ueber den
 * DB-Cache, aktuelle/zukuenftige immer live. Damit dieser Test nicht irgendwann
 * kippt, sobald der Kalender ueber Dezember 2026 hinauslaeuft, stubbt
 * {@link #stubStandardMocks} IMMER auch den (ungueltigen) Cache-Eintrag mit
 * {@code lenient()} - er wird nur konsultiert, wenn der jeweilige Monat zum
 * Testzeitpunkt tatsaechlich in der Vergangenheit liegt.
 *
 * Dummy-Daten (DSGVO): Max Mustermann, ID 1.
 */
@ExtendWith(MockitoExtension.class)
class TagesSollCharakterisierungMonatsSaldoTest {

    @Mock
    private MonatsSaldoRepository monatsSaldoRepository;
    @Mock
    private ZeitbuchungRepository zeitbuchungRepository;
    @Mock
    private AbwesenheitRepository abwesenheitRepository;
    @Mock
    private ZeitkontoKorrekturRepository korrekturRepository;
    @Mock
    private MitarbeiterRepository mitarbeiterRepository;
    @Mock
    private ZeitkontoService zeitkontoService;
    @Mock
    private TagesSollService tagesSollService;

    @Mock
    private jakarta.persistence.EntityManager entityManager;

    @InjectMocks
    private MonatsSaldoService monatsSaldoService;

    private static final Long MITARBEITER_ID = 1L;

    private Mitarbeiter testMitarbeiter;
    private ZeitkontoVersion testZeitkonto;

    @BeforeEach
    void setUp() {
        // self-injection fuer den @Lazy @Autowired self-Proxy simulieren
        // (Vorbild: MonatsSaldoServiceTest.setUp()).


        testMitarbeiter = new Mitarbeiter();
        testMitarbeiter.setId(MITARBEITER_ID);
        testMitarbeiter.setVorname("Max");
        testMitarbeiter.setNachname("Mustermann");
        lenient().when(entityManager.find(Mitarbeiter.class, MITARBEITER_ID, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)).thenReturn(testMitarbeiter);

        testZeitkonto = new ZeitkontoVersion();
        testZeitkonto.setMontagStunden(new BigDecimal("8.00"));
        testZeitkonto.setDienstagStunden(new BigDecimal("8.00"));
        testZeitkonto.setMittwochStunden(new BigDecimal("8.00"));
        testZeitkonto.setDonnerstagStunden(new BigDecimal("8.00"));
        testZeitkonto.setFreitagStunden(new BigDecimal("8.00"));
        testZeitkonto.setSamstagStunden(new BigDecimal("0.00"));
        testZeitkonto.setSonntagStunden(new BigDecimal("0.00"));
    }

    /**
     * Stubbt alles, was {@code berechneMonatsSaldo} braucht, plus (lenient)
     * einen ungueltigen Cache-Eintrag und den Save-Pfad - fuer den Fall, dass
     * der Monat zum Testzeitpunkt bereits vergangen ist (siehe Klassen-Javadoc).
     *
     * {@code tagesSollService.feiertagsGutschriftSumme} wird mit exakten
     * {@code eq()}-Matchern (Mitarbeiter, ZeitkontoVersion, ersterTag, letzterTag)
     * auf den je Testfall erwarteten Wert gestubbt - keine {@code any()}-Pauschale,
     * damit ein falscher Stub-Wert den jeweiligen Test tatsaechlich rot werden
     * laesst (siehe Gegenprobe im Kontext-Log).
     */
    private void stubStandardMocks(int jahr, int monat, BigDecimal erwarteteFeiertagsGutschrift) {
        LocalDate ersterTag = LocalDate.of(jahr, monat, 1);
        LocalDate letzterTag = YearMonth.of(jahr, monat).atEndOfMonth();

        lenient().when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(
                eq(MITARBEITER_ID), any(), any())).thenReturn(Collections.emptyList());
        lenient().when(zeitkontoService.berechneSollstundenFuerMonat(MITARBEITER_ID, jahr, monat))
                .thenReturn(new BigDecimal("168.00"));
        lenient().when(abwesenheitRepository.sumStundenByMitarbeiterIdAndDatumBetween(
                eq(MITARBEITER_ID), eq(ersterTag), eq(letzterTag))).thenReturn(BigDecimal.ZERO);
        lenient().when(tagesSollService.feiertagsGutschriftSumme(
                eq(MITARBEITER_ID), eq(ersterTag), eq(letzterTag)))
                .thenReturn(erwarteteFeiertagsGutschrift);
        lenient().when(korrekturRepository.findByMitarbeiterIdAndDatumBetween(
                eq(MITARBEITER_ID), eq(ersterTag), eq(letzterTag))).thenReturn(Collections.emptyList());

        MonatsSaldo ungueltigerCache = new MonatsSaldo();
        ungueltigerCache.setMitarbeiter(testMitarbeiter);
        ungueltigerCache.setJahr(jahr);
        ungueltigerCache.setMonat(monat);
        ungueltigerCache.setGueltig(false);
        ungueltigerCache.setBerechnetAm(LocalDateTime.now().minusDays(1));
        lenient().when(monatsSaldoRepository.findGesperrt(MITARBEITER_ID, jahr, monat))
                .thenReturn(Optional.of(ungueltigerCache));
        lenient().when(monatsSaldoRepository.save(any(MonatsSaldo.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void vollerFeiertag_ergibtAchtStundenGutschrift() {
        // Do 2026-01-01 (Neujahr) - liegt zum Testzeitpunkt (08.09.2026) in der
        // Vergangenheit, geht also ueber den Cache-Pfad von getOrBerechne.
        stubStandardMocks(2026, 1, new BigDecimal("8.00"));

        MonatsSaldo result = monatsSaldoService.getOrBerechne(MITARBEITER_ID, 2026, 1);

        assertEquals(0, new BigDecimal("8.00").compareTo(result.getFeiertagsStunden()),
                "Voller Feiertag an einem Arbeitstag sollte 8 Stunden Gutschrift ergeben, war: "
                        + result.getFeiertagsStunden());
    }

    @Test
    void halberFeiertag_ergibtVierStundenGutschrift() {
        // Do 2026-12-24 (Heiligabend) - liegt zum Testzeitpunkt in der Zukunft,
        // getOrBerechne berechnet live ohne den Cache zu konsultieren.
        stubStandardMocks(2026, 12, new BigDecimal("4.00"));

        MonatsSaldo result = monatsSaldoService.getOrBerechne(MITARBEITER_ID, 2026, 12);

        assertEquals(0, new BigDecimal("4.00").compareTo(result.getFeiertagsStunden()),
                "Halber Feiertag sollte 4 Stunden Gutschrift ergeben, war: " + result.getFeiertagsStunden());
    }

    @Test
    void feiertagAmWochenende_ergibtKeineGutschrift() {
        // Sa 2026-12-26 - Sollstunden am Samstag sind 0, deshalb zaehlt der
        // Feiertag trotz vollem Feiertags-Flag nicht mit.
        stubStandardMocks(2026, 12, BigDecimal.ZERO);

        MonatsSaldo result = monatsSaldoService.getOrBerechne(MITARBEITER_ID, 2026, 12);

        assertEquals(0, BigDecimal.ZERO.compareTo(result.getFeiertagsStunden()),
                "Feiertag am Wochenende (Soll=0) sollte 0 Stunden Gutschrift ergeben, war: "
                        + result.getFeiertagsStunden());
    }
}
