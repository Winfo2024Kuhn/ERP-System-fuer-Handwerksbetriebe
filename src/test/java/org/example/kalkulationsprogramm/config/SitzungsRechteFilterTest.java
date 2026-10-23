package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Issue #244: Bekommt ein angemeldeter Kollege Admin-Rechte, gelten sie ab der nächsten
 * Anfrage – ohne neue Anmeldung. Läuft durch die echten Security-Ketten mit einer echten
 * Sitzung; ein Probe-Controller ersetzt die Fach-Controller.
 */
@WebMvcTest(controllers = SitzungsRechteFilterTest.ProbeController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ZeiterfassungSecurityFilter.class))
@Import({SecurityConfig.class, SitzungsRechteFilterTest.ProbeController.class, SitzungsRechteFilterTest.FilterBeans.class})
class SitzungsRechteFilterTest {

    private static final String HANDY_TOKEN = "12345678-1234-4234-8234-123456789abc";
    private static final FrontendUserPrincipal MAX_ALS_BENUTZER = principal(FrontendUserRole.USER);
    private static final FrontendUserPrincipal MAX_ALS_ADMIN = principal(FrontendUserRole.ADMIN, FrontendUserRole.USER);

    @TestConfiguration
    static class FilterBeans {
        @Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
    }

    @RestController
    static class ProbeController {
        /** Nur Admins: liegt unter /api/frontend-users/** (Benutzerverwaltung). */
        @GetMapping("/api/frontend-users/sitzungs-probe")
        String benutzerverwaltung() { return "ok"; }

        /** Für die Handy-App freigegebene Route (MobileApiPolicy). */
        @GetMapping("/api/lieferanten/{id}")
        String lieferant() { return "handy"; }

        /** Rollen, die ein Controller aus der Anmeldung liest (z. B. Postfach-Sichtbarkeit). */
        @GetMapping({"/api/sitzungs-probe", "/api/lieferanten/sitzungs-probe"})
        String rollen(Authentication anmeldung) {
            return String.join(",", new TreeSet<>(FrontendUserPrincipal.aus(anmeldung).getRoles().stream()
                    .map(Enum::name).toList()));
        }
    }

    @Autowired MockMvc mvc;
    @MockitoBean FrontendUserDetailsService users;
    @MockitoBean MitarbeiterRepository mitarbeiter;

    @Test
    void neuesAdminRechtGiltOhneNeueAnmeldung() throws Exception {
        MockHttpSession sitzung = sitzungVon(MAX_ALS_BENUTZER);
        when(users.aktuellerStand(any())).thenReturn(Optional.of(MAX_ALS_ADMIN));

        mvc.perform(get("/api/frontend-users/sitzungs-probe").session(sitzung))
                .andExpect(status().isOk());

        // Die Sitzung hat den neuen Stand übernommen.
        assertThat(FrontendUserPrincipal.aus(gespeicherteAnmeldung(sitzung))).isSameAs(MAX_ALS_ADMIN);
    }

    @Test
    void neuesAdminRechtGiltAuchInDerGemeinsamenHandyKette() throws Exception {
        MockHttpSession sitzung = sitzungVon(MAX_ALS_BENUTZER);
        when(users.aktuellerStand(any())).thenReturn(Optional.of(MAX_ALS_ADMIN));

        mvc.perform(get("/api/lieferanten/sitzungs-probe").session(sitzung))
                .andExpect(status().isOk())
                .andExpect(content().string("ADMIN,USER"));
    }

    @Test
    void entzogenesAdminRechtGiltSofort() throws Exception {
        MockHttpSession sitzung = sitzungVon(MAX_ALS_ADMIN);
        when(users.aktuellerStand(any())).thenReturn(Optional.of(MAX_ALS_BENUTZER));

        mvc.perform(get("/api/frontend-users/sitzungs-probe").session(sitzung))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/sitzungs-probe").session(sitzung))
                .andExpect(status().isOk())
                .andExpect(content().string("USER"));
    }

    @Test
    void unveraenderteRechteBleibenWieSie() throws Exception {
        MockHttpSession sitzung = sitzungVon(MAX_ALS_ADMIN);
        when(users.aktuellerStand(any())).thenAnswer(aufruf -> Optional.of(aufruf.getArgument(0)));

        mvc.perform(get("/api/frontend-users/sitzungs-probe").session(sitzung))
                .andExpect(status().isOk());
        assertThat(FrontendUserPrincipal.aus(gespeicherteAnmeldung(sitzung))).isSameAs(MAX_ALS_ADMIN);
    }

    @Test
    void deaktivierterBenutzerWirdAbgemeldet() throws Exception {
        MockHttpSession sitzung = sitzungVon(MAX_ALS_ADMIN);
        when(users.aktuellerStand(any())).thenReturn(Optional.empty());

        mvc.perform(get("/api/sitzungs-probe").session(sitzung))
                .andExpect(status().isUnauthorized());
        assertThat(sitzung.isInvalid()).isTrue();
    }

    @Test
    void handyAnmeldungPerTokenBleibtUnberuehrt() throws Exception {
        Mitarbeiter max = new Mitarbeiter();
        max.setId(9L);
        max.setAktiv(true);
        when(mitarbeiter.findByLoginTokenAndAktivTrue(HANDY_TOKEN)).thenReturn(Optional.of(max));

        mvc.perform(get("/api/lieferanten/5").header("X-Auth-Token", HANDY_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string("handy"));
        verifyNoInteractions(users);
    }

    @Test
    void ohneAnmeldungBleibtEs401() throws Exception {
        mvc.perform(get("/api/sitzungs-probe")).andExpect(status().isUnauthorized());
    }

    private static MockHttpSession sitzungVon(FrontendUserPrincipal principal) {
        MockHttpSession sitzung = new MockHttpSession();
        SecurityContext kontext = new SecurityContextImpl(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        sitzung.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, kontext);
        return sitzung;
    }

    private static Authentication gespeicherteAnmeldung(MockHttpSession sitzung) {
        var kontext = (SecurityContext) sitzung.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        return kontext.getAuthentication();
    }

    private static FrontendUserPrincipal principal(FrontendUserRole... rollen) {
        return new FrontendUserPrincipal(7L, "max.mustermann", "Max Mustermann", "hash", true, Set.of(rollen));
    }
}
