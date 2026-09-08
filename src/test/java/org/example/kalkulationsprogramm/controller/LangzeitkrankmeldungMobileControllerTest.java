package org.example.kalkulationsprogramm.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungPhaseTyp;
import org.example.kalkulationsprogramm.service.LangzeitkrankmeldungService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc-Tests fuer {@link LangzeitkrankmeldungMobileController}. Der
 * Endpoint ist unauthentifiziert (Token in der URL statt Login), deshalb
 * liegt der Schwerpunkt auf den Sicherheits-/DSGVO-Faellen aus
 * TESTING_SECURITY.md: unbekanntes Token, Path-Traversal- und
 * SQL-Injection-Muster im Token, sowie die ausdrueckliche Zusicherung, dass
 * die Antwort nie einen Namen oder eine Notiz enthaelt.
 */
@WebMvcTest(LangzeitkrankmeldungMobileController.class)
@AutoConfigureMockMvc(addFilters = false)
class LangzeitkrankmeldungMobileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LangzeitkrankmeldungService service;

    @Nested
    @DisplayName("GET /api/zeiterfassung/langzeitkrankmeldung/{token}")
    class GetMobileStand {

        @Test
        @DisplayName("Unbekanntes Token liefert leeres Objekt ohne Fehler")
        void unbekanntesTokenLiefertLeeresObjekt() throws Exception {
            given(service.getMobileStand(eq("unbekanntes-token"), any(LocalDate.class)))
                    .willReturn(Collections.emptyMap());

            mockMvc.perform(get("/api/zeiterfassung/langzeitkrankmeldung/{token}", "unbekanntes-token"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{}", true));
        }

        @Test
        @DisplayName("Laufende Wiedereingliederung liefert genau die fuenf erwarteten Felder")
        void laufendeWiedereingliederungLiefertFelder() throws Exception {
            Map<String, Object> stand = new LinkedHashMap<>();
            stand.put("phase", LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG);
            stand.put("phaseLabel", "Wiedereingliederung");
            stand.put("heuteGeplanteStunden", new BigDecimal("4.00"));
            stand.put("seit", LocalDate.of(2026, 3, 1));
            stand.put("bisDatum", LocalDate.of(2026, 4, 30));
            given(service.getMobileStand(eq("gueltiges-token"), any(LocalDate.class))).willReturn(stand);

            mockMvc.perform(get("/api/zeiterfassung/langzeitkrankmeldung/{token}", "gueltiges-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.phase").value("WIEDEREINGLIEDERUNG"))
                    .andExpect(jsonPath("$.phaseLabel").value("Wiedereingliederung"))
                    .andExpect(jsonPath("$.heuteGeplanteStunden").value(4.00))
                    .andExpect(jsonPath("$.seit").value("2026-03-01"))
                    .andExpect(jsonPath("$.bisDatum").value("2026-04-30"))
                    .andExpect(jsonPath("$.length()").value(5));
        }

        @Test
        @DisplayName("Antwort enthaelt nie einen Namen oder eine Notiz (DSGVO)")
        void antwortEnthaeltNieNamenOderNotiz() throws Exception {
            Map<String, Object> stand = new LinkedHashMap<>();
            stand.put("phase", LangzeitkrankmeldungPhaseTyp.KRANKENGELD);
            stand.put("phaseLabel", "Krankengeld der Krankenkasse");
            stand.put("heuteGeplanteStunden", null);
            stand.put("seit", LocalDate.of(2026, 1, 10));
            stand.put("bisDatum", null);
            given(service.getMobileStand(eq("noch-ein-token"), any(LocalDate.class))).willReturn(stand);

            mockMvc.perform(get("/api/zeiterfassung/langzeitkrankmeldung/{token}", "noch-ein-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").doesNotExist())
                    .andExpect(jsonPath("$.mitarbeiterName").doesNotExist())
                    .andExpect(jsonPath("$.notiz").doesNotExist())
                    .andExpect(jsonPath("$.vorname").doesNotExist())
                    .andExpect(jsonPath("$.nachname").doesNotExist());
        }

        @Test
        @DisplayName("Path-Traversal-Muster (als kodierter Token-Wert) liefert leeres Objekt, keine Exception")
        void pathTraversalTokenLiefertLeeresObjekt() throws Exception {
            // Echte, unkodierte "/"-Zeichen wuerden schon von der URL-Normalisierung
            // als Navigation aufgeloest (RFC-3986-Dot-Segment-Entfernung) und nie als
            // Token-WERT im Controller ankommen - das faengt schon die URL-Schicht ab.
            // Ein Token, dessen WERT nach dem Dekodieren "../../etc/passwd" ergibt,
            // muss die Schraegstriche deshalb kodiert mitbringen (%2F) - genau das
            // bildet dieser Test nach: der Controller reicht den dekodierten Wert
            // unveraendert an den Service durch, ohne Exception.
            given(service.getMobileStand(any(), any(LocalDate.class))).willReturn(Collections.emptyMap());

            mockMvc.perform(get("/api/zeiterfassung/langzeitkrankmeldung/..%2F..%2Fetc%2Fpasswd"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{}", true));
        }

        @Test
        @DisplayName("SQL-Injection-Muster im Token liefert leeres Objekt, keine Exception")
        void sqlInjectionTokenLiefertLeeresObjekt() throws Exception {
            // Bewusst ohne Semikolon: ein ";" in einem URL-Pfadsegment wird von
            // Spring als Matrix-Parameter-Trenner behandelt und vor dem Routing
            // abgeschnitten (UrlPathHelper#removeSemicolonContent) - das ist ein
            // Detail der URL-Schicht, kein Beleg fuer/gegen SQL-Injection-Schutz.
            // Dieses Muster prueft stattdessen, dass ein klassischer
            // Injection-Versuch ohne Semikolon unveraendert und ohne Exception
            // beim Service ankommt (der ihn ueber Named Params sicher behandelt).
            String sqlInjectionToken = "' OR '1'='1' --";
            given(service.getMobileStand(eq(sqlInjectionToken), any(LocalDate.class)))
                    .willReturn(Collections.emptyMap());

            mockMvc.perform(get("/api/zeiterfassung/langzeitkrankmeldung/{token}", sqlInjectionToken))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{}", true));

            verify(service).getMobileStand(eq(sqlInjectionToken), any(LocalDate.class));
        }

        @Test
        @DisplayName("Jedes Token bekommt nur seinen eigenen Stand, nie den eines anderen Mitarbeiters")
        void jedesTokenBekommtNurEigenenStand() throws Exception {
            Map<String, Object> standEigen = new LinkedHashMap<>();
            standEigen.put("phase", LangzeitkrankmeldungPhaseTyp.LOHNFORTZAHLUNG);
            standEigen.put("phaseLabel", "Lohnfortzahlung durch den Betrieb");
            standEigen.put("heuteGeplanteStunden", null);
            standEigen.put("seit", LocalDate.of(2026, 2, 1));
            standEigen.put("bisDatum", LocalDate.of(2026, 3, 14));

            given(service.getMobileStand(eq("token-mitarbeiter-a"), any(LocalDate.class))).willReturn(standEigen);
            given(service.getMobileStand(eq("token-mitarbeiter-b"), any(LocalDate.class)))
                    .willReturn(Collections.emptyMap());

            mockMvc.perform(get("/api/zeiterfassung/langzeitkrankmeldung/{token}", "token-mitarbeiter-a"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.phase").value("LOHNFORTZAHLUNG"))
                    .andExpect(jsonPath("$.seit").value("2026-02-01"));

            mockMvc.perform(get("/api/zeiterfassung/langzeitkrankmeldung/{token}", "token-mitarbeiter-b"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{}", true));

            verify(service).getMobileStand(eq("token-mitarbeiter-a"), any(LocalDate.class));
            verify(service).getMobileStand(eq("token-mitarbeiter-b"), any(LocalDate.class));
        }

        @Test
        @DisplayName("Ruft den Service mit dem heutigen Datum als Stichtag auf")
        void ruftServiceMitHeutigemDatumAuf() throws Exception {
            given(service.getMobileStand(eq("heute-token"), any(LocalDate.class)))
                    .willReturn(Collections.emptyMap());

            mockMvc.perform(get("/api/zeiterfassung/langzeitkrankmeldung/{token}", "heute-token"))
                    .andExpect(status().isOk());

            verify(service).getMobileStand(eq("heute-token"), eq(LocalDate.now()));
        }
    }
}
