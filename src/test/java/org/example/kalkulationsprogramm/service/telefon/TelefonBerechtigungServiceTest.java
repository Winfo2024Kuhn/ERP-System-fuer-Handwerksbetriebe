package org.example.kalkulationsprogramm.service.telefon;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.MitarbeiterArt;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelefonBerechtigungServiceTest {

    @Mock FrontendUserProfileRepository profiles;
    @InjectMocks TelefonBerechtigungService service;

    private FrontendUserProfile profil(boolean recht, boolean profilAktiv, boolean mitarbeiterAktiv, MitarbeiterArt art) {
        Abteilung ohne = new Abteilung();
        Abteilung mit = new Abteilung();
        mit.setDarfTelefonSehen(recht);
        Mitarbeiter m = new Mitarbeiter();
        m.setId(9L);
        m.setAktiv(mitarbeiterAktiv);
        m.setArt(art);
        m.setAbteilungen(Set.of(ohne, mit));
        FrontendUserProfile p = new FrontendUserProfile();
        p.setId(70L);
        p.setActive(profilAktiv);
        p.setMitarbeiter(m);
        when(profiles.findById(70L)).thenReturn(Optional.of(p));
        return p;
    }

    private static UsernamePasswordAuthenticationToken auth() {
        var principal = new FrontendUserPrincipal(70L, "max@example.com", "Max Mustermann", "", true, Set.of());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
    }

    @Test
    @DisplayName("Recht über eine der Abteilungen")
    void mitRecht() {
        FrontendUserProfile p = profil(true, true, true, MitarbeiterArt.MENSCH);
        assertThat(service.darfTelefonSehen(auth())).isTrue();
        assertThat(service.darfTelefonSehen(70L)).isTrue();
        assertThat(service.verlange(auth())).isSameAs(p);
    }

    @Test
    @DisplayName("Kein Recht: Flag aus, Profil inaktiv, Mitarbeiter inaktiv, System-Mitarbeiter, ohne Mitarbeiter")
    void ohneRecht() {
        profil(false, true, true, MitarbeiterArt.MENSCH);
        assertThat(service.darfTelefonSehen(auth())).isFalse();
        assertThatThrownBy(() -> service.verlange(auth())).isInstanceOf(AccessDeniedException.class);
        profil(true, false, true, MitarbeiterArt.MENSCH);
        assertThat(service.darfTelefonSehen(auth())).isFalse();
        profil(true, true, false, MitarbeiterArt.MENSCH);
        assertThat(service.darfTelefonSehen(auth())).isFalse();
        profil(true, true, true, MitarbeiterArt.SYSTEM);
        assertThat(service.darfTelefonSehen(auth())).isFalse();
        profil(true, true, true, MitarbeiterArt.MENSCH).setMitarbeiter(null);
        assertThat(service.darfTelefonSehen(auth())).isFalse();
    }

    @Test
    @DisplayName("Ohne Anmeldung oder fremder Principal-Typ → kein Recht")
    void ohneAnmeldung() {
        assertThat(service.darfTelefonSehen((org.springframework.security.core.Authentication) null)).isFalse();
        assertThat(service.darfTelefonSehen(UsernamePasswordAuthenticationToken.authenticated("admin", null, List.of()))).isFalse();
        assertThat(service.darfTelefonSehen(UsernamePasswordAuthenticationToken.unauthenticated("x", null))).isFalse();
        assertThat(service.darfTelefonSehen((Long) null)).isFalse();
        when(profiles.findById(5L)).thenReturn(Optional.empty());
        assertThat(service.darfTelefonSehen(5L)).isFalse();
    }
}
