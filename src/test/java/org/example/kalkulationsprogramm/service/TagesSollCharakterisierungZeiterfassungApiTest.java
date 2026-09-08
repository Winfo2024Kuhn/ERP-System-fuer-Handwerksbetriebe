package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Zeitkonto;
import org.example.kalkulationsprogramm.repository.LangzeitkrankmeldungPhaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Charakterisierungstest (Langzeitkrankmeldung, Abschnitt 1 / Task 2).
 *
 * Friert die frueher private Methode {@code ZeiterfassungApiService.berechneFeiertagsStunden}
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
 *
 * <p><b>Zweite Abweichung (Task 10, Abschnitt 3):</b> Task 10 loescht
 * {@code ZeiterfassungApiService.berechneFeiertagsStunden} ersatzlos und
 * verdrahtet den einzigen Aufrufer auf {@code TagesSollService.feiertagsGutschriftSumme}.
 * Der urspruengliche Zugriffsweg per {@link org.springframework.test.util.ReflectionTestUtils}
 * lief damit ins Leere (Methode existiert nicht mehr). Einzige erlaubte Anpassung
 * hier: der Zugriffsweg ruft jetzt direkt {@code TagesSollService.feiertagsGutschrift}
 * (Einzeltag-Variante) auf einer selbst gebauten {@code TagesSollService}-Instanz auf -
 * der neuen fachlichen Heimat dieser Berechnung. Alle drei Testfaelle verwenden
 * von == bis, ein Einzeltag-Aufruf ist dafuer aequivalent zur alten
 * Bereichsberechnung. Die erwarteten Zahlen (8, 4.00, 0) bleiben unveraendert.
 *
 * Dummy-Daten (DSGVO): Max Mustermann, ID 1.
 */
@ExtendWith(MockitoExtension.class)
class TagesSollCharakterisierungZeiterfassungApiTest {

    @Mock private FeiertagService feiertagService;
    @Mock private LangzeitkrankmeldungPhaseRepository phaseRepository;

    private TagesSollService tagesSollService;

    private Zeitkonto zeitkonto;

    private static final Long MITARBEITER_ID = 1L;
    private static final LocalDate VOLLER_FEIERTAG = LocalDate.of(2026, 1, 1);
    private static final LocalDate HALBER_FEIERTAG = LocalDate.of(2026, 12, 24);
    private static final LocalDate FEIERTAG_AM_WOCHENENDE = LocalDate.of(2026, 12, 26);

    @BeforeEach
    void setUp() {
        tagesSollService = new TagesSollService(feiertagService, phaseRepository);

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

    /**
     * von/bis wie im urspruenglichen Zugriffsweg beibehalten, obwohl alle
     * Aufrufer von == bis uebergeben - so bleibt sichtbar, dass hier
     * bewusst nur der Einzeltag-Fall abgedeckt ist.
     */
    private BigDecimal berechneFeiertagsStunden(LocalDate von, LocalDate bis) {
        if (!von.equals(bis)) {
            throw new IllegalArgumentException("Testhelfer deckt nur Einzeltag-Aufrufe ab (von == bis)");
        }
        return tagesSollService.feiertagsGutschrift(MITARBEITER_ID, zeitkonto, von);
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
