package org.example.kalkulationsprogramm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.dto.Beitraege.BeitragDetailDto;
import org.example.kalkulationsprogramm.dto.Beitraege.BeitragSummaryDto;
import org.example.kalkulationsprogramm.dto.Beitraege.BeitragUpsertRequest;
import org.example.kalkulationsprogramm.service.BeitraegeWebsiteClient;
import org.example.kalkulationsprogramm.service.BeitraegeWebsiteException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deckt alle Endpoints von {@link BeitraegeController} ab: je Endpoint
 * Happy-Path und Fehlerfall (eine vom gemockten {@link BeitraegeWebsiteClient}
 * geworfene {@link BeitraegeWebsiteException} muss als HTTP 502 herauskommen).
 * Fuer den Bild-Endpunkt zusaetzlich die Sicherheits-Header (nosniff,
 * cachePrivate) sowie die 502-statt-500-Absicherung bei einem von der
 * Website gelieferten, nicht parsbaren Content-Type.
 */
@WebMvcTest(BeitraegeController.class)
@AutoConfigureMockMvc(addFilters = false)
class BeitraegeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BeitraegeWebsiteClient beitraegeWebsiteClient;

    // --- GET /api/beitraege/bild/{dateiname} ---

    @Test
    void bild_gueltigerDateiname_liefertBildMitSicherheitsHeadern() throws Exception {
        byte[] bytes = {1, 2, 3, 4};
        given(beitraegeWebsiteClient.holeBild("abc123.webp"))
                .willReturn(new BeitraegeWebsiteClient.BildAntwort(bytes, "image/webp"));

        mockMvc.perform(get("/api/beitraege/bild/abc123.webp"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.parseMediaType("image/webp")))
                .andExpect(content().bytes(bytes))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(header().string("Cache-Control", not(containsString("public"))));
    }

    @Test
    void bild_abgelehnterDateiname_liefert502() throws Exception {
        given(beitraegeWebsiteClient.holeBild("boese.js"))
                .willThrow(new BeitraegeWebsiteException("Unzulaessiger Dateiname fuer ein Beitragsbild."));

        mockMvc.perform(get("/api/beitraege/bild/boese.js"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void bild_ungueltigerContentTypeVonDerWebsite_liefert502StattServerFehler() throws Exception {
        byte[] bytes = {1};
        given(beitraegeWebsiteClient.holeBild("abc123.webp"))
                .willReturn(new BeitraegeWebsiteClient.BildAntwort(bytes, "kaputt"));

        mockMvc.perform(get("/api/beitraege/bild/abc123.webp"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false));
    }

    // --- GET /api/beitraege ---

    @Test
    void liste_happyPath_liefertBeitraege() throws Exception {
        BeitragSummaryDto summary = new BeitragSummaryDto();
        summary.setId(1L);
        summary.setSlug("erster-beitrag");
        summary.setTitle("Erster Beitrag");
        summary.setExcerpt("Kurzfassung");
        summary.setStatus("published");
        given(beitraegeWebsiteClient.listeAlle()).willReturn(List.of(summary));

        mockMvc.perform(get("/api/beitraege"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Erster Beitrag"));
    }

    @Test
    void liste_clientWirftException_liefert502() throws Exception {
        given(beitraegeWebsiteClient.listeAlle())
                .willThrow(new BeitraegeWebsiteException("Website nicht erreichbar."));

        mockMvc.perform(get("/api/beitraege"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false));
    }

    // --- GET /api/beitraege/{id} ---

    @Test
    void detail_happyPath_liefertBeitrag() throws Exception {
        BeitragDetailDto detail = new BeitragDetailDto();
        detail.setId(5L);
        detail.setSlug("detail");
        detail.setTitle("Detail-Beitrag");
        detail.setExcerpt("Kurz");
        detail.setStatus("draft");
        detail.setContent("Volltext");
        given(beitraegeWebsiteClient.hole(5L)).willReturn(detail);

        mockMvc.perform(get("/api/beitraege/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.content").value("Volltext"));
    }

    @Test
    void detail_clientWirftException_liefert502() throws Exception {
        given(beitraegeWebsiteClient.hole(999L))
                .willThrow(new BeitraegeWebsiteException("Website-API antwortete mit HTTP 404", 404));

        mockMvc.perform(get("/api/beitraege/999"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false));
    }

    // --- POST /api/beitraege ---

    @Test
    void anlegen_happyPath_liefertNeuenBeitrag() throws Exception {
        BeitragUpsertRequest request = new BeitragUpsertRequest();
        request.setTitle("Neu");
        request.setExcerpt("E");
        request.setContent("C");

        BeitragDetailDto detail = new BeitragDetailDto();
        detail.setId(9L);
        detail.setSlug("neu");
        detail.setTitle("Neu");
        detail.setExcerpt("E");
        detail.setStatus("draft");
        detail.setContent("C");
        given(beitraegeWebsiteClient.anlegen(any(BeitragUpsertRequest.class))).willReturn(detail);

        mockMvc.perform(post("/api/beitraege")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9));
    }

    @Test
    void anlegen_clientWirftException_liefert502() throws Exception {
        BeitragUpsertRequest request = new BeitragUpsertRequest();
        request.setTitle("Neu");
        request.setExcerpt("E");
        request.setContent("C");

        given(beitraegeWebsiteClient.anlegen(any(BeitragUpsertRequest.class)))
                .willThrow(new BeitraegeWebsiteException("Website nicht erreichbar."));

        mockMvc.perform(post("/api/beitraege")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false));
    }

    // --- POST /api/beitraege/{id}/status ---

    @Test
    void status_happyPath_liefertAktualisiertenBeitrag() throws Exception {
        BeitragDetailDto detail = new BeitragDetailDto();
        detail.setId(3L);
        detail.setSlug("s");
        detail.setTitle("T");
        detail.setExcerpt("E");
        detail.setStatus("published");
        detail.setContent("C");
        given(beitraegeWebsiteClient.statusSetzen(eq(3L), eq("published"))).willReturn(detail);

        mockMvc.perform(post("/api/beitraege/3/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"published\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("published"));
    }

    @Test
    void status_clientWirftException_liefert502() throws Exception {
        given(beitraegeWebsiteClient.statusSetzen(anyLong(), anyString()))
                .willThrow(new BeitraegeWebsiteException("Website nicht erreichbar."));

        mockMvc.perform(post("/api/beitraege/3/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"published\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false));
    }

    // --- DELETE /api/beitraege/{id}/bilder/{imageId} ---

    @Test
    void bildLoeschen_happyPath_liefertAktualisiertenBeitrag() throws Exception {
        BeitragDetailDto detail = new BeitragDetailDto();
        detail.setId(8L);
        detail.setSlug("d");
        detail.setTitle("T");
        detail.setExcerpt("E");
        detail.setStatus("draft");
        detail.setContent("C");
        given(beitraegeWebsiteClient.bildLoeschen(8L, 15L)).willReturn(detail);

        mockMvc.perform(delete("/api/beitraege/8/bilder/15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(8));
    }

    @Test
    void bildLoeschen_clientWirftException_liefert502() throws Exception {
        given(beitraegeWebsiteClient.bildLoeschen(anyLong(), anyLong()))
                .willThrow(new BeitraegeWebsiteException("Website nicht erreichbar."));

        mockMvc.perform(delete("/api/beitraege/8/bilder/15"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false));
    }
}
