package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.example.kalkulationsprogramm.dto.Produktkategroie.KategorieAuswertungZeileDto;
import org.example.kalkulationsprogramm.dto.Produktkategroie.KategorieLoeschPruefungDto;
import org.example.kalkulationsprogramm.dto.Produktkategroie.KategorieLoeschenDto;
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieErstellenDto;
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieResponseDto;
import org.example.kalkulationsprogramm.service.ProduktkategorieAuswertungService;
import org.example.kalkulationsprogramm.service.ProduktkategorieService;
import org.example.kalkulationsprogramm.service.ProduktkategorieVerwaltungService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(ProduktkategorieController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProduktkategorieControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ProduktkategorieService produktkategorieService;
    @MockitoBean ProduktkategorieVerwaltungService verwaltungService;
    @MockitoBean ProduktkategorieAuswertungService auswertungService;

    private static ProduktkategorieResponseDto ordner(long id, String name) {
        ProduktkategorieResponseDto dto = new ProduktkategorieResponseDto();
        dto.setId(id);
        dto.setBezeichnung(name);
        dto.setLeaf(true);
        return dto;
    }

    @Test
    void auswertungMitJahrUndOrdner() throws Exception {
        given(auswertungService.werteAus(2026, 12L)).willReturn(List.of(new KategorieAuswertungZeileDto(
                13L, "Edelstahl", null, "Laufende Meter", true, 4, 31.5, 20, 1.58, 3)));

        mockMvc.perform(get("/api/produktkategorien/auswertung").param("jahr", "2026").param("parentId", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kategorieId").value(13))
                .andExpect(jsonPath("$[0].stundenProEinheit").value(1.58))
                .andExpect(jsonPath("$[0].leaf").value(true));
    }

    @Test
    void auswertungOhneParameterUndUngueltigesJahr() throws Exception {
        given(auswertungService.werteAus(null, null)).willReturn(List.of());
        mockMvc.perform(get("/api/produktkategorien/auswertung")).andExpect(status().isOk());

        given(auswertungService.werteAus(eq(1999), isNull()))
                .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte ein Jahr zwischen 2000 und 2100 wählen."));
        mockMvc.perform(get("/api/produktkategorien/auswertung").param("jahr", "1999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bitte ein Jahr zwischen 2000 und 2100 wählen."));
    }

    @Test
    void auswertungMitTextStattJahrIst400() throws Exception {
        mockMvc.perform(get("/api/produktkategorien/auswertung").param("jahr", "'; DROP TABLE x; --"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verschieben() throws Exception {
        given(verwaltungService.verschiebe(4L, 5L)).willReturn(ordner(4, "Tore"));
        mockMvc.perform(patch("/api/produktkategorien/4/verschieben").contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentId\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bezeichnung").value("Tore"));

        given(verwaltungService.verschiebe(4L, null)).willReturn(ordner(4, "Tore"));
        mockMvc.perform(patch("/api/produktkategorien/4/verschieben").contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentId\":null}"))
                .andExpect(status().isOk());
    }

    @Test
    void verschiebenInEigenenUnterordnerIst400() throws Exception {
        given(verwaltungService.verschiebe(1L, 3L)).willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Ein Ordner kann nicht in sich selbst oder in einen eigenen Unterordner verschoben werden."));

        mockMvc.perform(patch("/api/produktkategorien/1/verschieben").contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentId\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Ein Ordner kann nicht in sich selbst oder in einen eigenen Unterordner verschoben werden."));
    }

    @Test
    void loeschpruefung() throws Exception {
        given(verwaltungService.pruefeLoeschen(2L)).willReturn(new KategorieLoeschPruefungDto(2L, "Geländer",
                "Metallbau > Geländer", true, 1, 0, 1, 5,
                List.of(new KategorieLoeschPruefungDto.Unterordner(3L, "Edelstahl", "Metallbau > Geländer > Edelstahl")),
                List.of(),
                List.of(new KategorieLoeschPruefungDto.ProjektEintrag(9L, "Neubau Mustermann", "2026-014",
                        "Max Mustermann"))));

        mockMvc.perform(get("/api/produktkategorien/2/loeschpruefung"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hatBezuege").value(true))
                .andExpect(jsonPath("$.zeitbuchungAnzahl").value(5))
                .andExpect(jsonPath("$.projekte[0].kunde").value("Max Mustermann"))
                .andExpect(jsonPath("$.unterordner[0].pfad").value("Metallbau > Geländer > Edelstahl"));
    }

    @Test
    void loeschpruefungUnbekannterOrdnerIst404() throws Exception {
        given(verwaltungService.pruefeLoeschen(-1L))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Diesen Ordner gibt es nicht mehr."));

        mockMvc.perform(get("/api/produktkategorien/-1/loeschpruefung")).andExpect(status().isNotFound());
    }

    @Test
    void loeschenMitEntscheidung() throws Exception {
        mockMvc.perform(post("/api/produktkategorien/4/loeschen").contentType(MediaType.APPLICATION_JSON)
                .content("{\"modus\":\"UMZIEHEN\",\"zielKategorieId\":5}"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<KategorieLoeschenDto> auftrag = ArgumentCaptor.forClass(KategorieLoeschenDto.class);
        verify(verwaltungService).loesche(eq(4L), auftrag.capture(), any());
        org.assertj.core.api.Assertions.assertThat(auftrag.getValue())
                .isEqualTo(new KategorieLoeschenDto("UMZIEHEN", 5L));
    }

    @Test
    void loeschenMitUnbekanntemModusIst400() throws Exception {
        willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Bitte wählen Sie, ob der Inhalt umziehen oder mitgelöscht werden soll."))
                .given(verwaltungService).loesche(eq(4L), any(), any());

        mockMvc.perform(post("/api/produktkategorien/4/loeschen").contentType(MediaType.APPLICATION_JSON)
                .content("{\"modus\":\"<script>alert(1)</script>\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void einfachesLoeschenUndKonflikt() throws Exception {
        mockMvc.perform(delete("/api/produktkategorien/5")).andExpect(status().isNoContent());

        willThrow(new ResponseStatusException(HttpStatus.CONFLICT, "„Geländer“ wird noch verwendet."))
                .given(produktkategorieService).loescheKategorie(2L);
        mockMvc.perform(delete("/api/produktkategorien/2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("„Geländer“ wird noch verwendet."));
    }

    @Test
    void anlegenMitLeistungenMitnehmen() throws Exception {
        given(produktkategorieService.erstelleKategorie(any(), any())).willReturn(ordner(9, "Edelstahl"));

        mockMvc.perform(multipart("/api/produktkategorien")
                        .param("bezeichnung", "Edelstahl").param("parentId", "2").param("leistungenMitnehmen", "true"))
                .andExpect(status().isCreated());

        ArgumentCaptor<ProduktkategorieErstellenDto> dto = ArgumentCaptor.forClass(ProduktkategorieErstellenDto.class);
        verify(produktkategorieService).erstelleKategorie(dto.capture(), any());
        org.assertj.core.api.Assertions.assertThat(dto.getValue().isLeistungenMitnehmen()).isTrue();
        org.assertj.core.api.Assertions.assertThat(dto.getValue().getParentId()).isEqualTo(2L);
    }

    @Test
    void bearbeitenMitBildEntfernenUndFehlern() throws Exception {
        given(produktkategorieService.aktualisiereKategorie(eq(2L), any(), any())).willReturn(ordner(2, "Geländer"));
        mockMvc.perform(multipart("/api/produktkategorien/2").with(r -> { r.setMethod("PUT"); return r; })
                        .param("bezeichnung", "Geländer").param("bildEntfernen", "true"))
                .andExpect(status().isOk());
        ArgumentCaptor<ProduktkategorieErstellenDto> dto = ArgumentCaptor.forClass(ProduktkategorieErstellenDto.class);
        verify(produktkategorieService).aktualisiereKategorie(eq(2L), dto.capture(), any());
        org.assertj.core.api.Assertions.assertThat(dto.getValue().isBildEntfernen()).isTrue();

        given(produktkategorieService.aktualisiereKategorie(eq(3L), any(), any()))
                .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte geben Sie dem Ordner einen Namen."));
        mockMvc.perform(multipart("/api/produktkategorien/3")
                        .file(new MockMultipartFile("bild", "../../etc/passwd", "image/png", new byte[] { 1 }))
                        .with(r -> { r.setMethod("PUT"); return r; }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bitte geben Sie dem Ordner einen Namen."));
    }

    @Test
    void bestehendeLeseEndpunkte() throws Exception {
        given(produktkategorieService.findeAlleKategorien(true)).willReturn(List.of(ordner(1, "Metallbau")));
        given(produktkategorieService.findeHauptkategorien(false)).willReturn(List.of(ordner(1, "Metallbau")));
        given(produktkategorieService.findeUnterkategorie(1L, false)).willReturn(List.of(ordner(2, "Geländer")));
        given(produktkategorieService.sucheLeafKategorien("gel")).willReturn(List.of(ordner(2, "Geländer")));
        given(produktkategorieService.findeKategorieById(2L, false)).willReturn(ordner(2, "Geländer"));
        given(produktkategorieService.findeKategorieById(99L, false)).willThrow(new RuntimeException("weg"));
        given(produktkategorieService.analysiereKategorie(2L, 2026))
                .willReturn(new org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieAnalyseDto());

        mockMvc.perform(get("/api/produktkategorien").param("light", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].leaf").value(true));
        mockMvc.perform(get("/api/produktkategorien/haupt")).andExpect(status().isOk());
        mockMvc.perform(get("/api/produktkategorien/1/unterkategorien"))
                .andExpect(jsonPath("$[0].bezeichnung").value("Geländer"));
        mockMvc.perform(get("/api/produktkategorien/suche").param("q", "gel")).andExpect(status().isOk());
        mockMvc.perform(get("/api/produktkategorien/2")).andExpect(status().isOk());
        mockMvc.perform(get("/api/produktkategorien/99")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/produktkategorien/2/analyse").param("jahr", "2026")).andExpect(status().isOk());
    }

    @Test
    void beschreibungAendern() throws Exception {
        given(produktkategorieService.aktualisiereBeschreibung(2L, "<p>Text</p>")).willReturn(ordner(2, "Geländer"));
        given(produktkategorieService.aktualisiereBeschreibung(eq(99L), any())).willThrow(new RuntimeException("weg"));

        mockMvc.perform(patch("/api/produktkategorien/2/beschreibung").contentType(MediaType.APPLICATION_JSON)
                .content("{\"beschreibung\":\"<p>Text</p>\"}")).andExpect(status().isOk());
        mockMvc.perform(patch("/api/produktkategorien/99/beschreibung").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isNotFound());
    }
}
