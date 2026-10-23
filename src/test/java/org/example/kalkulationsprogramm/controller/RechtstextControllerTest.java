package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.RechtstextArt;
import org.example.kalkulationsprogramm.domain.RechtstextFassung;
import org.example.kalkulationsprogramm.mapper.RechtstextMapper;
import org.example.kalkulationsprogramm.service.RechtstextService;
import org.example.kalkulationsprogramm.service.WiderrufsbelehrungPdfService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RechtstextController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RechtstextMapper.class)
class RechtstextControllerTest {

    private static final RechtstextArt W = RechtstextArt.WIDERRUFSBELEHRUNG;

    @Autowired private MockMvc mockMvc;
    @MockitoBean private RechtstextService rechtstextService;
    @MockitoBean private WiderrufsbelehrungPdfService widerrufsbelehrungPdfService;

    private static RechtstextService.Rechtstext standard() {
        return new RechtstextService.Rechtstext(W, 0, LocalDate.of(2026, 10, 13), null, "<p>Standard</p>", true);
    }

    private static RechtstextService.Rechtstext fassung3() {
        return new RechtstextService.Rechtstext(W, 3, LocalDate.of(2026, 11, 2),
                LocalDateTime.of(2026, 11, 2, 9, 15), "<p>Max Mustermann</p>", false);
    }

    private static RechtstextFassung fassungEntity(int nr) {
        RechtstextFassung f = new RechtstextFassung();
        f.setArt(W);
        f.setFassung(nr);
        f.setStand(LocalDate.of(2026, 11, 2));
        f.setGeaendertAm(LocalDateTime.of(2026, 11, 2, 9, 15));
        f.setInhaltHtml("<p>Fassung " + nr + "</p>");
        return f;
    }

