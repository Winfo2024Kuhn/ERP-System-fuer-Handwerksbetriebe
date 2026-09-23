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
}
