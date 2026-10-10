package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class TailscaleFunnelFreigabeTest {

    private final SystemSettingsService settings = mock(SystemSettingsService.class);

    @Test
    void ohneEintragNutzenDieMitarbeiterDieTailscaleApp() {
        when(settings.get(TailscaleFunnelFreigabe.SCHLUESSEL, "false")).thenReturn("false");

        assertThat(new TailscaleFunnelFreigabe(settings).istFreigegeben()).isFalse();
    }

    @Test
    void wertWirdNurEinmalGelesen() {
        when(settings.get(TailscaleFunnelFreigabe.SCHLUESSEL, "false")).thenReturn("true");
        var freigabe = new TailscaleFunnelFreigabe(settings);

        assertThat(freigabe.istFreigegeben()).isTrue();
        assertThat(freigabe.istFreigegeben()).isTrue();

        // Die Netzgrenze fragt bei jeder Funnel-Anfrage – nicht jedes Mal die Datenbank.
        verify(settings, times(1)).get(TailscaleFunnelFreigabe.SCHLUESSEL, "false");
    }

    @Test
    void umschaltenSpeichertUndGiltSofort() {
        when(settings.get(TailscaleFunnelFreigabe.SCHLUESSEL, "false")).thenReturn("false");
        var freigabe = new TailscaleFunnelFreigabe(settings);
        assertThat(freigabe.istFreigegeben()).isFalse();

        freigabe.setzen(true);

        verify(settings).save(eq(TailscaleFunnelFreigabe.SCHLUESSEL), eq("true"), anyString());
        assertThat(freigabe.istFreigegeben()).isTrue();
    }

    @Test
    void merktSichDieLetzteFunnelAnfrage() {
        var freigabe = new TailscaleFunnelFreigabe(settings);
        assertThat(freigabe.letzteAnfrage()).isNull();

        freigabe.anfrageGesehen(true);

        assertThat(freigabe.letzteAnfrage()).isNotNull();
        assertThat(freigabe.letzteAbgewiesen()).isTrue();
    }
}
