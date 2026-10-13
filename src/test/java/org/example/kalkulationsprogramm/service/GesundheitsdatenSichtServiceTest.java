package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.config.MobilePrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Wer sieht „Krankheit" im Team-Kalender (Issue #268)? Admins und Geschäftsführung
 * alles, alle anderen nur ihre eigenen Einträge. Dummy-Daten: Max Mustermann.
 */
@ExtendWith(MockitoExtension.class)
class GesundheitsdatenSichtServiceTest {

    @Mock
    private FrontendUserProfileRepository profileRepository;
    @Mock
    private MitarbeiterRepository mitarbeiterRepository;

    @Mock
    private ZeiterfassungSichtbarkeitService zeiterfassungSichtbarkeitService;
    @InjectMocks
    private GesundheitsdatenSichtService service;

    private static Mitarbeiter mitarbeiter(long id, boolean gf) {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(id);
        m.setVorname("Max");
        m.setNachname("Mustermann");
        m.setIstGeschaeftsfuehrer(gf);
        return m;
    }

    private static Authentication pc(long profilId, FrontendUserRole rolle) {
        FrontendUserPrincipal p = new FrontendUserPrincipal(profilId, "max", "Max Mustermann", "x", true, Set.of(rolle));
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    private void profilMit(long profilId, Mitarbeiter m) {
        FrontendUserProfile profil = new FrontendUserProfile();
        profil.setMitarbeiter(m);
        when(profileRepository.findById(profilId)).thenReturn(Optional.of(profil));
    }

    @Test
    void admin_siehtAlleGruende() {
        when(zeiterfassungSichtbarkeitService.darfZeiterfassungSehen(any())).thenReturn(true);
        GesundheitsdatenSichtService.Sicht sicht = service.sichtFuer(pc(5L, FrontendUserRole.ADMIN));

        assertTrue(sicht.darfAlleGruendeSehen());
        assertTrue(sicht.darfGrundSehen(99L));
    }

    @Test
    void mitZeiterfassungsRechtAmPc_siehtAlleGruende() {
        // Recht über Geschäftsführung oder Abteilung entscheidet ZeiterfassungSichtbarkeitService.
        when(zeiterfassungSichtbarkeitService.darfZeiterfassungSehen(any())).thenReturn(true);
        profilMit(5L, mitarbeiter(7L, false));

        GesundheitsdatenSichtService.Sicht sicht = service.sichtFuer(pc(5L, FrontendUserRole.USER));

        assertTrue(sicht.darfGrundSehen(99L));
        assertEquals(7L, sicht.eigeneMitarbeiterId());
    }

    @Test
    void ohneZeiterfassungsRechtAmPc_siehtNurEigenenGrund() {
        when(zeiterfassungSichtbarkeitService.darfZeiterfassungSehen(any())).thenReturn(false);
        profilMit(5L, mitarbeiter(7L, true));

        GesundheitsdatenSichtService.Sicht sicht = service.sichtFuer(pc(5L, FrontendUserRole.USER));

        assertFalse(sicht.darfAlleGruendeSehen());
        assertTrue(sicht.darfGrundSehen(7L));
        assertFalse(sicht.darfGrundSehen(99L));
    }

    @Test
    void pcBenutzerOhneVerknuepftenMitarbeiter_siehtKeineGruende() {
        when(profileRepository.findById(5L)).thenReturn(Optional.empty());

        GesundheitsdatenSichtService.Sicht sicht = service.sichtFuer(pc(5L, FrontendUserRole.USER));

        assertNull(sicht.eigeneMitarbeiterId());
        assertFalse(sicht.darfGrundSehen(7L));
    }

    @Test
    void handy_mitarbeiterSiehtNurEigenen_gfSiehtAlle() {
        when(mitarbeiterRepository.findById(7L)).thenReturn(Optional.of(mitarbeiter(7L, false)));
        when(mitarbeiterRepository.findById(8L)).thenReturn(Optional.of(mitarbeiter(8L, true)));

        GesundheitsdatenSichtService.Sicht mitarbeiterSicht = service.sichtFuer(
                new UsernamePasswordAuthenticationToken(new MobilePrincipal(7L), null, Set.of()));
        GesundheitsdatenSichtService.Sicht gfSicht = service.sichtFuer(
                new UsernamePasswordAuthenticationToken(new MobilePrincipal(8L), null, Set.of()));

        assertTrue(mitarbeiterSicht.darfGrundSehen(7L));
        assertFalse(mitarbeiterSicht.darfGrundSehen(8L));
        assertTrue(gfSicht.darfGrundSehen(7L));
    }

    @Test
    void ohneAnmeldungOderFremderPrincipal_siehtKeineGruende() {
        assertFalse(service.sichtFuer(null).darfGrundSehen(7L));
        assertFalse(service.sichtFuer(new UsernamePasswordAuthenticationToken("anonym", null)).darfGrundSehen(7L));
    }
}
