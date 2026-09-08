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
 * Sicherheits-Regressionstest zum Abschnitts-4-Nachbesserung-Befund 1: der
 * Hinweis-Endpoint (GET .../urlaubs-hinweise) verrät Existenz und
 * Beginndatum einer Langzeitkrankmeldung eines Mitarbeiters - Gesundheitsdaten
 * nach Art. 9 DSGVO. Er lag ursprünglich unter {@code /api/urlaub/antraege/hinweise}
 * und damit versehentlich auf der {@code permitAll()}-Kette der Mobile-App
 * ({@link SecurityConfig#ZEITERFASSUNG_PATHS}, Muster {@code /api/urlaub/**}).
 * Er MUSS hinter dem Login liegen.
 *
 * <p>Steht bewusst im {@code config}-Package: {@link SecurityConfig#ZEITERFASSUNG_PATHS}
 * ist package-private, ein Test in {@code controller} oder {@code service}
 * (die Dateien des Nachbesserungs-Auftrags) käme nicht dran, ohne die
 * Sichtbarkeit in {@code SecurityConfig} selbst zu ändern - das war ausdrücklich
 * nicht erlaubt.
 *
 * <p>Der tatsächlich gemappte Pfad wird per Reflection aus
 * {@link UrlaubsantragController} gelesen (Klassen- + Methoden-Annotation),
 * statt ihn im Test hart zu verdrahten - eine künftige Pfadänderung fällt so
 * sofort auf, egal ob sie den Endpoint zurück auf die permitAll-Kette schiebt.
 * Ein reiner "liefert 200 mit Login"-Test würde genau diesen Fehler nicht
 * fangen (der Endpoint hat vorher ja auch mit Login 200 geliefert - das
 * Problem war, dass er das AUCH ohne Login tat).
 */
class UrlaubsHinweiseSicherheitTest {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private static boolean ohneLoginErreichbar(String pfad) {
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
    @DisplayName("Befund 1: Der Hinweis-Endpoint liegt NICHT mehr auf der permitAll-Kette")
    void hinweiseEndpointLiegtNichtAufDerPermitAllKette() {
        String pfad = gemappterHinweisePfad();
        assertThat(ohneLoginErreichbar(pfad))
                .as("GET %s verrät Existenz/Beginndatum einer Krankmeldung (Art. 9 DSGVO) - muss authenticated() sein",
                        pfad)
                .isFalse();
    }

    @Test
    @DisplayName("Gegenprobe: der ursprüngliche Pfad wäre auf der permitAll-Kette gelandet")
    void derUrspruenglicheUrlaubsPfadWaereOhneLoginErreichbarGewesen() {
        // Dokumentiert den Befund: hätte der Endpoint weiterhin unter
        // /api/urlaub/antraege/hinweise gelegen, wäre er über /api/urlaub/**
        // (Mobile-Whitelist) ohne jede Authentifizierung erreichbar gewesen.
        assertThat(ohneLoginErreichbar("/api/urlaub/antraege/hinweise")).isTrue();
    }

    @Test
    @DisplayName("Die eigentlichen Mobile-Endpoints des Urlaubsantrags bleiben unangetastet")
    void mobileUrlaubsEndpointsBleibenWeiterhinOhneLoginErreichbar() {
        // Der Umzug des Hinweis-Endpoints darf die Endpoints, die die Handy-App
        // wirklich braucht (Antrag stellen, Anträge lesen, Resturlaub), nicht
        // versehentlich mit hinter den Login ziehen.
        assertThat(ohneLoginErreichbar("/api/urlaub/antraege")).isTrue();
        assertThat(ohneLoginErreichbar("/api/urlaub/resturlaub")).isTrue();
    }
}
