package org.example.kalkulationsprogramm.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.List;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufFaelligkeitDto.*;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufFaelligkeitService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

class EinkaufFaelligkeitControllerTest {
    @Test
    void listeVerlangtLeserechtUndLiefertDiePaginierteFaelligkeitsliste(){
        var service=mock(EinkaufFaelligkeitService.class);var rights=mock(EinkaufBerechtigungService.class);var auth=mock(Authentication.class);
        var pageable=PageRequest.of(0,20);var expected=new PageImpl<>(List.of(new Faelligkeit("ZEUGNIS",4L,"B-4 / ZEUGNIS_3_1",null,LocalDate.parse("2026-09-22"),9L,"Fehlt")),pageable,1);
        when(rights.verlange(auth,EinkaufBerechtigung.LESEN)).thenReturn(9L);
        when(service.liste(LocalDate.parse("2026-09-23"),9L,pageable)).thenReturn(expected);
        var controller=new EinkaufFaelligkeitController(service,rights);

        assertEquals(expected,controller.liste(LocalDate.parse("2026-09-23"),9L,pageable,auth));
        verify(service).liste(LocalDate.parse("2026-09-23"),9L,pageable);
    }

    @Test
    void nachfrageOhneLeserechtWirdNichtVorbereitet(){
        var service=mock(EinkaufFaelligkeitService.class);var rights=mock(EinkaufBerechtigungService.class);var auth=mock(Authentication.class);
        when(rights.verlange(auth,EinkaufBerechtigung.LESEN)).thenThrow(new AccessDeniedException("forbidden"));
        var controller=new EinkaufFaelligkeitController(service,rights);

        assertThrows(AccessDeniedException.class,()->controller.nachfrage(new NachfrageRequest("ZEUGNIS",4L,null),auth));
        verifyNoInteractions(service);
    }
}
