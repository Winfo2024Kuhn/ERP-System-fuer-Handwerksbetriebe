package org.example.kalkulationsprogramm.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.HiCadImportDto;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.service.einkauf.HiCadImportService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class HiCadImportControllerTest {
    @Test
    void technischeAnlageErfordertBearbeitungsrechtVorJedemUpload() {
        var dienst = mock(HiCadImportService.class);
        var rechte = mock(EinkaufBerechtigungService.class);
        var anmeldung = new UsernamePasswordAuthenticationToken("dummy", "dummy");
        var datei = new MockMultipartFile("datei", "zeichnung.pdf", "application/pdf", "%PDF-1.7 Dummy".getBytes());
        when(rechte.verlange(anmeldung, EinkaufBerechtigung.BEARBEITEN))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Kein Recht"));
        var steuerung = new HiCadImportController(dienst, rechte);
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> steuerung.anlageErgänzen(3L, 2, datei, anmeldung));
        org.mockito.Mockito.verifyNoInteractions(dienst);
        org.mockito.Mockito.doReturn(4L).when(rechte).verlange(anmeldung, EinkaufBerechtigung.BEARBEITEN);
        steuerung.anlageErgänzen(3L, 2, datei, anmeldung);
        org.mockito.Mockito.verify(dienst).anlageErgänzen(3L, 2, datei, 4L);
    }

    @Test
    void previewDelegatesOnlyAfterTheEditPermissionCheck() {
        HiCadImportService service = mock(HiCadImportService.class);
        EinkaufBerechtigungService permissions = mock(EinkaufBerechtigungService.class);
        var auth = new UsernamePasswordAuthenticationToken("test@example.com", "dummy");
        var file = new MockMultipartFile("file", "parts.xlsx", "application/octet-stream", new byte[]{1});
        var expected = new HiCadImportDto.Vorschau(3L, "hash", false, List.of());
        when(permissions.verlange(auth, EinkaufBerechtigung.BEARBEITEN)).thenReturn(4L);
        when(service.vorschau(17L, file, null, 4L)).thenReturn(expected);

        var actual = new HiCadImportController(service, permissions).vorschau(17L, file, null, auth);

        assertEquals(expected, actual);
    }

    @Test
    void progressRequiresReadPermissionAndReturnsPersistedRemainingQuantity() {
        HiCadImportService service = mock(HiCadImportService.class);
        EinkaufBerechtigungService permissions = mock(EinkaufBerechtigungService.class);
        var auth = new UsernamePasswordAuthenticationToken("test@example.com", "dummy");
        var expected = new HiCadImportDto.ImportFortschritt(3L, 2L, false,
                List.of(new HiCadImportDto.ZeilenFortschritt(4, new java.math.BigDecimal("10"),
                        new java.math.BigDecimal("4"), new java.math.BigDecimal("6"), false)));
        when(permissions.verlange(auth, EinkaufBerechtigung.LESEN)).thenReturn(4L);
        when(service.fortschritt(3L, 4L)).thenReturn(expected);

        var actual = new HiCadImportController(service, permissions).fortschritt(3L, auth);

        assertEquals(expected, actual);
    }
}
