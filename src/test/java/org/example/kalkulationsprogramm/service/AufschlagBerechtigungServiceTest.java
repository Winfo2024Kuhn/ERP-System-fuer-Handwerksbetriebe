package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.MitarbeiterArt;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/** Nur Dummy-Daten. */
class AufschlagBerechtigungServiceTest {

    private final FrontendUserProfileRepository profile = mock(FrontendUserProfileRepository.class);
    private final AufschlagBerechtigungService service = new AufschlagBerechtigungService(profile);

    private static Authentication anmeldung(Long id, boolean admin, boolean aktiv) {
        FrontendUserPrincipal principal = mock(FrontendUserPrincipal.class);
        when(principal.getId()).thenReturn(id);
        when(principal.isEnabled()).thenReturn(aktiv);
        when(principal.hasRole(FrontendUserRole.ADMIN)).thenReturn(admin);
        return new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of());
    }

    private static FrontendUserProfile profil(boolean aktiv, Mitarbeiter m) {
        FrontendUserProfile p = new FrontendUserProfile();
        p.setActive(aktiv);
        p.setMitarbeiter(m);
        return p;
    }

    private static Mitarbeiter mitarbeiter(boolean abteilungMitRecht) {
        Abteilung a = new Abteilung();
        a.setDarfAufschlaegeAendern(abteilungMitRecht);
        Mitarbeiter m = new Mitarbeiter();
        m.setId(5L);
        m.setArt(MitarbeiterArt.MENSCH);
        m.setAktiv(true);
        m.setAbteilungen(Set.of(a));
        return m;
    }

    @Test
    void administratorenDuerfenImmer() {
        assertThat(service.darfAendern(anmeldung(1L, true, true))).isTrue();
    }

    @Test
    void einBenutzerAusEinerAbteilungMitRechtDarf() {
        when(profile.findById(2L)).thenReturn(Optional.of(profil(true, mitarbeiter(true))));

        assertThat(service.darfAendern(anmeldung(2L, false, true))).isTrue();
    }

    @Test
    void ohneRechtInDerAbteilungOhneMitarbeiterOderMitInaktivemProfilDarfNiemand() {
        when(profile.findById(2L)).thenReturn(Optional.of(profil(true, mitarbeiter(false))));
        when(profile.findById(3L)).thenReturn(Optional.of(profil(true, null)));
        when(profile.findById(4L)).thenReturn(Optional.of(profil(false, mitarbeiter(true))));
        when(profile.findById(6L)).thenReturn(Optional.empty());

        assertThat(service.darfAendern(anmeldung(2L, false, true))).as("Abteilung ohne Recht").isFalse();
        assertThat(service.darfAendern(anmeldung(3L, false, true))).as("kein Mitarbeiter verknüpft").isFalse();
        assertThat(service.darfAendern(anmeldung(4L, false, true))).as("Profil nicht aktiv").isFalse();
        assertThat(service.darfAendern(anmeldung(6L, false, true))).as("Profil unbekannt").isFalse();
        assertThat(service.darfAendern(anmeldung(null, false, true))).as("ohne ID").isFalse();
    }

    @Test
    void einDeaktivierterAdministratorDarfNicht() {
        assertThat(service.darfAendern(anmeldung(1L, true, false))).isFalse();
    }

    @Test
    void ohneAnmeldungOderMitFremdemPrincipalDarfNiemand() {
        assertThat(service.darfAendern(null)).isFalse();
        assertThat(service.darfAendern(new UsernamePasswordAuthenticationToken("anonym", null))).isFalse();
    }

    @Test
    void verlangeWirftBeiFehlendemRecht403() {
        assertThatThrownBy(() -> service.verlange(anmeldung(6L, false, true))).isInstanceOf(AccessDeniedException.class).hasMessageContaining("Aufschläge");
        service.verlange(anmeldung(1L, true, true));
    }
}
