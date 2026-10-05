package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.example.kalkulationsprogramm.domain.EmailTextTemplate;
import org.example.kalkulationsprogramm.domain.EmailTextTemplateKategorie;
import org.example.kalkulationsprogramm.service.EmailTextTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EmailTextTemplateControllerLueckenTest {

    private final EmailTextTemplateService service = mock(EmailTextTemplateService.class);
    private MockMvc mvc;

    private static final String GUELTIG =
            "{\"dokumentTyp\":\"rechnung\",\"name\":\"Standard\",\"subjectTemplate\":\"Betreff\",\"htmlBody\":\"<p>Hallo</p>\"}";

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new EmailTextTemplateController(service)).build();
    }

    private static EmailTextTemplate entity(long id, String typ) {
        EmailTextTemplate e = new EmailTextTemplate();
        e.setId(id);
        e.setDokumentTyp(typ);
        e.setName("Vorlage " + id);
        e.setSubjectTemplate("S");
        e.setHtmlBody("<p>B</p>");
        return e;
    }

    @Test
    @DisplayName("GET Liste: leitet fehlende Kategorie aus dem Dokumenttyp ab")
    void liste() throws Exception {
        EmailTextTemplate mitKategorie = entity(1L, "RECHNUNG");
        mitKategorie.setKategorie(EmailTextTemplateKategorie.SYSTEM);
        when(service.list()).thenReturn(List.of(mitKategorie, entity(2L, "MAHNUNG"), entity(3L, "SONSTIGES")));

        mvc.perform(get("/api/email-textvorlagen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].kategorie").value("SYSTEM"))
                .andExpect(jsonPath("$[1].kategorie").value("MAHNWESEN"))
                .andExpect(jsonPath("$[2].kategorie").value("SYSTEM"));
    }

    @Test
    @DisplayName("GET /{id}: gefunden -> 200, unbekannt -> 404")
    void einzeln() throws Exception {
        when(service.get(1L)).thenReturn(entity(1L, "ANGEBOT"));
        when(service.get(99L)).thenThrow(new IllegalArgumentException("nicht gefunden"));

        mvc.perform(get("/api/email-textvorlagen/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dokumentTyp").value("ANGEBOT"))
                .andExpect(jsonPath("$.kategorie").value("DOKUMENT"));
        mvc.perform(get("/api/email-textvorlagen/99")).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "9999999999999999999999", "';DROP"})
    @DisplayName("GET /{id}: nicht numerische IDs -> 400")
    void ungueltigeId(String id) throws Exception {
        mvc.perform(get("/api/email-textvorlagen/{id}", id)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("POST: gueltige Vorlage wird angelegt")
    void anlegen() throws Exception {
        when(service.create(any())).thenReturn(entity(5L, "RECHNUNG"));
        mvc.perform(post("/api/email-textvorlagen").contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
        verify(service).create(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"dokumentTyp\":\"\",\"name\":\"n\",\"subjectTemplate\":\"s\",\"htmlBody\":\"b\"}",
            "{\"dokumentTyp\":\"RECHNUNG\",\"name\":\" \",\"subjectTemplate\":\"s\",\"htmlBody\":\"b\"}",
            "{\"dokumentTyp\":\"RECHNUNG\",\"name\":\"n\",\"subjectTemplate\":\"\",\"htmlBody\":\"b\"}",
            "{\"dokumentTyp\":\"RECHNUNG\",\"name\":\"n\",\"subjectTemplate\":\"s\"}"
    })
    @DisplayName("POST/PUT: fehlende oder leere Pflichtfelder -> 400, Service wird nicht aufgerufen")
    void pflichtfelder(String json) throws Exception {
        mvc.perform(post("/api/email-textvorlagen").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/email-textvorlagen/1").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("POST: XSS-/SQL-Strings werden unveraendert an den Service gereicht (Maskierung erfolgt beim Rendern)")
    void injectionStrings() throws Exception {
        when(service.create(any())).thenReturn(entity(6L, "RECHNUNG"));
        String json = "{\"dokumentTyp\":\"'; DROP TABLE x; --\",\"name\":\"<script>alert(1)</script>\","
                + "\"subjectTemplate\":\"s\",\"htmlBody\":\"<script>alert(1)</script>\"}";
        mvc.perform(post("/api/email-textvorlagen").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
        verify(service).create(any());
    }

    @Test
    @DisplayName("PUT: Erfolg -> 200, unbekannte ID -> 404")
    void aendern() throws Exception {
        when(service.update(eq(1L), any())).thenReturn(entity(1L, "RECHNUNG"));
        when(service.update(eq(99L), any())).thenThrow(new IllegalArgumentException("nicht gefunden"));

        mvc.perform(put("/api/email-textvorlagen/1").contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                .andExpect(status().isOk());
        mvc.perform(put("/api/email-textvorlagen/99").contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE: 204 und Service wird mit der ID aufgerufen")
    void loeschen() throws Exception {
        mvc.perform(delete("/api/email-textvorlagen/4")).andExpect(status().isNoContent());
        verify(service).delete(4L);
    }

    @Test
    @DisplayName("GET /dokumenttypen: enthaelt alle Dokumenttypen plus ZEICHNUNG und Webseiten-Bestaetigung mit Kategorie")
    void dokumenttypen() throws Exception {
        mvc.perform(get("/api/email-textvorlagen/dokumenttypen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.value=='ANGEBOT')].label").value("Anfrage / Angebot"))
                .andExpect(jsonPath("$[?(@.value=='RECHNUNG')].kategorie").value("DOKUMENT"))
                .andExpect(jsonPath("$[?(@.value=='ERSTE_MAHNUNG')].kategorie").value("MAHNWESEN"))
                .andExpect(jsonPath("$[?(@.value=='ZEICHNUNG')].kategorie").value("DOKUMENT"))
                .andExpect(jsonPath("$[?(@.value=='ZEICHNUNG')].label").value("Zeichnung / Entwurf"))
                .andExpect(jsonPath("$[?(@.value=='WEBSITE_ANFRAGE_BESTAETIGUNG')].kategorie").value("WEBSITE"))
                .andExpect(jsonPath("$[?(@.value=='WEBSITE_ANFRAGE_BESTAETIGUNG')].kategorieLabel")
                        .value("Webseite & Anfragen"));
    }

    @Test
    @DisplayName("GET /placeholders: liefert Tokens im {{TOKEN}}-Format inkl. Bankdaten")
    void platzhalter() throws Exception {
        mvc.perform(get("/api/email-textvorlagen/placeholders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.token=='{{ANREDE}}')]").exists())
                .andExpect(jsonPath("$[?(@.token=='{{IBAN}}')]").exists())
                .andExpect(jsonPath("$[?(@.token=='{{REVIEW_LINK}}')]").exists());
    }
}
