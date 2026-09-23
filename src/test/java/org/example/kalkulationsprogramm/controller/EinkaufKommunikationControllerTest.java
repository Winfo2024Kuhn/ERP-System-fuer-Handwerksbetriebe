package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKommunikationDto.Vorschau;
import org.example.kalkulationsprogramm.service.EmailImportService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufAntwortZuordnungService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufKommunikationService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufDateiService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class EinkaufKommunikationControllerTest {
    @Test
    void vorschauVerlangtVersandrechtUndNutztVorgegebeneVorlage() {
        var communication = mock(EinkaufKommunikationService.class);
        var assignments = mock(EinkaufAntwortZuordnungService.class);
        var rights = mock(EinkaufBerechtigungService.class);
        var imports = mock(EmailImportService.class);
        var files = mock(EinkaufDateiService.class);
        var auth = mock(Authentication.class);
        var preview = new Vorschau(4, "token", "Betreff", "Text", "test@example.com", 9L, java.util.List.of());
        when(rights.verlange(auth, EinkaufBerechtigung.ANFRAGE_SENDEN)).thenReturn(7L);
        when(communication.vorschau(3L, 5L, 6L)).thenReturn(preview);
        var controller = new EinkaufKommunikationController(communication, assignments, rights, imports, files);

        assertSame(preview, controller.vorschau(3L, 5L, 6L, auth));

        verify(rights).verlange(auth, EinkaufBerechtigung.ANFRAGE_SENDEN);
        verify(communication).vorschau(3L, 5L, 6L);
    }

    @Test
    void manuellerAbrufIstAusdruecklichAufEinkaufskontoBegrenzt() {
        var imports = mock(EmailImportService.class);
        var rights = mock(EinkaufBerechtigungService.class);
        var auth = mock(Authentication.class);
        when(rights.verlange(auth, EinkaufBerechtigung.BEARBEITEN)).thenReturn(7L);
        when(imports.doImport("EINKAUF")).thenReturn(2);
        var controller = new EinkaufKommunikationController(mock(EinkaufKommunikationService.class),
                mock(EinkaufAntwortZuordnungService.class), rights, imports, mock(EinkaufDateiService.class));

        assertEquals(2, controller.abrufen(auth).importierteNachrichten());

        verify(imports).doImport("EINKAUF");
        verify(rights).verlange(auth, EinkaufBerechtigung.BEARBEITEN);
    }
}
