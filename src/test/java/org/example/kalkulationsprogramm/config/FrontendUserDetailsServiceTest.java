package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class FrontendUserDetailsServiceTest {

    private static final FrontendUserPrincipal MAX_ALS_BENUTZER = new FrontendUserPrincipal(
            7L, "max.mustermann", "Max Mustermann", "hash", true, Set.of(FrontendUserRole.USER));

    @Mock FrontendUserProfileRepository repository;
    @InjectMocks FrontendUserDetailsService service;

    @Test
    void anmeldungOhneRolleWirdNormalerBenutzer() {
        FrontendUserProfile profil = profil("hash");
        profil.setRoleSet(Set.of());
        when(repository.findByUsernameIgnoreCase("max.mustermann")).thenReturn(Optional.of(profil));

        var principal = (FrontendUserPrincipal) service.loadUserByUsername("max.mustermann");

        assertThat(principal.getRoles()).containsExactly(FrontendUserRole.USER);
        assertThat(principal.getDisplayName()).isEqualTo("Max Mustermann");
    }

    @Test
    void anmeldungOhnePasswortAbgelehnt() {
        when(repository.findByUsernameIgnoreCase("max.mustermann")).thenReturn(Optional.of(profil(" ")));

        assertThatThrownBy(() -> service.loadUserByUsername("max.mustermann"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void unbekannterBenutzerAbgelehnt() {
        when(repository.findByUsernameIgnoreCase("niemand")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("niemand"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void unveraenderteRechteLiefernDenselbenPrincipal() {
        when(repository.findAktivById(7L)).thenReturn(Optional.of(true));
        when(repository.findRollenById(7L)).thenReturn(Set.of(FrontendUserRole.USER));

        assertThat(service.aktuellerStand(MAX_ALS_BENUTZER)).containsSame(MAX_ALS_BENUTZER);
    }

    @Test
    void neuesAdminRechtGiltSofort() {
        when(repository.findAktivById(7L)).thenReturn(Optional.of(true));
        when(repository.findRollenById(7L)).thenReturn(Set.of(FrontendUserRole.ADMIN, FrontendUserRole.USER));

        FrontendUserPrincipal aktuell = service.aktuellerStand(MAX_ALS_BENUTZER).orElseThrow();

        assertThat(aktuell).isNotSameAs(MAX_ALS_BENUTZER);
        assertThat(aktuell.hasRole(FrontendUserRole.ADMIN)).isTrue();
        assertThat(aktuell.getAuthorities()).extracting(Object::toString).contains("ROLE_ADMIN");
        assertThat(aktuell.getId()).isEqualTo(7L);
        assertThat(aktuell.getUsername()).isEqualTo("max.mustermann");
        assertThat(aktuell.getDisplayName()).isEqualTo("Max Mustermann");
        assertThat(aktuell.getPassword()).isEqualTo("hash");
        assertThat(aktuell.isEnabled()).isTrue();
    }

    @Test
    void ohneEingetrageneRolleBleibtEsBeimNormalenBenutzer() {
        when(repository.findAktivById(7L)).thenReturn(Optional.of(true));
        when(repository.findRollenById(7L)).thenReturn(Set.of());

        assertThat(service.aktuellerStand(MAX_ALS_BENUTZER)).containsSame(MAX_ALS_BENUTZER);
    }

    @Test
    void deaktivierterBenutzerVerliertDieAnmeldung() {
        when(repository.findAktivById(7L)).thenReturn(Optional.of(false));

        assertThat(service.aktuellerStand(MAX_ALS_BENUTZER)).isEmpty();
        verify(repository, never()).findRollenById(7L);
    }

    @Test
    void geloeschterBenutzerVerliertDieAnmeldung() {
        when(repository.findAktivById(7L)).thenReturn(Optional.empty());

        assertThat(service.aktuellerStand(MAX_ALS_BENUTZER)).isEmpty();
    }

    private static FrontendUserProfile profil(String passwortHash) {
        FrontendUserProfile profil = new FrontendUserProfile();
        profil.setId(7L);
        profil.setUsername("max.mustermann");
        profil.setDisplayName("Max Mustermann");
        profil.setPasswordHash(passwortHash);
        profil.setActive(true);
        return profil;
    }
}
