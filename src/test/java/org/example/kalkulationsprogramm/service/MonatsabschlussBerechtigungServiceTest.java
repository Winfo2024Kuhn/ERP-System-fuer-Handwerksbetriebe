package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonatsabschlussBerechtigungServiceTest {
    @Mock FrontendUserProfileRepository profiles;
    @InjectMocks MonatsabschlussBerechtigungService service;

    private UsernamePasswordAuthenticationToken session() {
        var principal = new FrontendUserPrincipal(70L, "test@example.com", "Max Mustermann", "", true, Set.of(FrontendUserRole.ADMIN));
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
    private Mitarbeiter employee(boolean allowed) {
        var m = new Mitarbeiter(); m.setId(9L);
        var abteilung = new Abteilung(); abteilung.setDarfMonatAbschliessen(allowed);
        m.setAbteilungen(Set.of(abteilung));
        var p = new FrontendUserProfile(); p.setId(70L); p.setMitarbeiter(m);
        when(profiles.findById(70L)).thenReturn(Optional.of(p));
        return m;
    }
    @Test void administratorOhneFlagHatKeinenBypass() {
        employee(false);
        assertThatThrownBy(() -> service.verlangeAkteur(session())).isInstanceOf(AccessDeniedException.class);
    }
    @Test void akteurIstMitarbeiterDesProfilsNichtPrincipalId() {
        var m = employee(true);
        assertThat(service.verlangeAkteur(session())).isSameAs(m);
        verify(profiles).findById(70L);
    }
    @Test void beliebigerPrincipalAuchMitAdminAuthorityIstKeinSessionProfil() {
        var fake = UsernamePasswordAuthenticationToken.authenticated("9", null, session().getAuthorities());
        assertThat(service.darfMonatAbschliessen(fake)).isFalse();
        assertThat(service.darfMonatAbschliessen(null)).isFalse();
        verifyNoInteractions(profiles);
    }
    @Test void inaktiverMenschUndSystemSindAbgewiesen() {
        var m = employee(true); m.setAktiv(false);
        assertThat(service.darfMonatAbschliessen(session())).isFalse();
        m.setAktiv(true); m.setArt(MitarbeiterArt.SYSTEM);
        assertThat(service.darfMonatAbschliessen(session())).isFalse();
    }
    @Test void inaktivesOderFehlendesProfilIstAbgewiesen() {
        var p = new FrontendUserProfile(); p.setActive(false);
        when(profiles.findById(70L)).thenReturn(Optional.of(p), Optional.empty());
        assertThat(service.darfMonatAbschliessen(session())).isFalse();
        assertThat(service.darfMonatAbschliessen(session())).isFalse();
    }
    @Test void irgendeineAbteilungMitFlagGenuegt() {
        var m = employee(false); var ja = new Abteilung(); ja.setDarfMonatAbschliessen(true);
        m.setAbteilungen(new HashSet<>(m.getAbteilungen())); m.getAbteilungen().add(ja);
        assertThat(service.darfMonatAbschliessen(session())).isTrue();
    }
}
