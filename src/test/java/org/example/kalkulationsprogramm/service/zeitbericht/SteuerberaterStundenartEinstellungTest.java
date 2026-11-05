package org.example.kalkulationsprogramm.service.zeitbericht;

import org.example.kalkulationsprogramm.dto.zeitbericht.SteuerberaterBerichtDto.Stundenart;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SteuerberaterStundenartEinstellungTest {

    @Mock SystemSettingsService settings;
    @InjectMocks SteuerberaterStundenartEinstellung einstellung;

    @Test
    void nochNieGewaehltIstLeerUndStandardSoll() {
        when(settings.get(SteuerberaterStundenartEinstellung.SCHLUESSEL, "")).thenReturn("");
        assertThat(einstellung.gespeichert()).isEmpty();
        assertThat(einstellung.oderStandard()).isEqualTo(Stundenart.SOLL);
    }

    @Test
    void liestGespeicherteWahl() {
        when(settings.get(SteuerberaterStundenartEinstellung.SCHLUESSEL, "")).thenReturn(" IST ");
        assertThat(einstellung.gespeichert()).contains(Stundenart.IST);
        assertThat(einstellung.oderStandard()).isEqualTo(Stundenart.IST);
    }

    @Test
    void ungueltigerWertWirdIgnoriert() {
        when(settings.get(SteuerberaterStundenartEinstellung.SCHLUESSEL, "")).thenReturn("<script>");
        assertThat(einstellung.gespeichert()).isEmpty();
        when(settings.get(SteuerberaterStundenartEinstellung.SCHLUESSEL, "")).thenReturn(null);
        assertThat(einstellung.gespeichert()).isEmpty();
    }

    @Test
    void speichertWahlUndVerlangtEineAuswahl() {
        assertThat(einstellung.speichern(Stundenart.IST)).isEqualTo(Stundenart.IST);
        verify(settings).save(eq(SteuerberaterStundenartEinstellung.SCHLUESSEL), eq("IST"), anyString());

        assertThatThrownBy(() -> einstellung.speichern(null)).isInstanceOf(ResponseStatusException.class);
        verify(settings, never()).save(eq(SteuerberaterStundenartEinstellung.SCHLUESSEL), eq("null"), anyString());
    }
}
