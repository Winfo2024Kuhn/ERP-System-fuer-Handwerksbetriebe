package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AngemeldeterMitarbeiterServiceTest {

    @Mock
    private FrontendUserProfileRepository profilRepository;

    @InjectMocks
    private AngemeldeterMitarbeiterService service;

    private static UsernamePasswordAuthenticationToken anmeldung(long profilId) {
        FrontendUserPrincipal p = new FrontendUserPrincipal(
                profilId, "max.mustermann", "Max Mustermann", "hash", true, Set.of(FrontendUserRole.ADMIN));
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    @Test
    void liefertZugeordnetenMitarbeiter() {
        Mitarbeiter max = new Mitarbeiter();
        max.setId(7L);
        FrontendUserProfile profil = new FrontendUserProfile();
        profil.setId(42L);
        profil.setMitarbeiter(max);
        when(profilRepository.findById(42L)).thenReturn(Optional.of(profil));

        assertThat(service.bearbeiter(anmeldung(42L))).isSameAs(max);
    }

    @Test
    void ohneAnmeldung401() {
        assertThatThrownBy(() -> service.bearbeiter(null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verifyNoInteractions(profilRepository);
    }

    @Test
    void profilOhneMitarbeiter403MitHinweis() {
        FrontendUserProfile profil = new FrontendUserProfile();
        profil.setId(42L);
        when(profilRepository.findById(42L)).thenReturn(Optional.of(profil));

        assertThatThrownBy(() -> service.bearbeiter(anmeldung(42L)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getReason()).isEqualTo(AngemeldeterMitarbeiterService.KEIN_MITARBEITER);
                });
    }

    @Test
    void geloeschtesProfilBeiNochGueltigerSitzung401() {
        when(profilRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bearbeiter(anmeldung(42L)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
