package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.config.MobilePrincipal;
import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/** Wer sieht am PC den Menü-Bereich „Zeiterfassung“? Dummy-Daten: Max Mustermann. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZeiterfassungSichtbarkeitServiceTest {

    @Mock FrontendUserProfileRepository profiles;
    @InjectMocks ZeiterfassungSichtbarkeitService service;

    private Mitarbeiter mitarbeiter(boolean abteilungsRecht, boolean gf) {
        Abteilung ohne = new Abteilung();
        ohne.setDarfZeiterfassungSehen(false);
        Abteilung mit = new Abteilung();
        mit.setDarfZeiterfassungSehen(abteilungsRecht);
        Mitarbeiter m = new Mitarbeiter();
        m.setId(9L);
        m.setVorname("Max");
        m.setNachname("Mustermann");
        m.setAktiv(true);
        m.setArt(MitarbeiterArt.MENSCH);
        m.setIstGeschaeftsfuehrer(gf);
        m.setAbteilungen(Set.of(ohne, mit));
        return m;
    }

    private FrontendUserProfile profil(Mitarbeiter m, boolean aktiv) {
        FrontendUserProfile p = new FrontendUserProfile();
        p.setId(70L);
        p.setActive(aktiv);
        p.setMitarbeiter(m);
        when(profiles.findById(70L)).thenReturn(Optional.of(p));
        return p;
    }

    private static UsernamePasswordAuthenticationToken pc(FrontendUserRole... rollen) {
        var principal = new FrontendUserPrincipal(70L, "max@example.com", "Max Mustermann", "", true, Set.of(rollen));
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    @Test
    @DisplayName("Admin sieht die Zeiterfassung immer, ohne Profil nachzuschlagen")
    void admin() {
        assertThat(service.darfZeiterfassungSehen(pc(FrontendUserRole.ADMIN))).isTrue();
        verifyNoInteractions(profiles);
    }

    @Test
    @DisplayName("Recht über eine von mehreren Abteilungen reicht")
    void abteilungsRecht() {
        profil(mitarbeiter(true, false), true);
        assertThat(service.darfZeiterfassungSehen(pc(FrontendUserRole.USER))).isTrue();
    }

    @Test
    @DisplayName("Geschäftsführung sieht sie auch ohne Abteilungsrecht")
    void geschaeftsfuehrung() {
        profil(mitarbeiter(false, true), true);
        assertThat(service.darfZeiterfassungSehen(pc(FrontendUserRole.USER))).isTrue();
    }

    @Test
    @DisplayName("Kein Recht: kein Haken, Profil inaktiv, Mitarbeiter inaktiv oder System, ohne Mitarbeiter, ohne Profil")
    void ohneRecht() {
        profil(mitarbeiter(false, false), true);
        assertThat(service.darfZeiterfassungSehen(pc(FrontendUserRole.USER))).isFalse();

        profil(mitarbeiter(true, false), false);
        assertThat(service.darfZeiterfassungSehen(pc(FrontendUserRole.USER))).isFalse();

        Mitarbeiter inaktiv = mitarbeiter(true, false);
        inaktiv.setAktiv(false);
        profil(inaktiv, true);
        assertThat(service.darfZeiterfassungSehen(pc(FrontendUserRole.USER))).isFalse();

        Mitarbeiter system = mitarbeiter(true, false);
        system.setArt(MitarbeiterArt.SYSTEM);
        profil(system, true);
        assertThat(service.darfZeiterfassungSehen(pc(FrontendUserRole.USER))).isFalse();

        profil(null, true);
        assertThat(service.darfZeiterfassungSehen(pc(FrontendUserRole.USER))).isFalse();

        when(profiles.findById(70L)).thenReturn(Optional.empty());
        assertThat(service.darfZeiterfassungSehen(pc(FrontendUserRole.USER))).isFalse();
    }

    @Test
    @DisplayName("Ohne Anmeldung, mit Handy-Token, deaktiviertem Zugang oder ohne Profil-ID: nicht sichtbar")
    void ohneGueltigenPcZugang() {
        assertThat(service.darfZeiterfassungSehen(null)).isFalse();
        assertThat(service.darfZeiterfassungSehen(
                UsernamePasswordAuthenticationToken.authenticated(new MobilePrincipal(9L), null, List.of()))).isFalse();
        assertThat(service.darfZeiterfassungSehen(
                UsernamePasswordAuthenticationToken.unauthenticated(pc().getPrincipal(), null))).isFalse();

        var deaktiviert = new FrontendUserPrincipal(70L, "max@example.com", "Max Mustermann", "", false, Set.of());
        assertThat(service.darfZeiterfassungSehen(
                UsernamePasswordAuthenticationToken.authenticated(deaktiviert, null, List.of()))).isFalse();

        var ohneId = new FrontendUserPrincipal(null, "max@example.com", "Max Mustermann", "", true, Set.of());
        assertThat(service.darfZeiterfassungSehen(
                UsernamePasswordAuthenticationToken.authenticated(ohneId, null, List.of()))).isFalse();
    }
}
