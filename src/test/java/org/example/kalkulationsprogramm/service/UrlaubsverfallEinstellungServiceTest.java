package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;

import org.example.kalkulationsprogramm.service.UrlaubsverfallEinstellungService.Verfallsregel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UrlaubsverfallEinstellungServiceTest {

    @Mock SystemSettingsService settings;
    private UrlaubsverfallEinstellungService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(LocalDate.of(2026, 10, 13).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        service = new UrlaubsverfallEinstellungService(settings, clock);
    }

    @Test
    void ohneEinstellungGiltDerGesetzlicheStandard() {
        when(settings.get(UrlaubsverfallEinstellungService.VERFAELLT, "true")).thenReturn("true");
        when(settings.get(UrlaubsverfallEinstellungService.VERFALL_TAG, "")).thenReturn("");

        Verfallsregel regel = service.regel();

        assertThat(regel).isEqualTo(Verfallsregel.STANDARD);
        assertThat(regel.verfallsdatum(2027)).contains(LocalDate.of(2027, 3, 31));
    }

    @Test
    void eigenerTagUndVerfaelltNicht() {
        when(settings.get(UrlaubsverfallEinstellungService.VERFAELLT, "true")).thenReturn("false");
        when(settings.get(UrlaubsverfallEinstellungService.VERFALL_TAG, "")).thenReturn("06-30");

        Verfallsregel regel = service.regel();

        assertThat(regel.verfaellt()).isFalse();
        assertThat(regel.verfallsTag()).isEqualTo(MonthDay.of(6, 30));
        assertThat(regel.verfallsdatum(2027)).isEmpty();
    }

    @Test
    void kaputterGespeicherterTagFaelltAufDenStandardZurueck() {
        when(settings.get(UrlaubsverfallEinstellungService.VERFAELLT, "true")).thenReturn("true");
        when(settings.get(UrlaubsverfallEinstellungService.VERFALL_TAG, "")).thenReturn("<script>alert(1)</script>");

        assertThat(service.regel().verfallsTag()).isEqualTo(MonthDay.of(3, 31));
    }

    @Test
    void schalttagWirdImNormalenJahrZum28Februar() {
        Verfallsregel regel = new Verfallsregel(true, MonthDay.of(2, 29));
        assertThat(regel.verfallsdatum(2027)).contains(LocalDate.of(2027, 2, 28));
        assertThat(regel.verfallsdatum(2028)).contains(LocalDate.of(2028, 2, 29));
    }

    @Test
    void speichertGueltigeRegel() {
        Verfallsregel regel = service.speichern(false, 30, 6);

        assertThat(regel).isEqualTo(new Verfallsregel(false, MonthDay.of(6, 30)));
        verify(settings).save(eq(UrlaubsverfallEinstellungService.VERFAELLT), eq("false"), anyString());
        verify(settings).save(eq(UrlaubsverfallEinstellungService.VERFALL_TAG), eq("06-30"), anyString());
    }

    @Test
    void lehntUngueltigeTageAb() {
        assertThatThrownBy(() -> service.speichern(true, 31, 2))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Diesen Tag gibt es nicht");
        assertThatThrownBy(() -> service.speichern(true, null, 3))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Tag und Monat");
        assertThatThrownBy(() -> service.speichern(true, 1, 13))
                .isInstanceOf(ResponseStatusException.class);
        verify(settings, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void startjahrAusDerEinstellung_sonstLaufendesJahr() {
        when(settings.get(UrlaubsverfallEinstellungService.AB_JAHR, "")).thenReturn("2025", "", "kaputt");

        assertThat(service.uebertragAbJahr()).isEqualTo(2025);
        assertThat(service.uebertragAbJahr()).isEqualTo(2026);
        assertThat(service.uebertragAbJahr()).isEqualTo(2026);
    }

    @Test
    void mehrurlaubImEintrittsjahr_standardIstDasGesetz() {
        when(settings.get(UrlaubsverfallEinstellungService.MEHRURLAUB_ANTEILIG, "false")).thenReturn("false", " TRUE ");

        assertThat(service.mehrurlaubImEintrittsjahrAnteilig()).isFalse();
        assertThat(service.mehrurlaubImEintrittsjahrAnteilig()).isTrue();
    }

    @Test
    void mehrurlaubImEintrittsjahrSpeichern() {
        assertThat(service.speichereMehrurlaubImEintrittsjahrAnteilig(true)).isTrue();
        verify(settings).save(eq(UrlaubsverfallEinstellungService.MEHRURLAUB_ANTEILIG), eq("true"), anyString());

        assertThatThrownBy(() -> service.speichereMehrurlaubImEintrittsjahrAnteilig(null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Eintrittsjahr");
    }
}
