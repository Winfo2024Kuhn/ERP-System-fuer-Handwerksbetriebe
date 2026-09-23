package org.example.kalkulationsprogramm.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufZeugnisDto.Pruefung;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufZeugnisService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

class EinkaufZeugnisSecurityTest {
    @Test
    void pruefungOhneSpezialrechtErreichtDenServiceNicht() {
        var service=mock(EinkaufZeugnisService.class);
        var rights=mock(EinkaufBerechtigungService.class);
        var auth=mock(Authentication.class);
        when(rights.verlange(auth,EinkaufBerechtigung.ZEUGNIS_PRUEFEN)).thenThrow(new AccessDeniedException("forbidden"));
        var controller=new EinkaufZeugnisController(service,rights);

        assertThrows(AccessDeniedException.class,()->controller.pruefen(12L,new Pruefung(0,"BESTANDEN","Dummy geprüft","V1"),auth));

        verify(rights).verlange(auth,EinkaufBerechtigung.ZEUGNIS_PRUEFEN);
        verifyNoInteractions(service);
    }
}
