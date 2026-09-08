package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Zeitkonto;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ZeitkontoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Charakterisierungstest (Langzeitkrankmeldung, Abschnitt 1 / Task 2).
 *
 * Friert zahlengenau ein, was {@link ZeitkontoService#berechneSollstundenFuerZeitraum}
 * vor der Umstellung auf {@link TagesSollService} lieferte (siehe E2 im Plan).
 * Wird eine dieser Zusicherungen rot, ist die Zusicherung falsch, nicht der
 * Bestand.
 *
 * Wichtiger Befund (weiterhin gueltig): die alte Schleife fragte beim
 * Feiertag NUR {@code FeiertagService#istHalberFeiertag} ab, niemals
 * {@code istFeiertag}. Ein voller Feiertag zaehlte deshalb als bezahlter
 * Arbeitstag mit den vollen Sollstunden - das ist E2's "periodenSoll"
 * wortwoertlich.
 *
 * Seit Task 7 delegiert {@code berechneSollstundenFuerZeitraum} an
 * {@link TagesSollService#periodenSollSumme}. Diese Klasse mockt deshalb
 * {@code TagesSollService} statt {@code FeiertagService} und stubt pro Test
 * genau den Wert, den die alte Schleife fuer den jeweiligen Fixture-Tag
 * geliefert hat - reine Verkabelung, die Zusicherungen (Zahlen, Faelle)
 * sind unveraendert.
 *
 * Dummy-Daten (DSGVO): Max Mustermann, ID 1.
 */
@ExtendWith(MockitoExtension.class)
class TagesSollCharakterisierungZeitkontoTest {

    @Mock
    private ZeitkontoRepository zeitkontoRepository;

    @Mock
    private MitarbeiterRepository mitarbeiterRepository;

    @Mock
    private TagesSollService tagesSollService;

    @InjectMocks
    private ZeitkontoService zeitkontoService;

    private Zeitkonto zeitkonto;

    // Gemeinsame Fixture aus dem Plan (Task 2): Mo-Fr 8,00 h, Sa/So 0,00 h.
    private static final LocalDate MONTAG_NORMAL = LocalDate.of(2026, 6, 1);
    private static final LocalDate SAMSTAG_WOCHENENDE = LocalDate.of(2026, 6, 6);
    private static final LocalDate VOLLER_FEIERTAG = LocalDate.of(2026, 1, 1); // Donnerstag, Neujahr
    private static final LocalDate HALBER_FEIERTAG = LocalDate.of(2026, 12, 24); // Donnerstag, Heiligabend
    private static final LocalDate FEIERTAG_AM_WOCHENENDE = LocalDate.of(2026, 12, 26); // Samstag

    @BeforeEach
    void setUp() {
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

    @Test
    void montag_normalerArbeitstag_gibtVolleSollstunden() {
        when(tagesSollService.periodenSollSumme(1L, zeitkonto, MONTAG_NORMAL, MONTAG_NORMAL))
                .thenReturn(new BigDecimal("8"));

        BigDecimal result = zeitkontoService.berechneSollstundenFuerZeitraum(zeitkonto, MONTAG_NORMAL, MONTAG_NORMAL);

        assertEquals(0, new BigDecimal("8").compareTo(result));
    }

    @Test
    void samstag_wochenende_gibtNull() {
        when(tagesSollService.periodenSollSumme(1L, zeitkonto, SAMSTAG_WOCHENENDE, SAMSTAG_WOCHENENDE))
                .thenReturn(BigDecimal.ZERO);

        BigDecimal result = zeitkontoService.berechneSollstundenFuerZeitraum(zeitkonto, SAMSTAG_WOCHENENDE,
                SAMSTAG_WOCHENENDE);

        assertEquals(0, BigDecimal.ZERO.compareTo(result));
    }

    @Test
    void vollerFeiertag_zaehltAlsBezahlterArbeitstag_gibtVolleSollstunden() {
        when(tagesSollService.periodenSollSumme(1L, zeitkonto, VOLLER_FEIERTAG, VOLLER_FEIERTAG))
                .thenReturn(new BigDecimal("8"));

        BigDecimal result = zeitkontoService.berechneSollstundenFuerZeitraum(zeitkonto, VOLLER_FEIERTAG,
                VOLLER_FEIERTAG);

        assertEquals(0, new BigDecimal("8").compareTo(result));
    }

    @Test
    void halberFeiertag_gibtHalbeSollstunden() {
        when(tagesSollService.periodenSollSumme(1L, zeitkonto, HALBER_FEIERTAG, HALBER_FEIERTAG))
                .thenReturn(new BigDecimal("4.00"));

        BigDecimal result = zeitkontoService.berechneSollstundenFuerZeitraum(zeitkonto, HALBER_FEIERTAG,
                HALBER_FEIERTAG);

        assertEquals(0, new BigDecimal("4.00").compareTo(result));
    }

    @Test
    void feiertagAmWochenende_gibtNull() {
        when(tagesSollService.periodenSollSumme(1L, zeitkonto, FEIERTAG_AM_WOCHENENDE, FEIERTAG_AM_WOCHENENDE))
                .thenReturn(BigDecimal.ZERO);

        BigDecimal result = zeitkontoService.berechneSollstundenFuerZeitraum(zeitkonto, FEIERTAG_AM_WOCHENENDE,
                FEIERTAG_AM_WOCHENENDE);

        assertEquals(0, BigDecimal.ZERO.compareTo(result));
    }

    @Test
    void volleWocheMitVollemFeiertagAmMittwoch_gibtVierzig() {
        // Mo 2026-06-01 bis So 2026-06-07, Mittwoch (2026-06-03) als voller
        // (nicht halber) Feiertag - aendert nichts an der Summe, weil ein
        // voller Feiertag als bezahlter Arbeitstag zaehlt (siehe Klassen-Javadoc).
        LocalDate wochenStart = LocalDate.of(2026, 6, 1);
        LocalDate wochenEnde = LocalDate.of(2026, 6, 7);
        when(tagesSollService.periodenSollSumme(1L, zeitkonto, wochenStart, wochenEnde))
                .thenReturn(new BigDecimal("40"));

        BigDecimal result = zeitkontoService.berechneSollstundenFuerZeitraum(zeitkonto, wochenStart, wochenEnde);

        assertEquals(0, new BigDecimal("40").compareTo(result));
    }
}
