package org.example.kalkulationsprogramm.controller.advice;

import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.orm.jpa.JpaOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Isolierter Test fuer {@link RestExceptionHandler}, ohne vollen Spring-Kontext.
 *
 * <p>Ein frueherer Anlauf wollte dies ueber ein {@code @WebMvcTest(controllers = ...)}
 * loesen, das auf eine verschachtelte Testklasse als Dummy-Controller zeigt --
 * dabei registriert Spring die Handler-Methoden der verschachtelten Klasse
 * nicht zuverlaessig (kein Mapping, die Requests laufen ins Leere). Deshalb hier
 * bewusst der fuer isolierte {@code @ControllerAdvice}-Tests uebliche Weg:
 * {@link MockMvcBuilders#standaloneSetup} verdrahtet eine Dummy-Controller-Instanz
 * plus die Advice-Instanz direkt zu einer MockMvc-Infrastruktur, komplett ohne
 * Spring-Kontext und damit ohne jede Bean-Scanning-Unsicherheit.</p>
 */
class RestExceptionHandlerTest {

    /**
     * Steht stellvertretend fuer eine echte JPA-Entitaet. Der volle
     * Klassenname darf laut Testfall nicht im Response landen -- daher ein
     * eigener, unverwechselbarer Name statt einer echten Domain-Klasse.
     */
    private static final class Testentitaet {
    }

    @RestController
    private static class DummyController {

        @GetMapping("/test/optimistic-lock")
        public String optimisticLock() {
            throw new ObjectOptimisticLockingFailureException(Testentitaet.class, 42L);
        }

        @GetMapping("/test/jpa-optimistic-lock")
        public String jpaOptimisticLock() {
            // JpaOptimisticLockingFailureException ist die Unterklasse, die
            // Spring Data JPA beim echten Versionskonflikt (Task 6/8) ueber
            // EntityManagerFactoryUtils tatsaechlich wirft.
            // ObjectOptimisticLockingFailureException ist deren direkte
            // Oberklasse -- der Handler muss also auch diesen Fall abdecken.
            throw new JpaOptimisticLockingFailureException(
                    new OptimisticLockException("Zeile wurde von einer anderen Transaktion geaendert."));
        }

        @GetMapping("/test/data-integrity")
        public String dataIntegrity() {
            throw new DataIntegrityViolationException("uk_irgendein_constraint");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // ConstraintMessageResolver ist laut Konstruktor @Nullable -- null
        // ist hier unproblematisch, weil dieser Test den DataIntegrityViolation-
        // Zweig nur zur Abgrenzung mitnutzt (siehe
        // dataIntegrityUndOptimisticLocking_ueberschneidenSichNichtImHandlerResolver),
        // nicht um dessen Resolver-Logik zu pruefen.
        mockMvc = MockMvcBuilders.standaloneSetup(new DummyController())
                .setControllerAdvice(new RestExceptionHandler(null))
                .build();
    }

    @Test
    void optimisticLockingFailure_liefert409MitStatusUndWordingTabelleText() throws Exception {
        mockMvc.perform(get("/test/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        "Jemand anders hat diese Daten gerade gespeichert. Ihre Änderungen wurden nicht übernommen — bitte neu laden."));
    }

    @Test
    void optimisticLockingFailure_technicalMessageOhneStacktraceUndKlassennamen() throws Exception {
        mockMvc.perform(get("/test/optimistic-lock"))
                .andExpect(status().isConflict())
                // detail ist das "technicalMessage"-Feld von ApiError; bei
                // NON_EMPTY-Serialisierung heisst "nichts drin" == im JSON
                // gar nicht erst vorhanden.
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("org.springframework"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Exception"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(Testentitaet.class.getName()))));
    }

    @Test
    void jpaOptimisticLockingFailureException_alsUnterklasseWirdEbenfallsAbgefangen() throws Exception {
        // Das ist der praxisrelevante Fall: repository.save(...) auf einer
        // veralteten Version wirft in Wirklichkeit diese Unterklasse, nicht
        // die Basisklasse direkt.
        mockMvc.perform(get("/test/jpa-optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        "Jemand anders hat diese Daten gerade gespeichert. Ihre Änderungen wurden nicht übernommen — bitte neu laden."));
    }

    @Test
    void dataIntegrityUndOptimisticLocking_ueberschneidenSichNichtImHandlerResolver() throws Exception {
        // Beide sind Geschwister unter DataAccessException (siehe Plan). Der
        // Test sichert ab, dass Spring pro Exception-Typ eindeutig den
        // jeweils eigenen Handler waehlt statt einer der beiden Meldungen
        // fuer beide Faelle zu verwenden.
        mockMvc.perform(get("/test/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Jemand anders hat diese Daten gerade gespeichert. Ihre Änderungen wurden nicht übernommen — bitte neu laden."));

        mockMvc.perform(get("/test/data-integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Constraint violation"));
    }
}
