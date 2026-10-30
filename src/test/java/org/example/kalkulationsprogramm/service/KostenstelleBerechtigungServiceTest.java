package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
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
class KostenstelleBerechtigungServiceTest {

    private final FrontendUserProfileRepository profile = mock(FrontendUserProfileRepository.class);
    private final KostenstelleBerechtigungService service =
            new KostenstelleBerechtigungService(new AbteilungsRechtPruefung(profile));

    private static Authentication anmeldung(Long id, boolean admin) {
        FrontendUserPrincipal principal = mock(FrontendUserPrincipal.class);
        when(principal.getId()).thenReturn(id);
        when(principal.isEnabled()).thenReturn(true);
        when(principal.hasRole(FrontendUserRole.ADMIN)).thenReturn(admin);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    private static FrontendUserProfile profil(boolean kostenstellenRecht, boolean aufschlagRecht) {
        Abteilung a = new Abteilung();
        a.setDarfKostenstellenVerwalten(kostenstellenRecht);
        a.setDarfAufschlaegeAendern(aufschlagRecht);
        Mitarbeiter m = new Mitarbeiter();
        m.setId(5L);
        m.setArt(MitarbeiterArt.MENSCH);
        m.setAktiv(true);
        m.setAbteilungen(Set.of(a));
        FrontendUserProfile p = new FrontendUserProfile();
        p.setActive(true);
        p.setMitarbeiter(m);
        return p;
    }

    @Test
    void administratorenDuerfenImmer() {
        assertThat(service.darfVerwalten(anmeldung(1L, true))).isTrue();
    }

    @Test
    void abteilungMitRechtDarf() {
        when(profile.findById(2L)).thenReturn(Optional.of(profil(true, false)));

        assertThat(service.darfVerwalten(anmeldung(2L, false))).isTrue();
    }

    @Test
    void dasAufschlagRechtReichtNicht() {
        when(profile.findById(3L)).thenReturn(Optional.of(profil(false, true)));

        assertThat(service.darfVerwalten(anmeldung(3L, false))).isFalse();
        assertThatThrownBy(() -> service.verlange(anmeldung(3L, false)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Du darfst Kostenstellen nicht verwalten.");
    }

    @Test
    void ohneAnmeldungDarfNiemand() {
        assertThat(service.darfVerwalten(null)).isFalse();
    }
}
