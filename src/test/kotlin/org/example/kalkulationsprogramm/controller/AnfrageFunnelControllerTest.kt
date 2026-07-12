package org.example.kalkulationsprogramm.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.example.kalkulationsprogramm.domain.Anfrage
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageFunnelRequestDto
import org.example.kalkulationsprogramm.service.AnfrageFunnelService
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AnfrageFunnelController::class)
@AutoConfigureMockMvc(addFilters = false)
class AnfrageFunnelControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var anfrageFunnelService: AnfrageFunnelService

    @Test
    fun multipartFunnelAnfrageWirdAngenommen() {
        val gespeichert = Anfrage().apply {
            id = 7L
        }
        given(anfrageFunnelService.verarbeiteFunnelAnfrage(any<AnfrageFunnelRequestDto>(), any()))
            .willReturn(gespeichert)

        val json = objectMapper.writeValueAsString(validDto())
        val anfragePart = MockMultipartFile(
            "anfrage",
            "anfrage.json",
            MediaType.APPLICATION_JSON_VALUE,
            json.toByteArray(),
        )
        val bild = MockMultipartFile(
            "bilder",
            "foto.jpg",
            MediaType.IMAGE_JPEG_VALUE,
            byteArrayOf(1, 2, 3),
        )

        mockMvc.perform(multipart("/api/internal/anfrage").file(anfragePart).file(bild))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.anfrageId").value(7))
    }

    @Test
    fun jsonFunnelAnfrageOhneDatenschutzWirdAbgelehnt() {
        val dto = validDto().apply {
            isDatenschutzAkzeptiert = false
        }

        mockMvc.perform(
            post("/api/internal/anfrage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun jsonFunnelAnfrageOhnePflichtfelderWirdAbgelehnt() {
        val dto = validDto().apply {
            email = null
        }

        mockMvc.perform(
            post("/api/internal/anfrage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)),
        )
            .andExpect(status().isBadRequest)
    }

    private fun validDto(): AnfrageFunnelRequestDto =
        AnfrageFunnelRequestDto().apply {
            serviceTyp = "Neubau"
            projektarten = listOf("Wohnhaus")
            nachricht = "Ich hätte gerne ein Angebot."
            vorname = "Max"
            nachname = "Mustermann"
            email = "max@example.de"
            telefon = "0170123"
            projektAnschrift = "Musterstraße 1, 12345 Musterstadt"
            isDatenschutzAkzeptiert = true
        }
}
