package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Test
    void herabgestufterAdminKannMitAlterAdminSessionKeineProfilrechteMehrLesenOderSetzen() {
        FrontendUserProfile aktuellesProfil = profile(7L, Set.of(FrontendUserRole.USER), Set.of());
        when(profileRepository.findById(7L)).thenReturn(Optional.of(aktuellesProfil));
        Authentication alteAdminSession = authentication(7L, FrontendUserRole.ADMIN);

        assertThatThrownBy(() -> service.profilRechte(alteAdminSession, 8L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.setzeRechte(alteAdminSession, 8L, Set.of(EinkaufBerechtigung.LESEN)))
                .isInstanceOf(AccessDeniedException.class);
        verify(profileRepository, never()).save(aktuellesProfil);
    }

    @Test
    void deaktivierterAdminKannMitAlterSessionKeineProfilrechteMehrLesenOderSetzen() {
        FrontendUserProfile aktuellesProfil = profile(7L, Set.of(FrontendUserRole.ADMIN), Set.of());
        aktuellesProfil.setActive(false);
        when(profileRepository.findById(7L)).thenReturn(Optional.of(aktuellesProfil));
        Authentication alteAdminSession = authentication(7L, FrontendUserRole.ADMIN);

        assertThatThrownBy(() -> service.profilRechte(alteAdminSession, 8L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.setzeRechte(alteAdminSession, 8L, Set.of(EinkaufBerechtigung.LESEN)))
                .isInstanceOf(AccessDeniedException.class);
        verify(profileRepository, never()).save(aktuellesProfil);
    }

    @Test
    void aktuellerAktiverAdminDarfAndereProfileRechteLesenUndSetzen() {
        FrontendUserProfile aktuellesProfil = profile(7L, Set.of(FrontendUserRole.ADMIN), Set.of());
        FrontendUserProfile zielProfil = profile(8L, Set.of(FrontendUserRole.USER), Set.of());
        when(profileRepository.findById(7L)).thenReturn(Optional.of(aktuellesProfil));
        when(profileRepository.findById(8L)).thenReturn(Optional.of(zielProfil));
        when(profileRepository.save(zielProfil)).thenReturn(zielProfil);
        Authentication aktuelleAdminSession = authentication(7L, FrontendUserRole.ADMIN);

        assertThat(service.profilRechte(aktuelleAdminSession, 8L)).isEmpty();
        assertThat(service.setzeRechte(aktuelleAdminSession, 8L, Set.of(EinkaufBerechtigung.LESEN)))
                .containsExactly(EinkaufBerechtigung.LESEN);
        assertThat(zielProfil.getEinkaufBerechtigungen()).containsExactly(EinkaufBerechtigung.LESEN);
        verify(profileRepository).save(zielProfil);
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
