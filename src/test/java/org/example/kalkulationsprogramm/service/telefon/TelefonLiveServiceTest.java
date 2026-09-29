package org.example.kalkulationsprogramm.service.telefon;

import org.example.kalkulationsprogramm.dto.Telefon.LiveAnrufDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelefonLiveServiceTest {

    @Mock TelefonBerechtigungService berechtigung;

    @Test
    @DisplayName("Ereignis geht an Berechtigte; wem das Recht entzogen wurde, dessen Verbindung wird geschlossen")
    void rechtWirdJeEreignisGeprueft() {
        TelefonLiveService live = new TelefonLiveService(berechtigung);
        live.verbinde(1L);
        live.verbinde(1L);
        live.verbinde(2L);
        assertThat(live.anzahlVerbindungen()).isEqualTo(3);

        when(berechtigung.darfTelefonSehen(1L)).thenReturn(true);
        when(berechtigung.darfTelefonSehen(2L)).thenReturn(false);
        live.sende(new LiveAnrufDto("0", "KLINGELT", "09311234567", null, List.of(), false));

        assertThat(live.anzahlVerbindungen()).isEqualTo(2);
        live.herzschlag();
    }
}
