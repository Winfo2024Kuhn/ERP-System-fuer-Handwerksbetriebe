package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class EinkaufBerechtigungServiceTest {

    @Mock
    private FrontendUserProfileRepository profileRepository;

    @InjectMocks
    private EinkaufBerechtigungService service;

    @Test
    void adminErhaeltAlleEinkaufsrechte() {
        FrontendUserProfile profile = profile(7L, Set.of(FrontendUserRole.ADMIN), Set.of());
        when(profileRepository.findById(7L)).thenReturn(Optional.of(profile));

        assertThat(service.rechte(authentication(7L, FrontendUserRole.USER)))
                .containsExactlyInAnyOrder(EinkaufBerechtigung.values());
    }

    @Test
    void userErhaeltNurAktuellGespeicherteRechteAusDemProfil() {
        FrontendUserProfile profile = profile(7L, Set.of(FrontendUserRole.USER),
                Set.of(EinkaufBerechtigung.LESEN));
        when(profileRepository.findById(7L)).thenReturn(Optional.of(profile));

        assertThat(service.rechte(authentication(7L, FrontendUserRole.ADMIN)))
                .containsExactly(EinkaufBerechtigung.LESEN);
    }

    @Test
    void fehlendeAuthentifizierungWirdAbgewiesen() {
        assertThatThrownBy(() -> service.rechte(null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deaktiviertesProfilWirdAuchBeiAlterSessionAbgewiesen() {
        FrontendUserProfile profile = profile(7L, Set.of(FrontendUserRole.ADMIN), Set.of());
        profile.setActive(false);
        when(profileRepository.findById(7L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.verlange(authentication(7L, FrontendUserRole.ADMIN),
                EinkaufBerechtigung.LESEN)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void benutzerOhneRechtWirdAbgewiesen() {
        FrontendUserProfile profile = profile(7L, Set.of(FrontendUserRole.USER), Set.of());
        when(profileRepository.findById(7L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.verlange(authentication(7L, FrontendUserRole.USER),
                EinkaufBerechtigung.LESEN)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void auditAkteurKommtAusPrincipalUndNichtAusAnfragewerten() {
        FrontendUserProfile profile = profile(7L, Set.of(FrontendUserRole.USER),
                Set.of(EinkaufBerechtigung.BEARBEITEN));
        when(profileRepository.findById(7L)).thenReturn(Optional.of(profile));

        assertThat(service.verlange(authentication(7L, FrontendUserRole.USER), EinkaufBerechtigung.BEARBEITEN))
                .isEqualTo(7L);
    }

    private static FrontendUserProfile profile(long id, Set<FrontendUserRole> roles,
            Set<EinkaufBerechtigung> permissions) {
        FrontendUserProfile profile = new FrontendUserProfile();
        profile.setId(id);
        profile.setRoleSet(new LinkedHashSet<>(roles));
        profile.setEinkaufBerechtigungen(permissions);
        return profile;
    }

    private static Authentication authentication(long id, FrontendUserRole staleRole) {
        FrontendUserPrincipal principal = new FrontendUserPrincipal(id, "test@example.com", "Max Mustermann",
                "{noop}secret", true, Set.of(staleRole));
        return new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities());
    }
}
