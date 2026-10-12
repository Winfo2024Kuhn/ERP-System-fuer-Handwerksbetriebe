package org.example.kalkulationsprogramm.testsupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;

/**
 * Für Security-Tests mit gemocktem {@link FrontendUserDetailsService}: Der
 * {@code SitzungsRechteFilter} gleicht jede Anfrage mit der Datenbank ab. Ohne diese Vorgabe
 * liefert der Mock „Benutzer unbekannt“, und jede Anmeldung im Test würde verworfen.
 * Rechteänderungen selbst prüft {@code SitzungsRechteFilterTest}.
 */
public final class SitzungsRechteImTest {

    private SitzungsRechteImTest() {
    }

    /** Jeder angemeldete Testbenutzer bleibt mit seinen Rechten angemeldet. */
    public static void rechteUnveraendert(FrontendUserDetailsService benutzer) {
        when(benutzer.aktuellerStand(any(FrontendUserPrincipal.class)))
                .thenAnswer(aufruf -> Optional.of(aufruf.getArgument(0, FrontendUserPrincipal.class)));
    }
}
