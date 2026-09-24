package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKommunikationDto.Vorschau;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKommunikationDto.Antwort;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKommunikationDto.AntwortVorschau;
import org.example.kalkulationsprogramm.service.EmailImportService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufAntwortZuordnungService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufKommunikationService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufDateiService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class EinkaufKommunikationControllerTest {
    @Test
    void pdfVorschauLiefertSnapshotBytesDerDateiIdMitLeserecht() throws Exception {
        var files = mock(EinkaufDateiService.class);
        var rights = mock(EinkaufBerechtigungService.class);
        var auth = mock(Authentication.class);
        byte[] pdf;
        try (var document = new org.apache.pdfbox.pdmodel.PDDocument(); var out = new java.io.ByteArrayOutputStream()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage()); document.save(out); pdf = out.toByteArray();
        }
        when(files.ladePdfSnapshot(55L, auth)).thenReturn(new org.springframework.core.io.ByteArrayResource(pdf));
        var controller = new EinkaufKommunikationController(mock(EinkaufKommunikationService.class),
                mock(EinkaufAntwortZuordnungService.class), rights, mock(EmailImportService.class), files);
        var response = controller.pdfVorschau(55L, auth);
        assertEquals(org.springframework.http.MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        org.junit.jupiter.api.Assertions.assertArrayEquals(pdf, response.getBody().getInputStream().readAllBytes());
        verify(rights).verlange(auth, EinkaufBerechtigung.LESEN);
        verify(files).ladePdfSnapshot(55L, auth);
        verify(files, never()).laden(anyLong(), any());
    }

    @Test
    void antwortVorschauUndVersandVerlangenRechteVorServicezugriff() {
        var service = mock(EinkaufKommunikationService.class);
        var rights = mock(EinkaufBerechtigungService.class);
        var auth = mock(Authentication.class);
        var controller = new EinkaufKommunikationController(service, mock(EinkaufAntwortZuordnungService.class), rights,
                mock(EmailImportService.class), mock(EinkaufDateiService.class));
        var request = new Antwort("Re: Angebot", "<p>Danke</p>", List.of(), null, java.util.UUID.randomUUID());
        var preview = new AntwortVorschau("token", "Re: Angebot", "<p>Danke</p>", "test@example.invalid", List.of(),
                "<supplier@example.invalid>", List.of("<original@example.invalid>"));
        when(rights.verlange(auth, EinkaufBerechtigung.ANFRAGE_SENDEN)).thenReturn(7L);
        when(service.antwortVorschau(12L, request)).thenReturn(preview);

        assertSame(preview, controller.antwortVorschau(12L, request, auth));
        verify(service).antwortVorschau(12L, request);

        when(rights.verlange(auth, EinkaufBerechtigung.ANFRAGE_SENDEN))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("kein Recht"));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> controller.antworten(12L, request, auth));
        verify(service, never()).antworten(any(), any(), any());
    }

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
    void statusUndGezielteWiederholungPruefenGetrennteRechteVorServicezugriff() {
        var service = mock(EinkaufKommunikationService.class);
        var rights = mock(EinkaufBerechtigungService.class);
        var auth = mock(Authentication.class);
        var controller = new EinkaufKommunikationController(service, mock(EinkaufAntwortZuordnungService.class), rights,
                mock(EmailImportService.class), mock(EinkaufDateiService.class));
        when(rights.verlange(auth, EinkaufBerechtigung.LESEN)).thenThrow(new org.springframework.security.access.AccessDeniedException("kein Recht"));
        when(rights.verlange(auth, EinkaufBerechtigung.ANFRAGE_SENDEN)).thenThrow(new org.springframework.security.access.AccessDeniedException("kein Recht"));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> controller.versandstatus(1L, 2L, auth));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> controller.erneutSenden(1L, 3L, 4L, new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKommunikationDto.VersandWiederholung(0), auth));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> controller.antwortVersandstatus(9L, auth));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> controller.antwortErneutSenden(9L, 7L, new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKommunikationDto.VersandWiederholung(0), auth));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> controller.antwortKlaeren(9L, 7L, new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.Klaerung(0,
                        org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.Entscheidung.BEREITS_ANGENOMMEN, "Dummy-Beleg"), auth));
        verifyNoInteractions(service);
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
