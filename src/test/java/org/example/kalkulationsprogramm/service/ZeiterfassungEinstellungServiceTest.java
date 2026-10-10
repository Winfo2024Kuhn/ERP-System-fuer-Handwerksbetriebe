package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ZeiterfassungEinstellungServiceTest {

    @Mock SystemSettingsService settings;
    @InjectMocks ZeiterfassungEinstellungService service;

    @Test
    void ohneEinstellungGibtEsKeineGrenze() {
        when(settings.get(ZeiterfassungEinstellungService.EINGEFUEHRT_AM, "")).thenReturn("");
        assertThat(service.eingefuehrtAm()).isEmpty();
        assertThat(service.ersterAbschlussMonat()).isNull();
    }

    @Test
    void ersterAbschlussMonatIstDerMonatDesEinfuehrungstags() {
        when(settings.get(ZeiterfassungEinstellungService.EINGEFUEHRT_AM, "")).thenReturn("2025-03-17");
        assertThat(service.eingefuehrtAm()).contains(LocalDate.of(2025, 3, 17));
        assertThat(service.ersterAbschlussMonat()).isEqualTo(LocalDate.of(2025, 3, 1));
    }

    @Test
    void kaputterGespeicherterWertWirdIgnoriert() {
        when(settings.get(ZeiterfassungEinstellungService.EINGEFUEHRT_AM, "")).thenReturn("<script>alert(1)</script>");
        assertThat(service.eingefuehrtAm()).isEmpty();
        assertThat(service.ersterAbschlussMonat()).isNull();
    }

    @Test
    void speichernUndEntfernen() {
        assertThat(service.speichern("2025-03-17")).contains(LocalDate.of(2025, 3, 17));
        verify(settings).save(eq(ZeiterfassungEinstellungService.EINGEFUEHRT_AM), eq("2025-03-17"), anyString());
        assertThat(service.speichern("  ")).isEmpty();
        verify(settings).save(eq(ZeiterfassungEinstellungService.EINGEFUEHRT_AM), eq(""), anyString());
    }

    @Test
    void heuteIstErlaubtMorgenNicht() {
        assertThat(service.speichern(LocalDate.now().toString())).contains(LocalDate.now());
        assertThatThrownBy(() -> service.speichern(LocalDate.now().plusDays(1).toString()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Zukunft");
        verify(settings, never()).save(eq(ZeiterfassungEinstellungService.EINGEFUEHRT_AM),
                eq(LocalDate.now().plusDays(1).toString()), anyString());
    }
}
