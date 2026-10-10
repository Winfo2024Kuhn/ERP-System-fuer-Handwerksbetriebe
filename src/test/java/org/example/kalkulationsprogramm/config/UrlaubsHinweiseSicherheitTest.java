package org.example.kalkulationsprogramm.config;

import org.example.kalkulationsprogramm.controller.UrlaubsantragController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Der Büro-Hinweis verrät Gesundheitsdaten und bleibt für mobile Tokens gesperrt.
 * Der gemappte Pfad wird aus dem echten Controller gelesen, damit auch ein Umzug
 * in eine gemeinsam geroutete API die Methoden-/Routenfreigabe nicht umgeht.
 * Kettenzuordnung allein sagt nichts über anonymen Zugriff aus.
 */
class UrlaubsHinweiseSicherheitTest {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private static boolean nutztMobileFilterkette(String pfad) {
        return Arrays.stream(SecurityConfig.ZEITERFASSUNG_PATHS)
                .anyMatch(pattern -> MATCHER.match(pattern, pfad));
    }

    /**
     * Liest den tatsächlich von Spring aufgelösten Pfad der Hinweise-Methode:
     * Klassen-Präfix (falls vorhanden) + Methoden-Pfad, einfache Konkatenation
     * wie bei zwei literalen (wildcardfreien) {@code @RequestMapping}-Mustern.
     */
    private static String gemappterHinweisePfad() {
        Class<UrlaubsantragController> controllerClass = UrlaubsantragController.class;
        RequestMapping klassenMapping = controllerClass.getAnnotation(RequestMapping.class);
        String klassenPrefix = (klassenMapping != null && klassenMapping.value().length > 0)
                ? klassenMapping.value()[0]
                : "";

        for (Method m : controllerClass.getDeclaredMethods()) {
            if (!m.getName().equals("getHinweise")) {
                continue;
            }
            GetMapping mapping = m.getAnnotation(GetMapping.class);
            if (mapping == null) {
                throw new IllegalStateException("getHinweise hat kein @GetMapping mehr");
            }
            String methodenPfad = mapping.value().length > 0 ? mapping.value()[0] : mapping.path()[0];
            return klassenPrefix + methodenPfad;
        }
        throw new IllegalStateException("UrlaubsantragController.getHinweise nicht gefunden");
    }

    @Test
    @DisplayName("Der Büro-Hinweis ist kein freigegebener mobiler Lesezugriff")
    void hinweiseEndpointBleibtFuerMobileTokensGesperrt() {
        String pfad = gemappterHinweisePfad();
        assertThat(nutztMobileFilterkette(pfad)).isFalse();
        assertThat(MobileApiPolicy.allows("GET", pfad)).isFalse();
        assertThat(MobileApiPolicy.allows("HEAD", pfad)).isFalse();
    }

    @Test
    @DisplayName("Auch unter dem früheren Urlaubspfad erlaubt die Policy keine Gesundheitsdaten")
    void historischerUrlaubspfadIstTrotzGemeinsamerKetteNichtFreigegeben() {
        String pfad = "/api/urlaub/antraege/hinweise";
        assertThat(nutztMobileFilterkette(pfad)).isTrue();
        assertThat(MobileApiPolicy.allows("GET", pfad)).isFalse();
        assertThat(MobileApiPolicy.allows("HEAD", pfad)).isFalse();
    }

    @Test
    @DisplayName("Mobile Tokens dürfen eigene Urlaubsanträge lesen und stellen")
    void mobileUrlaubsEndpointsSindNurMitDenBenoetigtenMethodenFreigegeben() {
        assertThat(MobileApiPolicy.allows("GET", "/api/urlaub/antraege")).isTrue();
        assertThat(MobileApiPolicy.allows("POST", "/api/urlaub/antraege")).isTrue();
        assertThat(MobileApiPolicy.allows("GET", "/api/urlaub/resturlaub")).isTrue();
        assertThat(MobileApiPolicy.allows("PUT", "/api/urlaub/antraege/7/approve")).isFalse();
    }
}
