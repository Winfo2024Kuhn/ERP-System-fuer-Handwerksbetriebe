package org.example.kalkulationsprogramm.controller;

import tools.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageFunnelRequestDto;
import org.example.kalkulationsprogramm.service.AnfrageFunnelService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnfrageFunnelController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnfrageFunnelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AnfrageFunnelService anfrageFunnelService;

    @Test
    void multipartFunnelAnfrageWirdAngenommen() throws Exception {
        Anfrage gespeichert = new Anfrage();
        gespeichert.setId(7L);
        given(anfrageFunnelService.verarbeiteFunnelAnfrage(any(AnfrageFunnelRequestDto.class), any()))
                .willReturn(gespeichert);

        String json = objectMapper.writeValueAsString(validDto());
        MockMultipartFile anfragePart = new MockMultipartFile("anfrage", "anfrage.json",
                MediaType.APPLICATION_JSON_VALUE, json.getBytes());
        MockMultipartFile bild = new MockMultipartFile("bilder", "foto.jpg",
                MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/internal/anfrage").file(anfragePart).file(bild))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.anfrageId").value(7));
    }

    @Test
    void jsonFunnelAnfrageOhneDatenschutzWirdAbgelehnt() throws Exception {
        AnfrageFunnelRequestDto dto = validDto();
        dto.setDatenschutzAkzeptiert(false);

        mockMvc.perform(post("/api/internal/anfrage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void jsonFunnelAnfrageOhnePflichtfelderWirdAbgelehnt() throws Exception {
        AnfrageFunnelRequestDto dto = validDto();
        dto.setEmail(null);

        mockMvc.perform(post("/api/internal/anfrage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void multipartFunnelAnfrageOhnePflichtfelderWirdMit400Abgelehnt() throws Exception {
        mockMvc.perform(multipart("/api/internal/anfrage")
                        .file(anfragePart("{\"datenschutzAkzeptiert\":true}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Die Anfrage ist unvollständig oder ungültig."))
                .andExpect(jsonPath("$.felder", hasItems("vorname", "nachname", "email", "nachricht", "serviceTyp")));

        then(anfrageFunnelService).should(never()).verarbeiteFunnelAnfrage(any(), any());
    }

    @Test
    void multipartFunnelAnfrageOhneDatenschutzWirdAbgelehnt() throws Exception {
        AnfrageFunnelRequestDto dto = validDto();
        dto.setDatenschutzAkzeptiert(false);

        mockMvc.perform(multipart("/api/internal/anfrage")
                        .file(anfragePart(objectMapper.writeValueAsString(dto))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Datenschutz muss akzeptiert werden."))
                .andExpect(jsonPath("$.felder", hasItems("datenschutzAkzeptiert")));

        then(anfrageFunnelService).should(never()).verarbeiteFunnelAnfrage(any(), any());
    }

    @Test
    void multipartFunnelAnfrageMitKaputtemJsonWirdAbgelehnt() throws Exception {
        mockMvc.perform(multipart("/api/internal/anfrage").file(anfragePart("{kein json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Anfrage-Payload ist kein gültiges JSON."));

        then(anfrageFunnelService).should(never()).verarbeiteFunnelAnfrage(any(), any());
    }

    @Test
    void multipartFunnelAnfrageMitNullPayloadWirdAbgelehnt() throws Exception {
        mockMvc.perform(multipart("/api/internal/anfrage").file(anfragePart("null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Anfrage-Payload fehlt."));

        then(anfrageFunnelService).should(never()).verarbeiteFunnelAnfrage(any(), any());
    }

    @Test
    void multipartFunnelAnfrageMitNachrichtUeberDerGrenzeWirdAbgelehnt() throws Exception {
        AnfrageFunnelRequestDto dto = validDto();
        dto.setNachricht("x".repeat(5_001));

        mockMvc.perform(multipart("/api/internal/anfrage")
                        .file(anfragePart(objectMapper.writeValueAsString(dto))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.felder", hasItems("nachricht")));

        then(anfrageFunnelService).should(never()).verarbeiteFunnelAnfrage(any(), any());
    }

    @Test
    void multipartFunnelAnfrageMitNachrichtGenauAnDerGrenzeWirdAngenommen() throws Exception {
        Anfrage gespeichert = new Anfrage();
        gespeichert.setId(9L);
        given(anfrageFunnelService.verarbeiteFunnelAnfrage(any(AnfrageFunnelRequestDto.class), any()))
                .willReturn(gespeichert);
        AnfrageFunnelRequestDto dto = validDto();
        dto.setNachricht("x".repeat(5_000));

        mockMvc.perform(multipart("/api/internal/anfrage")
                        .file(anfragePart(objectMapper.writeValueAsString(dto))))
                .andExpect(status().isCreated());
    }

    @Test
    void multipartFunnelAnfrageReichtSkriptUndSqlInAllenTextfeldernUnveraendertWeiter() throws Exception {
        Anfrage gespeichert = new Anfrage();
        gespeichert.setId(8L);
        given(anfrageFunnelService.verarbeiteFunnelAnfrage(any(AnfrageFunnelRequestDto.class), any()))
                .willReturn(gespeichert);
        String xss = "<script>alert(1)</script>";
        String sql = "'; DROP TABLE kunde; --";
        AnfrageFunnelRequestDto dto = validDto();
        dto.setServiceTyp(xss);
        dto.setProjektarten(List.of(xss, sql));
        dto.setNachricht(xss);
        dto.setVorname(sql);
        dto.setNachname(xss);
        dto.setTelefon(sql);
        dto.setProjektAnschrift(xss);
        dto.setRechnungsAnschrift(sql);
        dto.setConsentIp(sql);
        dto.setDatenschutzVersion(xss);

        mockMvc.perform(multipart("/api/internal/anfrage")
                        .file(anfragePart(objectMapper.writeValueAsString(dto))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.anfrageId").value(8));

        ArgumentCaptor<AnfrageFunnelRequestDto> captor = ArgumentCaptor.forClass(AnfrageFunnelRequestDto.class);
        then(anfrageFunnelService).should().verarbeiteFunnelAnfrage(captor.capture(), any());
        AnfrageFunnelRequestDto angekommen = captor.getValue();
        assertThat(angekommen.getServiceTyp()).isEqualTo(xss);
        assertThat(angekommen.getProjektarten()).containsExactly(xss, sql);
        assertThat(angekommen.getNachricht()).isEqualTo(xss);
        assertThat(angekommen.getVorname()).isEqualTo(sql);
        assertThat(angekommen.getNachname()).isEqualTo(xss);
        assertThat(angekommen.getTelefon()).isEqualTo(sql);
        assertThat(angekommen.getProjektAnschrift()).isEqualTo(xss);
        assertThat(angekommen.getRechnungsAnschrift()).isEqualTo(sql);
        assertThat(angekommen.getConsentIp()).isEqualTo(sql);
        assertThat(angekommen.getDatenschutzVersion()).isEqualTo(xss);
    }

    private MockMultipartFile anfragePart(String json) {
        return new MockMultipartFile("anfrage", "anfrage.json",
                MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }

    private AnfrageFunnelRequestDto validDto() {
        AnfrageFunnelRequestDto dto = new AnfrageFunnelRequestDto();
        dto.setServiceTyp("Neubau");
        dto.setProjektarten(List.of("Wohnhaus"));
        dto.setNachricht("Ich hätte gerne ein Angebot.");
        dto.setVorname("Max");
        dto.setNachname("Mustermann");
        dto.setEmail("max.mustermann@example.com");
        dto.setTelefon("0170123");
        dto.setProjektAnschrift("Musterstraße 1, 12345 Musterstadt");
        dto.setDatenschutzAkzeptiert(true);
        return dto;
    }
}
