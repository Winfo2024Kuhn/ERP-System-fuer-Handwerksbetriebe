package org.example.kalkulationsprogramm.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufDateiService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class EinkaufDateiControllerTest {
    @Test
    void downloadsAsAttachmentWithNosniffAndRequiresReadPermission() throws java.io.IOException {
        EinkaufDateiService service = mock(EinkaufDateiService.class);
        EinkaufBerechtigungService permissions = mock(EinkaufBerechtigungService.class);
        var auth = new UsernamePasswordAuthenticationToken("test@example.com", "dummy");
        when(permissions.verlange(auth, EinkaufBerechtigung.LESEN)).thenReturn(4L);
        when(service.laden(9L, auth)).thenReturn(new ByteArrayResource(new byte[]{1, 2}));
        var response = new EinkaufDateiController(service, permissions).laden(9L, auth);

        org.junit.jupiter.api.Assertions.assertTrue(response.getHeaders().getFirst("Content-Disposition").startsWith("attachment;"));
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertEquals(2, response.getBody().contentLength());
    }
}
