package org.example.kalkulationsprogramm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.example.kalkulationsprogramm.service.TailscaleFunnelFreigabe;
import org.junit.jupiter.api.Test;

class ZeiterfassungZugangControllerTest {

    private final TailscaleFunnelFreigabe freigabe = mock(TailscaleFunnelFreigabe.class);
    private final ZeiterfassungZugangController controller = new ZeiterfassungZugangController(freigabe);

    @Test
    void speichertDieFreigabeUndMeldetDenNeuenStand() {
        when(freigabe.istFreigegeben()).thenReturn(true);

        var antwort = controller.speichern(new ZeiterfassungZugangController.ZugangRequest(true));

        verify(freigabe).setzen(true);
        assertThat(antwort.getStatusCode().value()).isEqualTo(200);
        assertThat(antwort.getBody().funnelFreigegeben()).isTrue();
    }

    @Test
    void ohneAngabeWirdNichtsUmgeschaltet() {
        var antwort = controller.speichern(new ZeiterfassungZugangController.ZugangRequest(null));

        assertThat(antwort.getStatusCode().value()).isEqualTo(400);
        verify(freigabe, never()).setzen(anyBoolean());
    }
}
