package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/** Zugehörigkeit (Admin oder Abteilungen) des Angemeldeten. Nur Dummy-Daten. */
class AbteilungsRechtPruefungTest {

    private final FrontendUserProfileRepository profile = mock(FrontendUserProfileRepository.class);
    private final AbteilungsRechtPruefung pruefung = new AbteilungsRechtPruefung(profile);

    private static Authentication anmeldung(Long id, boolean admin) {
        FrontendUserPrincipal principal = mock(FrontendUserPrincipal.class);
        when(principal.getId()).thenReturn(id);
        when(principal.isEnabled()).thenReturn(true);
        when(principal.hasRole(FrontendUserRole.ADMIN)).thenReturn(admin);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    private static Abteilung abteilung(long id, String name) {
        Abteilung a = new Abteilung();
        a.setId(id);
        a.setName(name);
        return a;
    }

    private static FrontendUserProfile profil(boolean aktiv, MitarbeiterArt art, boolean mitarbeiterAktiv,
            Abteilung... abteilungen) {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(5L);
        m.setVorname("Max");
        m.setNachname("Mustermann");
        m.setArt(art);
        m.setAktiv(mitarbeiterAktiv);
        m.setAbteilungen(Set.of(abteilungen));
        FrontendUserProfile p = new FrontendUserProfile();
        p.setActive(aktiv);
        p.setMitarbeiter(m);
        return p;
    }

    @Test
    @DisplayName("Admin: admin=true, keine Abteilungen nötig")
    void admin() {
        AbteilungsRechtPruefung.Zugehoerigkeit z = pruefung.zugehoerigkeit(anmeldung(1L, true));

        assertThat(z.admin()).isTrue();
        assertThat(z.abteilungIds()).isEmpty();
    }

    @Test
    @DisplayName("Aktiver Mitarbeiter: die IDs aller seiner Abteilungen")
    void abteilungen() {
        when(profile.findById(2L)).thenReturn(Optional.of(
                profil(true, MitarbeiterArt.MENSCH, true, abteilung(10, "Büro"), abteilung(11, "Werkstatt"))));

        AbteilungsRechtPruefung.Zugehoerigkeit z = pruefung.zugehoerigkeit(anmeldung(2L, false));

        assertThat(z.admin()).isFalse();
        assertThat(z.abteilungIds()).containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    @DisplayName("Inaktives Profil, inaktiver Mitarbeiter, kein Mensch, ohne Mitarbeiter oder ohne Anmeldung: keine Abteilungen")
    void keineAbteilungen() {
        Abteilung buero = abteilung(10, "Büro");
        when(profile.findById(3L)).thenReturn(Optional.of(profil(false, MitarbeiterArt.MENSCH, true, buero)));
        when(profile.findById(4L)).thenReturn(Optional.of(profil(true, MitarbeiterArt.MENSCH, false, buero)));
        FrontendUserProfile ohneMitarbeiter = new FrontendUserProfile();
        ohneMitarbeiter.setActive(true);
        when(profile.findById(5L)).thenReturn(Optional.of(ohneMitarbeiter));
        when(profile.findById(6L)).thenReturn(Optional.empty());

        for (long id : new long[] { 3L, 4L, 5L, 6L }) {
            AbteilungsRechtPruefung.Zugehoerigkeit z = pruefung.zugehoerigkeit(anmeldung(id, false));
            assertThat(z.admin()).isFalse();
            assertThat(z.abteilungIds()).as("Profil %d", id).isEmpty();
        }
        assertThat(pruefung.zugehoerigkeit(null)).isEqualTo(new AbteilungsRechtPruefung.Zugehoerigkeit(false, Set.of()));
        assertThat(pruefung.zugehoerigkeit(anmeldung(null, false)).abteilungIds()).isEmpty();
    }

    @Test
    @DisplayName("Die bestehende Rechteprüfung bleibt wie gehabt: Admin immer, sonst eine Abteilung mit dem Recht")
    void adminOderAbteilungDarf() {
        Abteilung buero = abteilung(10, "Büro");
        buero.setDarfKostenstellenVerwalten(true);
        when(profile.findById(2L)).thenReturn(Optional.of(profil(true, MitarbeiterArt.MENSCH, true, buero)));
        when(profile.findById(3L)).thenReturn(Optional.of(profil(true, MitarbeiterArt.MENSCH, false, buero)));

        assertThat(pruefung.adminOderAbteilungDarf(anmeldung(1L, true), a -> false)).isTrue();
        assertThat(pruefung.adminOderAbteilungDarf(anmeldung(2L, false), Abteilung::getDarfKostenstellenVerwalten))
                .isTrue();
        assertThat(pruefung.adminOderAbteilungDarf(anmeldung(3L, false), Abteilung::getDarfKostenstellenVerwalten))
                .isFalse();
        assertThat(pruefung.adminOderAbteilungDarf(null, a -> true)).isFalse();
    }
}
