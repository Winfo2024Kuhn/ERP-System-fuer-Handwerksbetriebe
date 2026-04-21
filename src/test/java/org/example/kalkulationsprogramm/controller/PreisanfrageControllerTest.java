package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Preisanfrage;
import org.example.kalkulationsprogramm.domain.PreisanfrageAngebot;
import org.example.kalkulationsprogramm.domain.PreisanfrageLieferant;
import org.example.kalkulationsprogramm.domain.PreisanfrageLieferantStatus;
import org.example.kalkulationsprogramm.domain.PreisanfrageStatus;
import org.example.kalkulationsprogramm.dto.Preisanfrage.PreisanfrageVergleichDto;
import org.example.kalkulationsprogramm.mapper.PreisanfrageMapper;
import org.example.kalkulationsprogramm.service.BestellungPdfService;
import org.example.kalkulationsprogramm.service.PreisanfrageAngebotsExtraktionService;
import org.example.kalkulationsprogramm.service.PreisanfrageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller-Tests fuer {@link PreisanfrageController}. Happy-Paths pro Endpoint,
 * 404-Faelle fuer unbekannte IDs + Security-Smokes (XSS in Notiz, SQLi-Payload
 * im Filter, negative / null IDs).
 */
@WebMvcTest(PreisanfrageController.class)
@AutoConfigureMockMvc(addFilters = false)
class PreisanfrageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PreisanfrageService preisanfrageService;

    @MockBean
    private PreisanfrageMapper preisanfrageMapper;

    @MockBean
    private BestellungPdfService bestellungPdfService;

    @MockBean
    private PreisanfrageAngebotsExtraktionService angebotsExtraktionService;

    // ─────────────────────────────────────────────────────────────
    // Helfer
    // ─────────────────────────────────────────────────────────────

    private Preisanfrage buildPreisanfrage(Long id) {
        Preisanfrage pa = new Preisanfrage();
        pa.setId(id);
        pa.setNummer("PA-2026-001");
        pa.setBauvorhaben("Bauvorhaben Max Mustermann");
        pa.setErstelltAm(LocalDateTime.of(2026, 4, 20, 10, 0));
        pa.setStatus(PreisanfrageStatus.OFFEN);
        return pa;
    }

    private org.example.kalkulationsprogramm.dto.Preisanfrage.PreisanfrageResponseDto buildResponseDto(Long id) {
        org.example.kalkulationsprogramm.dto.Preisanfrage.PreisanfrageResponseDto dto =
                new org.example.kalkulationsprogramm.dto.Preisanfrage.PreisanfrageResponseDto();
        dto.setId(id);
        dto.setNummer("PA-2026-001");
        dto.setBauvorhaben("Bauvorhaben Max Mustermann");
        dto.setStatus("OFFEN");
        return dto;
    }

    private PreisanfrageLieferant buildLieferant(Long palId, String name) {
        Lieferanten l = new Lieferanten();
        l.setId(99L);
        l.setLieferantenname(name);
        PreisanfrageLieferant pal = new PreisanfrageLieferant();
        pal.setId(palId);
        pal.setLieferant(l);
        pal.setStatus(PreisanfrageLieferantStatus.VORBEREITET);
        pal.setToken("PA-2026-001-ABCDE");
        return pal;
    }

    // ─────────────────────────────────────────────────────────────
    // POST /api/preisanfragen
    // ─────────────────────────────────────────────────────────────

    @Test
    void erstellen_happyPath_gibt201Zurueck() throws Exception {
        Preisanfrage pa = buildPreisanfrage(1L);
        when(preisanfrageService.erstellen(any())).thenReturn(pa);
        when(preisanfrageMapper.toResponseDto(pa)).thenReturn(buildResponseDto(1L));

        String body = """
                {
                  "bauvorhaben": "Bauvorhaben Max Mustermann",
                  "antwortFrist": "2026-05-01",
                  "notiz": "Bitte Angebot bis Freitag",
                  "lieferantIds": [10, 11],
                  "positionen": [{
                    "produktname": "Stahlprofil HEA 200",
                    "menge": 5,
                    "einheit": "m"
                  }]
                }
                """;
        mockMvc.perform(post("/api/preisanfragen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nummer").value("PA-2026-001"));
    }

    @Test
    void erstellen_ungueltigerInput_gibt400Zurueck() throws Exception {
        when(preisanfrageService.erstellen(any()))
                .thenThrow(new IllegalArgumentException("Mindestens ein Lieferant ist erforderlich"));

        mockMvc.perform(post("/api/preisanfragen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionen\":[]}"))
                .andExpect(status().isBadRequest());
    }

    // Security: XSS-Payload in Notiz muss 1:1 als String ankommen (kein HTML-Render).
    // Der Service bekommt die Payload, entscheidet selbst (Escape in PDF/Mail).
    @Test
    void erstellen_xssPayloadInNotiz_wirdUnveraendertWeitergereicht() throws Exception {
        Preisanfrage pa = buildPreisanfrage(2L);
        when(preisanfrageService.erstellen(any())).thenReturn(pa);
        when(preisanfrageMapper.toResponseDto(pa)).thenReturn(buildResponseDto(2L));

        String body = """
                {
                  "lieferantIds": [1],
                  "positionen": [{ "produktname": "Stahl" }],
                  "notiz": "<script>alert(1)</script>"
                }
                """;
        mockMvc.perform(post("/api/preisanfragen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        verify(preisanfrageService, times(1)).erstellen(any());
    }

    // ─────────────────────────────────────────────────────────────
    // POST /api/preisanfragen/{id}/versenden
    // ─────────────────────────────────────────────────────────────

    @Test
    void versendeAnAlleLieferanten_happyPath() throws Exception {
        Preisanfrage pa = buildPreisanfrage(7L);
        doNothing().when(preisanfrageService).versendeAnAlleLieferanten(7L);
        when(preisanfrageService.findeById(7L)).thenReturn(pa);
        when(preisanfrageMapper.toResponseDto(pa)).thenReturn(buildResponseDto(7L));

        mockMvc.perform(post("/api/preisanfragen/7/versenden"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void versendeAnAlleLieferanten_unbekannteId_gibt404Zurueck() throws Exception {
        doThrow(new IllegalArgumentException("Preisanfrage nicht gefunden: 9999"))
                .when(preisanfrageService).versendeAnAlleLieferanten(9999L);

        mockMvc.perform(post("/api/preisanfragen/9999/versenden"))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────────────────────
    // POST /api/preisanfragen/lieferant/{palId}/versenden
    // ─────────────────────────────────────────────────────────────

    @Test
    void versendeEinzelnenLieferanten_happyPath() throws Exception {
        doNothing().when(preisanfrageService).versendeAnEinzelnenLieferanten(42L);

        mockMvc.perform(post("/api/preisanfragen/lieferant/42/versenden"))
                .andExpect(status().isNoContent());
    }

    @Test
    void versendeEinzelnenLieferanten_konflikt_gibt409Zurueck() throws Exception {
        doThrow(new IllegalStateException("Mail-Versand fehlgeschlagen"))
                .when(preisanfrageService).versendeAnEinzelnenLieferanten(3L);

        mockMvc.perform(post("/api/preisanfragen/lieferant/3/versenden"))
                .andExpect(status().isConflict());
    }

    // ─────────────────────────────────────────────────────────────
    // GET /api/preisanfragen
    // ─────────────────────────────────────────────────────────────

    @Test
    void liste_ohneFilter_liefertAlle() throws Exception {
        Preisanfrage pa = buildPreisanfrage(1L);
        when(preisanfrageService.listeAlle(null)).thenReturn(List.of(pa));
        when(preisanfrageMapper.toResponseDto(pa)).thenReturn(buildResponseDto(1L));

        mockMvc.perform(get("/api/preisanfragen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nummer").value("PA-2026-001"));
    }

    @Test
    void liste_mitStatusFilter_liefertGefiltert() throws Exception {
        when(preisanfrageService.listeAlle(PreisanfrageStatus.OFFEN))
                .thenReturn(List.of(buildPreisanfrage(5L)));
        when(preisanfrageMapper.toResponseDto(any())).thenReturn(buildResponseDto(5L));

        mockMvc.perform(get("/api/preisanfragen").param("status", "OFFEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5));
    }

    // Security: SQLi-Payload im Filter-Parameter darf keinen 500/Leak ausloesen.
    @Test
    void liste_mitSqlInjectionPayload_behandeltAlsKeinFilter() throws Exception {
        when(preisanfrageService.listeAlle(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/preisanfragen")
                        .param("status", "'; DROP TABLE preisanfrage; --"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        verify(preisanfrageService, times(1)).listeAlle(null);
    }

    // ─────────────────────────────────────────────────────────────
    // GET /api/preisanfragen/{id}
    // ─────────────────────────────────────────────────────────────

    @Test
    void detail_happyPath() throws Exception {
        Preisanfrage pa = buildPreisanfrage(11L);
        when(preisanfrageService.findeById(11L)).thenReturn(pa);
        when(preisanfrageMapper.toResponseDto(pa)).thenReturn(buildResponseDto(11L));

        mockMvc.perform(get("/api/preisanfragen/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11));
    }

    @Test
    void detail_unbekannteId_gibt404Zurueck() throws Exception {
        when(preisanfrageService.findeById(123L))
                .thenThrow(new IllegalArgumentException("Preisanfrage nicht gefunden: 123"));

        mockMvc.perform(get("/api/preisanfragen/123"))
                .andExpect(status().isNotFound());
    }

    // Security: negative ID darf nicht 500 werfen.
    @Test
    void detail_negativeId_gibt404Zurueck() throws Exception {
        when(preisanfrageService.findeById(-1L))
                .thenThrow(new IllegalArgumentException("preisanfrageId muss positiv sein, war: -1"));

        mockMvc.perform(get("/api/preisanfragen/-1"))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────────────────────
    // GET /api/preisanfragen/{id}/vergleich
    // ─────────────────────────────────────────────────────────────

    @Test
    void vergleich_happyPath() throws Exception {
        PreisanfrageVergleichDto vgl = new PreisanfrageVergleichDto();
        vgl.setPreisanfrageId(8L);
        vgl.setNummer("PA-2026-001");
        when(preisanfrageService.getVergleich(8L)).thenReturn(vgl);

        mockMvc.perform(get("/api/preisanfragen/8/vergleich"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preisanfrageId").value(8));
    }

    @Test
    void vergleich_unbekannteId_gibt404Zurueck() throws Exception {
        when(preisanfrageService.getVergleich(404L))
                .thenThrow(new IllegalArgumentException("Preisanfrage nicht gefunden: 404"));

        mockMvc.perform(get("/api/preisanfragen/404/vergleich"))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────────────────────
    // GET /api/preisanfragen/lieferant/{palId}/pdf
    // ─────────────────────────────────────────────────────────────

    @Test
    void pdfForLieferant_happyPath(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("test.pdf");
        Files.write(pdf, new byte[] { 0x25, 0x50, 0x44, 0x46 }); // "%PDF"
        when(bestellungPdfService.generatePdfForPreisanfrage(5L)).thenReturn(pdf);

        mockMvc.perform(get("/api/preisanfragen/lieferant/5/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void pdfForLieferant_negativeId_gibt404Zurueck() throws Exception {
        mockMvc.perform(get("/api/preisanfragen/lieferant/-7/pdf"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(bestellungPdfService);
    }

    @Test
    void pdfForLieferant_unbekannteId_gibt404Zurueck() throws Exception {
        when(bestellungPdfService.generatePdfForPreisanfrage(9999L))
                .thenThrow(new IllegalArgumentException("PreisanfrageLieferant nicht gefunden: 9999"));

        mockMvc.perform(get("/api/preisanfragen/lieferant/9999/pdf"))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────────────────────
    // POST /api/preisanfragen/angebote
    // ─────────────────────────────────────────────────────────────

    @Test
    void eintragen_happyPath() throws Exception {
        PreisanfrageAngebot a = new PreisanfrageAngebot();
        a.setId(77L);
        when(preisanfrageService.eintragen(any())).thenReturn(a);

        String body = """
                {
                  "preisanfrageLieferantId": 1,
                  "preisanfragePositionId": 2,
                  "einzelpreis": 12.34
                }
                """;
        mockMvc.perform(post("/api/preisanfragen/angebote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(content().string("77"));
    }

    @Test
    void eintragen_negativerPreis_gibt400Zurueck() throws Exception {
        when(preisanfrageService.eintragen(any()))
                .thenThrow(new IllegalArgumentException("Einzelpreis darf nicht negativ sein"));

        String body = """
                {
                  "preisanfrageLieferantId": 1,
                  "preisanfragePositionId": 2,
                  "einzelpreis": -1
                }
                """;
        mockMvc.perform(post("/api/preisanfragen/angebote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // Security: nullable IDs im Body landen im Service, der eine klare Exception wirft.
    @Test
    void eintragen_nullIds_gibt400Zurueck() throws Exception {
        when(preisanfrageService.eintragen(any()))
                .thenThrow(new IllegalArgumentException("preisanfrageLieferantId muss positiv sein, war: null"));

        mockMvc.perform(post("/api/preisanfragen/angebote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"einzelpreis\": 10}"))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────
    // POST /api/preisanfragen/{id}/vergeben/{palId}
    // ─────────────────────────────────────────────────────────────

    @Test
    void vergeben_happyPath() throws Exception {
        Preisanfrage pa = buildPreisanfrage(3L);
        when(preisanfrageService.vergebeAuftrag(3L, 8L)).thenReturn(List.of());
        when(preisanfrageService.findeById(3L)).thenReturn(pa);
        when(preisanfrageMapper.toResponseDto(pa)).thenReturn(buildResponseDto(3L));

        mockMvc.perform(post("/api/preisanfragen/3/vergeben/8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3));
    }

    @Test
    void vergeben_unbekannteIds_gibt404Zurueck() throws Exception {
        doThrow(new IllegalArgumentException("Preisanfrage nicht gefunden: 1"))
                .when(preisanfrageService).vergebeAuftrag(anyLong(), anyLong());

        mockMvc.perform(post("/api/preisanfragen/1/vergeben/2"))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────────────────────
    // DELETE /api/preisanfragen/{id}
    // ─────────────────────────────────────────────────────────────

    @Test
    void abbrechen_happyPath() throws Exception {
        doNothing().when(preisanfrageService).abbrechen(6L);

        mockMvc.perform(delete("/api/preisanfragen/6"))
                .andExpect(status().isNoContent());
    }

    @Test
    void abbrechen_bereitsVergeben_gibt409Zurueck() throws Exception {
        doThrow(new IllegalStateException("Bereits vergebene Preisanfrage kann nicht mehr abgebrochen werden"))
                .when(preisanfrageService).abbrechen(eq(6L));

        mockMvc.perform(delete("/api/preisanfragen/6"))
                .andExpect(status().isConflict());
    }

    @Test
    void abbrechen_unbekannteId_gibt404Zurueck() throws Exception {
        doThrow(new IllegalArgumentException("Preisanfrage nicht gefunden: 9999"))
                .when(preisanfrageService).abbrechen(9999L);

        mockMvc.perform(delete("/api/preisanfragen/9999"))
                .andExpect(status().isNotFound());
    }

    // Dummy-Helfer, nicht direkt aufgerufen — verhindert "unused" Warnings bei buildLieferant.
    @Test
    void buildLieferantHelfer_istVerwendet() {
        PreisanfrageLieferant pal = buildLieferant(1L, "Max Mustermann Stahl GmbH");
        org.junit.jupiter.api.Assertions.assertEquals("PA-2026-001-ABCDE", pal.getToken());
    }

    // ─────────────────────────────────────────────────────────────
    // POST /api/preisanfragen/{id}/angebote/extrahieren
    // ─────────────────────────────────────────────────────────────

    @Test
    void extrahiereAngebote_happyPath_liefertErgebnis() throws Exception {
        PreisanfrageAngebotsExtraktionService.ExtraktionsErgebnis ergebnis =
                new PreisanfrageAngebotsExtraktionService.ExtraktionsErgebnis(
                        2, 5, 0, List.of("PAL#10: 3 Position(en) extrahiert"));
        when(angebotsExtraktionService.extrahiereFuerPreisanfrage(7L)).thenReturn(ergebnis);

        mockMvc.perform(post("/api/preisanfragen/7/angebote/extrahieren"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verarbeiteteLieferanten").value(2))
                .andExpect(jsonPath("$.extrahierteAngebote").value(5))
                .andExpect(jsonPath("$.fehler").value(0));
    }

    @Test
    void extrahiereAngebote_unbekannteId_gibt404Zurueck() throws Exception {
        when(angebotsExtraktionService.extrahiereFuerPreisanfrage(9999L))
                .thenThrow(new IllegalArgumentException("Preisanfrage nicht gefunden: 9999"));

        mockMvc.perform(post("/api/preisanfragen/9999/angebote/extrahieren"))
                .andExpect(status().isNotFound());
    }

    @Test
    void extrahiereAngebote_illegalState_gibt409Zurueck() throws Exception {
        when(angebotsExtraktionService.extrahiereFuerPreisanfrage(5L))
                .thenThrow(new IllegalStateException("Gemini lieferte leere Antwort fuer PAL 5"));

        mockMvc.perform(post("/api/preisanfragen/5/angebote/extrahieren"))
                .andExpect(status().isConflict());
    }
}
