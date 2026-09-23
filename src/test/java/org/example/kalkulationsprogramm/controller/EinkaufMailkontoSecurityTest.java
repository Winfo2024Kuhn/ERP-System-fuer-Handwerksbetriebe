package org.example.kalkulationsprogramm.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.config.LocalTestMailPolicy;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.repository.EinkaufMailkontoRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.mail.MailkontoService;
import org.example.kalkulationsprogramm.service.mail.MailSecretService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class EinkaufMailkontoSecurityTest {
    @Mock private EinkaufMailkontoRepository konten;
    @Mock private FrontendUserProfileRepository profiles;
    @Mock private SystemSettingsService settings;
    @Mock private MailSecretService secrets;
    private EinkaufMailkontoController controller;

    @BeforeEach
    void setUp() {
        MailkontoService service = new MailkontoService(konten, settings, secrets,
                new EinkaufBerechtigungService(profiles), org.mockito.Mockito.mock(LocalTestMailPolicy.class),
                org.mockito.Mockito.mock(org.example.kalkulationsprogramm.service.mail.KontoMailTransport.class));
        controller = new EinkaufMailkontoController(service);
    }

    @Test
    void adminRolleAusVeraltetemSessionPrincipalGewaehrenKeinenZugriff() {
        FrontendUserProfile profile = new FrontendUserProfile();
        profile.setActive(false);
        when(profiles.findById(70L)).thenReturn(Optional.of(profile));

        assertThrows(AccessDeniedException.class, () -> controller.lesen(auth(FrontendUserRole.ADMIN)));
        verifyNoInteractions(konten);
    }

    @Test
    void aktuelleDatenbankrolleMussAdminBleiben() {
        FrontendUserProfile profile = new FrontendUserProfile();
        profile.setActive(true);
        profile.setRoleSet(Set.of(FrontendUserRole.USER));
        when(profiles.findById(70L)).thenReturn(Optional.of(profile));

        assertThrows(AccessDeniedException.class, () -> controller.lesen(auth(FrontendUserRole.ADMIN)));
        verifyNoInteractions(konten);
    }

    private UsernamePasswordAuthenticationToken auth(FrontendUserRole role) {
        FrontendUserPrincipal principal = new FrontendUserPrincipal(70L, "test@example.com", "Max Mustermann", "", true, Set.of(role));
        return new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities());
    }
}
