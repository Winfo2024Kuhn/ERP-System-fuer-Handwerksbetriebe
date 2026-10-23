package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.example.kalkulationsprogramm.controller.SpaErrorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Prüft die öffentliche Kette ({@code staticResourcesFilterChain}) durch die ECHTE
 * Security-Filter-Chain: CSRF ist dort nicht abgeschaltet, sondern nimmt genau die
 * {@link SecurityConfig#OEFFENTLICHE_PFADE} aus. Für Browser, Service Worker und das
 * Nachtupdate darf sich dadurch nichts ändern – kein Request braucht ein CSRF-Token,
 * und es entsteht keine Session.
 *
 * <p>MockMvc hat keine Servlet-Registrierungen, die Ausnahme greift hier deshalb als
 * Ant-Matcher; in Produktion ist sie wie der securityMatcher ein MVC-Matcher. Dieser
 * Test prüft also das Verhalten, die Gleichheit der Matcher folgt aus dem Code.
 */
@SpringBootTest(properties = { "spring.jpa.hibernate.ddl-auto=create-drop", "file.mail-attachment-dir=attachments" })
@AutoConfigureMockMvc
class OeffentlichePfadeCsrfTest {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final Set<RequestMethod> LESENDE_METHODEN =
            EnumSet.of(RequestMethod.GET, RequestMethod.HEAD, RequestMethod.OPTIONS);

    @Autowired
    private MockMvc mockMvc;

    // Qualifier nötig: Der Actuator bringt weitere RequestMappingHandlerMapping-Beans mit.
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    /** Je Muster aus OEFFENTLICHE_PFADE ein konkreter Pfad; Wildcards werden zu einer Beispieldatei. */
    static Stream<String> oeffentlichePfade() {
        return Arrays.stream(SecurityConfig.OEFFENTLICHE_PFADE)
                .map(muster -> muster.replace("/**", "/beispiel.txt"))
                .peek(pfad -> assertThat(pfad).as("Muster ohne konkreten Beispielpfad").doesNotContain("*", "{"));
    }

    @ParameterizedTest(name = "GET {0}")
    @MethodSource("oeffentlichePfade")
    @DisplayName("GET ohne Login und ohne CSRF-Token wird nicht abgewiesen")
    void getOhneCsrfTokenNichtAbgewiesen(String pfad) throws Exception {
        pruefeNichtAbgewiesenOhneSession(get(pfad), "GET " + pfad);
    }

    @ParameterizedTest(name = "POST {0}")
    @MethodSource("oeffentlichePfade")
    @DisplayName("POST ohne CSRF-Token scheitert wie bisher nicht am CSRF-Filter")
    void postOhneCsrfTokenNichtAmCsrfFilterGescheitert(String pfad) throws Exception {
        pruefeNichtAbgewiesenOhneSession(post(pfad), "POST " + pfad);
    }

    @Test
    @DisplayName("Startseite, Health und HEAD liefern 200 und legen keine Session an")
    void statischeDateienOhneSession() throws Exception {
        for (MvcResult ergebnis : new MvcResult[] {
                mockMvc.perform(get("/index.html")).andExpect(status().isOk()).andReturn(),
                mockMvc.perform(head("/index.html")).andExpect(status().isOk()).andReturn(),
                mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andReturn() }) {
            assertThat(ergebnis.getRequest().getSession(false))
                    .as("Session nach %s", ergebnis.getRequest().getRequestURI())
                    .isNull();
        }
    }

    /**
     * Grund für die CSRF-Ausnahme: Unter den öffentlichen Pfaden gibt es nur lesende
     * Endpoints. Einzige Ausnahme ist die Fehlerseite (nimmt jede Methode an, ändert nichts).
     */
    @Test
    @DisplayName("Unter öffentlichen Pfaden gibt es keine schreibenden Endpoints")
    void keineSchreibendenEndpointsUnterOeffentlichenPfaden() {
        List<String> oeffentlicheMappings = new ArrayList<>();
        List<String> schreibend = new ArrayList<>();
        handlerMapping.getHandlerMethods().forEach((info, handler) -> {
            Set<RequestMethod> methoden = info.getMethodsCondition().getMethods();
            boolean nurLesend = !methoden.isEmpty() && LESENDE_METHODEN.containsAll(methoden);
            info.getPatternValues().stream().filter(OeffentlichePfadeCsrfTest::istOeffentlich).forEach(pfad -> {
                oeffentlicheMappings.add(pfad);
                boolean fehlerseite = handler.getBeanType() == SpaErrorController.class && "/error".equals(pfad);
                if (!nurLesend && !fehlerseite) {
                    schreibend.add((methoden.isEmpty() ? "ALLE" : methoden) + " " + pfad + " -> " + handler);
                }
            });
        });

        assertThat(oeffentlicheMappings).as("Suche findet die Fehlerseite").contains("/error");
        assertThat(schreibend).as("Schreibende Endpoints gehören unter /api/** (Session + CSRF)").isEmpty();
    }

    private void pruefeNichtAbgewiesenOhneSession(RequestBuilder anfrage, String beschreibung) throws Exception {
        MvcResult ergebnis = mockMvc.perform(anfrage).andReturn();
        assertThat(ergebnis.getResponse().getStatus()).as("Status für %s", beschreibung).isNotIn(401, 403);
        assertThat(ergebnis.getRequest().getSession(false)).as("Session nach %s", beschreibung).isNull();
    }

    /** In beide Richtungen: auch ein Mapping wie {@code /{seite}} oder {@code /**} träfe öffentliche Pfade. */
    private static boolean istOeffentlich(String mappingPfad) {
        return Arrays.stream(SecurityConfig.OEFFENTLICHE_PFADE).anyMatch(muster -> MATCHER.match(muster, mappingPfad))
                || oeffentlichePfade().anyMatch(beispiel -> MATCHER.match(mappingPfad, beispiel));
    }
}
