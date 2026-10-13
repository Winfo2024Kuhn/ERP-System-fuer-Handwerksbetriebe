package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.service.ZeiterfassungSichtbarkeitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ResponseStatusException;

/** Backend-Sperre der Zeiterfassung am PC (Issue #268). Dummy-Daten: Max Mustermann. */
class ZeiterfassungZugriffInterceptorTest {

    private final ZeiterfassungSichtbarkeitService service = mock(ZeiterfassungSichtbarkeitService.class);

    @SuppressWarnings("unchecked")
    private ZeiterfassungZugriffInterceptor interceptor(ZeiterfassungSichtbarkeitService s) {
        ObjectProvider<ZeiterfassungSichtbarkeitService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(s);
        return new ZeiterfassungZugriffInterceptor(provider);
    }

    private static void anmelden(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    private static FrontendUserPrincipal pc() {
        return new FrontendUserPrincipal(70L, "max@example.com", "Max Mustermann", "", true, Set.of(FrontendUserRole.USER));
    }

    private boolean aufruf(ZeiterfassungZugriffInterceptor i) {
        return i.preHandle(new MockHttpServletRequest("GET", "/api/urlaub/antraege"), new MockHttpServletResponse(), new Object());
    }

    @AfterEach
    void abmelden() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void pcOhneRecht_403MitKlartext() {
        anmelden(pc());
        when(service.darfZeiterfassungSehen(SecurityContextHolder.getContext().getAuthentication())).thenReturn(false);

        assertThatThrownBy(() -> aufruf(interceptor(service)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode().value()).isEqualTo(403);
                    assertThat(rse.getReason()).isEqualTo(ZeiterfassungZugriffInterceptor.MELDUNG);
                });
    }

    @Test
    void pcMitRecht_laeuftDurch() {
        anmelden(pc());
        when(service.darfZeiterfassungSehen(SecurityContextHolder.getContext().getAuthentication())).thenReturn(true);

        assertThat(aufruf(interceptor(service))).isTrue();
    }

    @Test
    void handyOderOhneAnmeldung_wirdNichtGeprueft() {
        anmelden(new MobilePrincipal(9L));
        assertThat(aufruf(interceptor(service))).isTrue();

        SecurityContextHolder.clearContext();
        assertThat(aufruf(interceptor(service))).isTrue();

        verifyNoInteractions(service);
    }

    @Test
    void ohneServiceImKontext_laeuftDurch() {
        anmelden(pc());
        assertThat(aufruf(interceptor(null))).isTrue();
    }

    @Test
    void pfadlisten_sperrenZeiterfassungUndLassenTeamKalenderUndSichtbarkeitFrei() {
        AntPathMatcher matcher = new AntPathMatcher();
        java.util.function.Predicate<String> gesperrt = pfad ->
                ZeiterfassungZugriffInterceptor.GESPERRTE_PFADE.stream().anyMatch(m -> matcher.match(m, pfad))
                        && ZeiterfassungZugriffInterceptor.FREIE_PFADE.stream().noneMatch(m -> matcher.match(m, pfad));

        assertThat(gesperrt).accepts("/api/urlaub/antraege", "/api/abwesenheit/mitarbeiter/7",
                "/api/langzeitkrankmeldungen/3", "/api/zeitverwaltung/zeitkonten",
                "/api/zeitverwaltung/monatsabschluesse/7/2026/10", "/api/zeitkonto/korrekturen/mitarbeiter/7");
        assertThat(gesperrt).rejects("/api/abwesenheit/team", "/api/zeitverwaltung/sichtbarkeit",
                "/api/zeitverwaltung/feiertage/zwischen", "/api/kalender", "/api/zeiterfassung/feiertage");
    }
}
