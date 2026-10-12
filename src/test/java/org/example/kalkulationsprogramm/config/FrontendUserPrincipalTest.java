package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class FrontendUserPrincipalTest {

    private static final FrontendUserPrincipal MAX = new FrontendUserPrincipal(
            42L, "max.mustermann", "Max Mustermann", "hash", true, Set.of(FrontendUserRole.ADMIN));

    @Test
    void liefertAngemeldetenBenutzer() {
        var auth = new UsernamePasswordAuthenticationToken(MAX, null, MAX.getAuthorities());

        assertThat(FrontendUserPrincipal.aus(auth)).isSameAs(MAX);
        assertThat(FrontendUserPrincipal.idAus(auth)).isEqualTo(42L);
    }

    @Test
    void ohneAnmeldungNull() {
        assertThat(FrontendUserPrincipal.aus(null)).isNull();
        assertThat(FrontendUserPrincipal.idAus(null)).isNull();
    }

    @Test
    void fremderPrincipalTypNull() {
        var auth = new UsernamePasswordAuthenticationToken("max.mustermann", null);

        assertThat(FrontendUserPrincipal.aus(auth)).isNull();
        assertThat(FrontendUserPrincipal.idAus(auth)).isNull();
    }
}
