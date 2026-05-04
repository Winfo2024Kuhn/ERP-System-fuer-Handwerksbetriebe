package org.example.kalkulationsprogramm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageFunnelRequestDto;
import org.example.kalkulationsprogramm.service.AnfrageFunnelService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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

    @MockBean
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

    private AnfrageFunnelRequestDto validDto() {
        AnfrageFunnelRequestDto dto = new AnfrageFunnelRequestDto();
        dto.setServiceTyp("Neubau");
        dto.setProjektarten(List.of("Wohnhaus"));
        dto.setNachricht("Ich hätte gerne ein Angebot.");
        dto.setVorname("Max");
        dto.setNachname("Mustermann");
        dto.setEmail("max@example.de");
        dto.setTelefon("0170123");
        dto.setProjektAnschrift("Musterstraße 1, 12345 Musterstadt");
        dto.setDatenschutzAkzeptiert(true);
        return dto;
    }
}
