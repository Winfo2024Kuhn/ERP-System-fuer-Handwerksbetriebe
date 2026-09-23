package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAngebotDto.Abweichungsfreigabe;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAngebotDto.VersionDto;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufAngebotService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class EinkaufAngebotControllerTest {
    @Test
    void technischeAbweichungBrauchtEineSeparateBegruendeteFreigabe() {
        var offers = mock(EinkaufAngebotService.class);
        var rights = mock(EinkaufBerechtigungService.class);
        var auth = mock(Authentication.class);
        var result = new VersionDto(8L, 4L, 1, 3L, 2L, "GEPRUEFT", "A-1", null, null, "EUR",
                java.util.List.of(), java.util.List.of(), null, null, null, null, null, 7L, null, null, null);
        when(rights.verlange(auth, EinkaufBerechtigung.BEARBEITEN)).thenReturn(12L);
        when(offers.abweichungBestaetigen(8L, 3L, "Werkstoffalternative fachlich geprüft", 12L)).thenReturn(result);
        var controller = new EinkaufAngebotController(offers, rights);

        assertSame(result, controller.abweichungBestaetigen(8L, 3L,
                new Abweichungsfreigabe("Werkstoffalternative fachlich geprüft"), auth));

        verify(rights).verlange(auth, EinkaufBerechtigung.BEARBEITEN);
        verify(offers).abweichungBestaetigen(8L, 3L, "Werkstoffalternative fachlich geprüft", 12L);
    }
}
