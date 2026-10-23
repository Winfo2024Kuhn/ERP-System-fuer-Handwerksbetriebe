package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Abwesenheit;
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.service.AbwesenheitService;
import org.example.kalkulationsprogramm.service.GesundheitsdatenSichtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/**
 * Abwesenheit buchen (Fehler als Klartext, 409 bei festgeschriebenem Monat –
 * Issue #269, Punkt 8) und Team-Kalender ohne Krankheitsgrund für Unbefugte
 * (Issue #268). Dummy-Daten: Max Mustermann, Erika Musterfrau.
 */
@WebMvcTest(AbwesenheitController.class)
@AutoConfigureMockMvc(addFilters = false)
class AbwesenheitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AbwesenheitService abwesenheitService;
    @MockitoBean
    private GesundheitsdatenSichtService gesundheitsdatenSichtService;

    private static final String BODY = """
            {"mitarbeiterId":1,"datum":"2026-08-03","typ":"URLAUB","halberTag":true}
            """;

    private static Abwesenheit abwesenheit(long id, long mitarbeiterId, String vorname, AbwesenheitsTyp typ) {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(mitarbeiterId);
        m.setVorname(vorname);
        m.setNachname("Mustermann");
        Abwesenheit a = new Abwesenheit();
        a.setId(id);
        a.setMitarbeiter(m);
        a.setTyp(typ);
        a.setDatum(LocalDate.of(2026, 8, 3));
        a.setStunden(new BigDecimal("8.00"));
        return a;
    }

    @Test
    void buchen_erfolgLiefert201() throws Exception {
        Abwesenheit gebucht = abwesenheit(5L, 1L, "Max", AbwesenheitsTyp.URLAUB);
        gebucht.setStunden(new BigDecimal("4.00"));
        when(abwesenheitService.bucheAbwesenheit(eq(1L), eq(LocalDate.of(2026, 8, 3)), eq(AbwesenheitsTyp.URLAUB),
                eq(true), any())).thenReturn(gebucht);

        mockMvc.perform(post("/api/abwesenheit").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.stunden").value(4.0));
    }

    @Test
    void buchen_festgeschriebenerMonatLiefert409StattServerfehler() throws Exception {
        when(abwesenheitService.bucheAbwesenheit(any(), any(), any(), anyBoolean(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                        "Dieser Monat ist bereits festgeschrieben. Bitte setzen Sie zuerst den Monatsabschluss zurück."));

        mockMvc.perform(post("/api/abwesenheit").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Dieser Monat ist bereits festgeschrieben. Bitte setzen Sie zuerst den Monatsabschluss zurück."));
    }

    @Test
    void buchen_regelverstossLiefert400MitMeldung() throws Exception {
        when(abwesenheitService.bucheAbwesenheit(any(), any(), any(), anyBoolean(), any()))
                .thenThrow(new IllegalStateException("Nicht genügend Überstunden für den Zeitausgleich."));

        mockMvc.perform(post("/api/abwesenheit").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Nicht genügend Überstunden für den Zeitausgleich."));
    }

    @Test
    void buchen_ungueltigeEingabenLiefern400OhneServiceAufruf() throws Exception {
        String[] kaputt = {
                "{\"datum\":\"2026-08-03\",\"typ\":\"URLAUB\"}",
                "{\"mitarbeiterId\":1,\"datum\":\"'; DROP TABLE abwesenheit; --\",\"typ\":\"URLAUB\"}",
                "{\"mitarbeiterId\":1,\"datum\":\"2026-08-03\",\"typ\":\"<script>alert(1)</script>\"}",
                "{\"mitarbeiterId\":1,\"datum\":\"2026-08-03\",\"typ\":\"URLAUB\",\"stunden\":\"viel\"}"
        };
        for (String body : kaputt) {
            mockMvc.perform(post("/api/abwesenheit").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Die Angaben zur Abwesenheit sind unvollständig oder ungültig."));
        }
        verify(abwesenheitService, never()).bucheAbwesenheit(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void team_ohneRecht_krankheitAnderer_wirdAbwesend_eigeneBleibtKrankheit() throws Exception {
        when(gesundheitsdatenSichtService.sichtFuer(any()))
                .thenReturn(new GesundheitsdatenSichtService.Sicht(false, 1L));
        when(abwesenheitService.getAllAbwesenheitenForZeitraum(any(), any())).thenReturn(List.of(
                abwesenheit(1L, 1L, "Max", AbwesenheitsTyp.KRANKHEIT),
                abwesenheit(2L, 2L, "Erika", AbwesenheitsTyp.KRANKHEIT),
                abwesenheit(3L, 2L, "Erika", AbwesenheitsTyp.URLAUB)));

        mockMvc.perform(get("/api/abwesenheit/team").param("von", "2026-08-01").param("bis", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].typ").value("KRANKHEIT"))
                .andExpect(jsonPath("$[1].typ").value("ABWESEND"))
                .andExpect(jsonPath("$[1].mitarbeiterName").value("Erika Mustermann"))
                .andExpect(jsonPath("$[2].typ").value("URLAUB"));
    }

    @Test
    void team_adminOderGf_siehtKrankheit() throws Exception {
        when(gesundheitsdatenSichtService.sichtFuer(any()))
                .thenReturn(new GesundheitsdatenSichtService.Sicht(true, null));
        when(abwesenheitService.getAllAbwesenheitenForZeitraum(any(), any())).thenReturn(List.of(
                abwesenheit(2L, 2L, "Erika", AbwesenheitsTyp.KRANKHEIT)));

        mockMvc.perform(get("/api/abwesenheit/team").param("von", "2026-08-01").param("bis", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].typ").value("KRANKHEIT"));
    }
}
