package org.example.kalkulationsprogramm.controller.miete

import org.example.kalkulationsprogramm.dto.miete.AnnualAccountingResponseDto
import org.example.kalkulationsprogramm.mapper.MieteMapper
import org.example.kalkulationsprogramm.service.miete.MietabrechnungPdfService
import org.example.kalkulationsprogramm.service.miete.MietabrechnungService
import org.example.kalkulationsprogramm.service.miete.model.AnnualAccountingResult
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

@WebMvcTest(MietabrechnungController::class)
@AutoConfigureMockMvc(addFilters = false)
class MietabrechnungControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var mietabrechnungService: MietabrechnungService

    @MockBean
    private lateinit var mietabrechnungPdfService: MietabrechnungPdfService

    @MockBean
    private lateinit var mapper: MieteMapper

    @Nested
    @DisplayName("GET /api/miete/mietobjekte/{id}/jahresabrechnung")
    inner class GetJahresabrechnung {

        @Test
        @DisplayName("Gibt 200 mit Jahresabrechnung zurück")
        fun gibtJahresabrechnungZurueck() {
            val responseDto = AnnualAccountingResponseDto().apply {
                mietobjektId = 1L
                jahr = 2025
                gesamtkosten = BigDecimal("12000.00")
            }

            given(mietabrechnungService.berechneJahresabrechnung(eq(1L), eq(2025)))
                .willReturn(AnnualAccountingResult.builder().build())
            given(mapper.toDto(any(AnnualAccountingResult::class.java))).willReturn(responseDto)

            mockMvc.perform(
                get("/api/miete/mietobjekte/1/jahresabrechnung")
                    .param("jahr", "2025"),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.mietobjektId").value(1))
                .andExpect(jsonPath("$.jahr").value(2025))
        }

        @Test
        @DisplayName("Ungültige Mietobjekt-ID wird verarbeitet")
        fun ungueltigeMietobjektId() {
            given(mietabrechnungService.berechneJahresabrechnung(eq(-1L), eq(2025)))
                .willThrow(RuntimeException("Nicht gefunden"))

            assertThrows(Exception::class.java) {
                mockMvc.perform(
                    get("/api/miete/mietobjekte/-1/jahresabrechnung")
                        .param("jahr", "2025"),
                )
            }
        }
    }

    @Nested
    @DisplayName("GET /api/miete/mietobjekte/{id}/jahresabrechnung/pdf")
    inner class DownloadPdf {

        @Test
        @DisplayName("Gibt PDF-Datei zurück")
        fun gibtPdfZurueck() {
            val pdfBytes = byteArrayOf(0x25, 0x50, 0x44, 0x46)
            given(mietabrechnungPdfService.generatePdf(1L, 2025)).willReturn(pdfBytes)

            mockMvc.perform(
                get("/api/miete/mietobjekte/1/jahresabrechnung/pdf")
                    .param("jahr", "2025"),
            )
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(
                    header().string(
                        "Content-Disposition",
                        "attachment; filename=jahresabrechnung-1-2025.pdf",
                    ),
                )
        }
    }
}
