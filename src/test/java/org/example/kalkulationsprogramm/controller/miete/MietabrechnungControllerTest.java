package org.example.kalkulationsprogramm.controller.miete;

import org.example.kalkulationsprogramm.dto.miete.AnnualAccountingResponseDto;
import org.example.kalkulationsprogramm.mapper.MieteMapper;
import org.example.kalkulationsprogramm.service.miete.MietabrechnungPdfService;
import org.example.kalkulationsprogramm.service.miete.MietabrechnungService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MietabrechnungController.class)
@AutoConfigureMockMvc(addFilters = false)
class MietabrechnungControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MietabrechnungService mietabrechnungService;

    @MockBean
    private MietabrechnungPdfService mietabrechnungPdfService;

    @MockBean
    private MieteMapper mapper;

    @Nested
    @DisplayName("GET /api/miete/mietobjekte/{id}/jahresabrechnung")
    class GetJahresabrechnung {

        @Test
        @DisplayName("Gibt 200 mit Jahresabrechnung zurück")
        void gibtJahresabrechnungZurueck() throws Exception {
            AnnualAccountingResponseDto responseDto = new AnnualAccountingResponseDto();
            responseDto.setMietobjektId(1L);
            responseDto.setJahr(2025);
            responseDto.setGesamtkosten(new BigDecimal("12000.00"));

            given(mietabrechnungService.berechneJahresabrechnung(eq(1L), eq(2025)))
                    .willReturn(org.example.kalkulationsprogramm.service.miete.model.AnnualAccountingResult.builder().build());
            given(mapper.toDto(any(org.example.kalkulationsprogramm.service.miete.model.AnnualAccountingResult.class))).willReturn(responseDto);

            mockMvc.perform(get("/api/miete/mietobjekte/1/jahresabrechnung")
                            .param("jahr", "2025"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mietobjektId").value(1))
                    .andExpect(jsonPath("$.jahr").value(2025));
        }

        @Test
        @DisplayName("Ungültige Mietobjekt-ID wird verarbeitet")
        void ungueltigeMietobjektId() throws Exception {
            given(mietabrechnungService.berechneJahresabrechnung(eq(-1L), eq(2025)))
                    .willThrow(new RuntimeException("Nicht gefunden"));

            assertThrows(Exception.class, () ->
                    mockMvc.perform(get("/api/miete/mietobjekte/-1/jahresabrechnung")
                            .param("jahr", "2025"))
            );
        }
    }

    @Nested
    @DisplayName("GET /api/miete/mietobjekte/{id}/jahresabrechnung/pdf")
    class DownloadPdf {

        @Test
        @DisplayName("Gibt PDF-Datei zurück")
        void gibtPdfZurueck() throws Exception {
            byte[] pdfBytes = new byte[]{0x25, 0x50, 0x44, 0x46};
            given(mietabrechnungPdfService.generatePdf(1L, 2025)).willReturn(pdfBytes);

            mockMvc.perform(get("/api/miete/mietobjekte/1/jahresabrechnung/pdf")
                            .param("jahr", "2025"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                    .andExpect(header().string("Content-Disposition",
                            "attachment; filename=jahresabrechnung-1-2025.pdf"));
        }
    }
}
