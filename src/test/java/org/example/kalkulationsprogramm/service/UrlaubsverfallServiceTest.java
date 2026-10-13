package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.UrlaubsverfallEinstellungService.Verfallsregel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Hinweis „Resturlaub verfällt bald“ mit eingestelltem Verfallstag. Dummy-Daten: Max Mustermann. */
@ExtendWith(MockitoExtension.class)
class UrlaubsverfallServiceTest {

    private static final String TOKEN = "token-max-mustermann";

    @Mock MitarbeiterRepository mitarbeiterRepository;
    @Mock UrlaubskontoService urlaubskontoService;
    @Mock UrlaubsverfallEinstellungService einstellungen;

    private Mitarbeiter max;

    @BeforeEach
    void setUp() {
        max = new Mitarbeiter();
        max.setId(1L);
        max.setVorname("Max");
        max.setNachname("Mustermann");
        lenient().when(mitarbeiterRepository.findByLoginTokenAndAktivTrue(TOKEN)).thenReturn(Optional.of(max));
        lenient().when(mitarbeiterRepository.findById(1L)).thenReturn(Optional.of(max));
    }

    private UrlaubsverfallService service(LocalDate heute) {
        Clock clock = Clock.fixed(heute.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        return new UrlaubsverfallService(mitarbeiterRepository, urlaubskontoService, einstellungen, clock);
    }

    private static Urlaubskonto konto(int jahr, String offen, LocalDate verfall) {
        BigDecimal o = new BigDecimal(offen);
        return new Urlaubskonto(jahr, new java.math.BigDecimal("30"), o, false, verfall, BigDecimal.ZERO, o, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("30").add(o));
    }

    @Test
    void warntZweiMonateVorDemStandardVerfall() {
        when(einstellungen.regel()).thenReturn(Verfallsregel.STANDARD);
        LocalDate verfall = LocalDate.of(2027, 3, 31);
        when(urlaubskontoService.berechne(max, 2027)).thenReturn(konto(2027, "2.5", verfall));

        Map<String, Object> warnung = service(LocalDate.of(2027, 3, 11)).pruefeVerfallWarnungByToken(TOKEN).orElseThrow();

        assertThat(warnung).containsEntry("resturlaubTage", new BigDecimal("2.5"))
                .containsEntry("verfallsDatum", "2027-03-31")
                .containsEntry("verfallsJahr", 2027)
                .containsEntry("tageVerbleibend", 20L)
                .containsEntry("dringend", true);
    }

    @Test
    void vorDemWarnzeitraumUndNachDemVerfallKeinHinweis() {
        when(einstellungen.regel()).thenReturn(Verfallsregel.STANDARD);

        assertThat(service(LocalDate.of(2027, 1, 30)).pruefeVerfallWarnung(1L)).isEmpty();
        assertThat(service(LocalDate.of(2027, 4, 1)).pruefeVerfallWarnung(1L)).isEmpty();
        verifyNoInteractions(urlaubskontoService);
    }

    @Test
    void ohneOffenenVorjahresrestKeinHinweis() {
        when(einstellungen.regel()).thenReturn(Verfallsregel.STANDARD);
        when(urlaubskontoService.berechne(max, 2027)).thenReturn(konto(2027, "0", LocalDate.of(2027, 3, 31)));

        assertThat(service(LocalDate.of(2027, 2, 1)).pruefeVerfallWarnung(1L)).isEmpty();
    }

    @Test
    void verfaelltNichtHeisstNieEinHinweis() {
        when(einstellungen.regel()).thenReturn(new Verfallsregel(false, MonthDay.of(3, 31)));

        assertThat(service(LocalDate.of(2027, 3, 1)).pruefeVerfallWarnung(1L)).isEmpty();
        verifyNoInteractions(urlaubskontoService);
    }

    @Test
    void fruehesVerfallsdatumWarntSchonImVorjahr() {
        when(einstellungen.regel()).thenReturn(new Verfallsregel(true, MonthDay.of(1, 15)));
        when(urlaubskontoService.berechne(max, 2027)).thenReturn(konto(2027, "3", LocalDate.of(2027, 1, 15)));

        Map<String, Object> warnung = service(LocalDate.of(2026, 12, 1)).pruefeVerfallWarnung(1L).orElseThrow();

        assertThat(warnung).containsEntry("verfallsDatum", "2027-01-15").containsEntry("dringend", false);
    }

    @Test
    void geschaeftsfuehrungUndUnbekannteBekommenNichts() {
        max.setIstGeschaeftsfuehrer(true);
        assertThat(service(LocalDate.of(2027, 3, 1)).pruefeVerfallWarnung(1L)).isEmpty();
        when(mitarbeiterRepository.findByLoginTokenAndAktivTrue("unbekannt")).thenReturn(Optional.empty());
        assertThat(service(LocalDate.of(2027, 3, 1)).pruefeVerfallWarnungByToken("unbekannt")).isEmpty();
        verifyNoInteractions(einstellungen);
        org.mockito.Mockito.verify(urlaubskontoService, org.mockito.Mockito.never()).berechne(any(), anyInt());
    }
}
