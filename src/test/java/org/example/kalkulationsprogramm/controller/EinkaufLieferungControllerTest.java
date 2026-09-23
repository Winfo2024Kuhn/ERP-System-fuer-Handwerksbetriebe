package org.example.kalkulationsprogramm.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufLieferungDto.*;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufLieferungService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class EinkaufLieferungControllerTest {
    @Test
    void annahmeVerlangtBearbeitungsrechtUndDelegiert() {
        var service = mock(EinkaufLieferungService.class);
        var rights = mock(EinkaufBerechtigungService.class);
        var auth = mock(Authentication.class);
        var request = new Annahme(2, 8L, Instant.parse("2026-09-23T09:00:00Z"),
                List.of(new Lieferanteil(12L, new BigDecimal("2"), "Charge-1", "Heat-1", List.of())), UUID.randomUUID());
        var expected = new LieferungDto(4L, 5L, 3L, 8L, request.eingang(), request.positionen());
        when(rights.verlange(auth, EinkaufBerechtigung.BEARBEITEN)).thenReturn(9L);
        when(service.annehmen(5L, request, 9L)).thenReturn(expected);
        var controller = new EinkaufLieferungController(service, rights);

        assertEquals(expected, controller.annehmen(5L, request, auth));
        verify(rights).verlange(auth, EinkaufBerechtigung.BEARBEITEN);
        verify(service).annehmen(5L, request, 9L);
    }
}
