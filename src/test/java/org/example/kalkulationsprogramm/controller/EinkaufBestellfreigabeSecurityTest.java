package org.example.kalkulationsprogramm.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBestellfreigabeService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

class EinkaufBestellfreigabeSecurityTest {
    @Test
    void fremdeRolleKannBestellungNichtFreigeben() {
        var service = mock(EinkaufBestellfreigabeService.class);
        var rights = mock(EinkaufBerechtigungService.class);
        var auth = mock(Authentication.class);
        when(rights.verlange(auth, EinkaufBerechtigung.BESTELLUNG_FREIGEBEN))
                .thenThrow(new AccessDeniedException("forbidden"));
        var controller = new EinkaufBestellfreigabeController(service, rights);

        assertThrows(AccessDeniedException.class,
                () -> controller.freigeben(17L, null, auth));
        verifyNoInteractions(service);
    }
}
