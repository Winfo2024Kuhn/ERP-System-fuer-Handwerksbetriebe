package org.example.kalkulationsprogramm.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.example.kalkulationsprogramm.service.AufschlagBerechtigungService;
import org.example.kalkulationsprogramm.service.AufschlagService;
import org.example.kalkulationsprogramm.service.AufschlagService.KategorieZeile;
import org.example.kalkulationsprogramm.service.AufschlagService.Quelle;
import org.example.kalkulationsprogramm.service.AufschlagService.Uebersicht;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/** Mit der echten Fehlerbehandlung; die Rechteprüfung ist gemockt (sie hat ihren eigenen Test). Die Sicherheitskette prüft {@link AufschlagControllerSecurityTest}. */
@WebMvcTest(AufschlagController.class)
@AutoConfigureMockMvc(addFilters = false)
class AufschlagControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private AufschlagService service;
    @MockitoBean private AufschlagBerechtigungService berechtigung;

    private static Uebersicht uebersicht() {
        return new Uebersicht(new BigDecimal("20.00"), List.of(new KategorieZeile(1, null, "Stahl", new BigDecimal("25.00"), new BigDecimal("25.00"), Quelle.KATEGORIE, "Stahl")));
    }

    @Test
    void berechtigungSagtDerOberflaecheObDerKnopfErscheint() throws Exception {
        when(berechtigung.darfAendern(any())).thenReturn(true);
        mvc.perform(get("/api/aufschlaege/berechtigung")).andExpect(status().isOk()).andExpect(jsonPath("$.darfAendern").value(true));

        when(berechtigung.darfAendern(any())).thenReturn(false);
        mvc.perform(get("/api/aufschlaege/berechtigung")).andExpect(status().isOk()).andExpect(jsonPath("$.darfAendern").value(false));
    }

    @Test
    void mitRechtSiehtManDieUebersichtUndAendertAllgemeinUndKategorie() throws Exception {
        when(service.uebersicht()).thenReturn(uebersicht());
        when(service.setzeAllgemein(any())).thenReturn(uebersicht());
        when(service.setzeKategorie(eq(1), any())).thenReturn(uebersicht());

        mvc.perform(get("/api/aufschlaege")).andExpect(status().isOk())
                .andExpect(jsonPath("$.allgemein").value(20.0)).andExpect(jsonPath("$.kategorien[0].pfad").value("Stahl")).andExpect(jsonPath("$.kategorien[0].wirksamQuelle").value("KATEGORIE"));
        mvc.perform(put("/api/aufschlaege/allgemein").contentType(MediaType.APPLICATION_JSON).content("{\"prozent\":12.5}")).andExpect(status().isOk());
        mvc.perform(put("/api/aufschlaege/kategorien/1").contentType(MediaType.APPLICATION_JSON).content("{\"prozent\":null}")).andExpect(status().isOk());

        verify(service).setzeAllgemein(new BigDecimal("12.5"));
        verify(service).setzeKategorie(1, null);
    }

    @Test
    void ohneRechtGibtEsWederUebersichtNochAenderung() throws Exception {
        doThrow(new AccessDeniedException("Du darfst die Aufschläge nicht ändern.")).when(berechtigung).verlange(any());

        mvc.perform(get("/api/aufschlaege")).andExpect(status().isForbidden());
        mvc.perform(put("/api/aufschlaege/allgemein").contentType(MediaType.APPLICATION_JSON).content("{\"prozent\":99}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/aufschlaege/kategorien/1").contentType(MediaType.APPLICATION_JSON).content("{\"prozent\":99}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void unsinnigeWerteUndUnbekannteKategorieKommenMitKlartextAn() throws Exception {
        when(service.setzeAllgemein(any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Der Aufschlag muss zwischen 0 und 999,99 Prozent liegen."));
        when(service.setzeKategorie(eq(99), any())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Die Kategorie gibt es nicht."));

        mvc.perform(put("/api/aufschlaege/allgemein").contentType(MediaType.APPLICATION_JSON).content("{\"prozent\":-1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("999,99")));
        mvc.perform(put("/api/aufschlaege/kategorien/99").contentType(MediaType.APPLICATION_JSON).content("{\"prozent\":5}")).andExpect(status().isNotFound());
    }

    @Test
    void kaputteOderFalscheEingabeGibt400() throws Exception {
        mvc.perform(put("/api/aufschlaege/allgemein").contentType(MediaType.APPLICATION_JSON).content("{kaputt")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/aufschlaege/allgemein").contentType(MediaType.APPLICATION_JSON).content("{\"prozent\":\"viel\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/aufschlaege/kategorien/abc").contentType(MediaType.APPLICATION_JSON).content("{\"prozent\":5}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
