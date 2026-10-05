package org.example.kalkulationsprogramm.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.example.kalkulationsprogramm.controller.advice.RestExceptionHandler;
import org.example.kalkulationsprogramm.exception.FirmenstammdatenUnvollstaendigException;
import org.example.kalkulationsprogramm.service.RechnungPdfService;
import org.example.kalkulationsprogramm.service.ZugferdErstellService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Vorab-Prüfung der Firmendaten, bevor eine Rechnung für die E-Rechnung gebucht wird. */
class DokumentGeneratorControllerZugferdPruefungTest {

    private ZugferdErstellService zugferdErstellService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        zugferdErstellService = mock(ZugferdErstellService.class);
        DokumentGeneratorController controller =
                new DokumentGeneratorController(mock(RechnungPdfService.class), zugferdErstellService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler(null))
                .build();
    }

    @Test
    void liefert204WennDieFirmendatenReichen() throws Exception {
        mvc.perform(get("/api/dokument-generator/zugferd-pruefung"))
                .andExpect(status().isNoContent());
    }

    @Test
    void liefert422MitMeldungWennFirmendatenFehlen() throws Exception {
        doThrow(new FirmenstammdatenUnvollstaendigException("die E-Rechnung", List.of("Straße", "Ort")))
                .when(zugferdErstellService).pruefeVerkaeuferdaten();

        mvc.perform(get("/api/dokument-generator/zugferd-pruefung"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        "Für die E-Rechnung fehlen Angaben zu Ihrem Betrieb: Straße, Ort. Bitte unter „Firma“ ergänzen."));
    }
}
