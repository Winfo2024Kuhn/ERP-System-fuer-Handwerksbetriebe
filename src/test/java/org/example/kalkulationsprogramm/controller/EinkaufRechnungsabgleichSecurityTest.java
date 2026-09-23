package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufRechnungsabgleichDto.BelegZuordnung;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufRechnungsabgleichService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class EinkaufRechnungsabgleichSecurityTest {
    @Test
    void fehlendesLeserechtUndReinesLeserechtVerhindernUnberechtigteAktionen() {
        var profiles=mock(FrontendUserProfileRepository.class);
        var profile=mock(FrontendUserProfile.class);
        when(profiles.findById(9L)).thenReturn(Optional.of(profile));
        when(profile.isActive()).thenReturn(true);
        when(profile.getEinkaufBerechtigungen()).thenReturn(Set.of());
        var principal=new FrontendUserPrincipal(9L,"dummy","Max Mustermann","unused",true,Set.of());
        var auth=new UsernamePasswordAuthenticationToken(principal,null,List.of());
        var service=mock(EinkaufRechnungsabgleichService.class);
        var controller=new EinkaufRechnungsabgleichController(service,new EinkaufBerechtigungService(profiles));
        assertThrows(AccessDeniedException.class,()->controller.vergleichen(41L,auth));
        when(profile.getEinkaufBerechtigungen()).thenReturn(Set.of(EinkaufBerechtigung.LESEN));
        assertThrows(AccessDeniedException.class,()->controller.zuordnen(42L,
                new BelegZuordnung(41L,0,"RECHNUNG",null,List.of(),UUID.randomUUID()),auth));
        verifyNoInteractions(service);
    }
}