    @Test
    @DisplayName("Übersicht: eine Karte je Art, Standardtext als Fassung 0")
    void liste() throws Exception {
        given(rechtstextService.alle()).willReturn(List.of(standard()));
        mockMvc.perform(get("/api/formulare/rechtstexte"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].art").value("WIDERRUFSBELEHRUNG"))
                .andExpect(jsonPath("$[0].bezeichnung").value("Widerrufsbelehrung"))
                .andExpect(jsonPath("$[0].fassung").value(0))
                .andExpect(jsonPath("$[0].stand").value("2026-10-13"))
                .andExpect(jsonPath("$[0].standardtext").value(true))
                .andExpect(jsonPath("$[0].geaendertAm").doesNotExist());
    }

    @Test
    @DisplayName("Detail mit HTML und Platzhaltern, Art in Kleinschreibung")
    void detail() throws Exception {
        given(rechtstextService.aktuell(W)).willReturn(fassung3());
        mockMvc.perform(get("/api/formulare/rechtstexte/widerrufsbelehrung"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fassung").value(3))
                .andExpect(jsonPath("$.geaendertAm").value("2026-11-02T09:15:00"))
                .andExpect(jsonPath("$.html").value("<p>Max Mustermann</p>"))
                .andExpect(jsonPath("$.platzhalter[0].token").value("{{FIRMA_KONTAKT}}"))
                .andExpect(jsonPath("$.platzhalter[0].beschreibung").exists())
                .andExpect(jsonPath("$.platzhalter[0].beispiel").exists());
    }

    @Test
    @DisplayName("Unbekannte Art und SQL-Injection im Pfad: 404 ohne Service-Aufruf")
    void unbekannteArt() throws Exception {
        mockMvc.perform(get("/api/formulare/rechtstexte/agb"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/formulare/rechtstexte/{art}", "'; DROP TABLE x; --"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/formulare/rechtstexte/agb").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"html\":\"<p>x</p>\"}"))
                .andExpect(status().isNotFound());
        verify(rechtstextService, never()).speichere(eq(W), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Speichern gibt die neue Fassung zurück")
    void speichern() throws Exception {
        given(rechtstextService.speichere(W, "<p>Max Mustermann</p>")).willReturn(fassung3());
        mockMvc.perform(put("/api/formulare/rechtstexte/WIDERRUFSBELEHRUNG").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"html\":\"<p>Max Mustermann</p>\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fassung").value(3))
                .andExpect(jsonPath("$.stand").value("2026-11-02"));
    }

    @Test
    @DisplayName("Speichern mit leerem oder XSS-Text: 400 mit verständlicher Meldung")
    void speichern_ungueltig() throws Exception {
        given(rechtstextService.speichere(W, "<script>alert(1)</script>")).willThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Der Text ist leer."));
        mockMvc.perform(put("/api/formulare/rechtstexte/WIDERRUFSBELEHRUNG").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"html\":\"<script>alert(1)</script>\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Der Text ist leer."));
    }

    @Test
    @DisplayName("Zurücksetzen auf Standardtext")
    void standard_zuruecksetzen() throws Exception {
        given(rechtstextService.setzeStandard(W)).willReturn(standard());
        mockMvc.perform(post("/api/formulare/rechtstexte/WIDERRUFSBELEHRUNG/standard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.standardtext").value(true));
    }

    @Test
    @DisplayName("Frühere Fassungen: Liste und einzelne Fassung")
    void fassungen() throws Exception {
        given(rechtstextService.fassungen(W)).willReturn(List.of(fassungEntity(2), fassungEntity(1)));
        given(rechtstextService.fassung(W, 1)).willReturn(fassungEntity(1));
        mockMvc.perform(get("/api/formulare/rechtstexte/WIDERRUFSBELEHRUNG/fassungen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fassung").value(2))
                .andExpect(jsonPath("$[0].html").doesNotExist())
                .andExpect(jsonPath("$[1].stand").value("2026-11-02"));
        mockMvc.perform(get("/api/formulare/rechtstexte/WIDERRUFSBELEHRUNG/fassungen/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.html").value("<p>Fassung 1</p>"));
    }

    @Test
    @DisplayName("Fassung 0, negativ, zu groß oder kein Zahlwert: 400 bzw. 404")
    void fassung_ungueltigeNummer() throws Exception {
        mockMvc.perform(get("/api/formulare/rechtstexte/WIDERRUFSBELEHRUNG/fassungen/0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/formulare/rechtstexte/WIDERRUFSBELEHRUNG/fassungen/-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/formulare/rechtstexte/WIDERRUFSBELEHRUNG/fassungen/" + Long.MAX_VALUE))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/formulare/rechtstexte/WIDERRUFSBELEHRUNG/fassungen/abc"))
                .andExpect(status().isBadRequest());
        verify(rechtstextService, never()).fassung(eq(W), anyInt());
        given(rechtstextService.fassung(W, 7)).willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "weg"));
        mockMvc.perform(get("/api/formulare/rechtstexte/WIDERRUFSBELEHRUNG/fassungen/7"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Vorschau liefert ein PDF; unbekannte Art 404")
    void vorschau() throws Exception {
        given(widerrufsbelehrungPdfService.erzeugeVorschau("<p>Max</p>")).willReturn("%PDF-1".getBytes());
        mockMvc.perform(post("/api/formulare/rechtstexte/WIDERRUFSBELEHRUNG/vorschau")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"html\":\"<p>Max</p>\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")))
                .andExpect(content().bytes("%PDF-1".getBytes()));

        mockMvc.perform(post("/api/formulare/rechtstexte/agb/vorschau")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"html\":null}"))
                .andExpect(status().isNotFound());
        verify(widerrufsbelehrungPdfService, never()).erzeugeVorschau(null);
    }

    @Test
    @DisplayName("Vorschau mit leerem Text: 400")
    void vorschau_leer() throws Exception {
        given(widerrufsbelehrungPdfService.erzeugeVorschau("")).willThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Der Text ist leer."));
        mockMvc.perform(post("/api/formulare/rechtstexte/WIDERRUFSBELEHRUNG/vorschau")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"html\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(rechtstextService);
    }

    @Test
    @DisplayName("Ohne Body: 400")
    void ohneBody() throws Exception {
        mockMvc.perform(put("/api/formulare/rechtstexte/WIDERRUFSBELEHRUNG").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        verify(rechtstextService, never()).speichere(eq(W), org.mockito.ArgumentMatchers.any());
    }
}
