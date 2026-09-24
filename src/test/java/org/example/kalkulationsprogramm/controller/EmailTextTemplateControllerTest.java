package org.example.kalkulationsprogramm.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.containsString;

import java.util.List;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVorlagenDto.Platzhalter;
import org.example.kalkulationsprogramm.service.EmailTextTemplateService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufVorlagenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EmailTextTemplateControllerTest {
    private final EmailTextTemplateService templates = org.mockito.Mockito.mock(EmailTextTemplateService.class);
    private final EinkaufVorlagenService einkauf = org.mockito.Mockito.mock(EinkaufVorlagenService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new EmailTextTemplateController(templates, einkauf)).build();
    }

    @Test
    void dokumenttypenBietenEinkaufskategorieUndGetrennteAnfragetypen() throws Exception {
        mvc.perform(get("/api/email-textvorlagen/dokumenttypen"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"value\":\"EINKAUF_ANFRAGE\",\"label\":\"Einkauf")))
                .andExpect(content().string(containsString("\"kategorie\":\"EINKAUF\"")))
                .andExpect(content().string(containsString("\"value\":\"EINKAUF_BESTELLUNG\"")));
    }

    @Test
    void placeholdersWerdenJeEinkaufsvorlagenartAbgefragt() throws Exception {
        given(einkauf.placeholders("EINKAUF_ANFRAGE"))
                .willReturn(List.of(new Platzhalter("ANFRAGENUMMER", "Anfragenummer", true, true)));

        mvc.perform(get("/api/email-textvorlagen/placeholders/EINKAUF_ANFRAGE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].token").value("ANFRAGENUMMER"));
    }

    @Test
    void serverVorschauRendertUndBlockiertUngueltigeVorlageVorPersistenz() throws Exception {
        var realRenderer = new EinkaufVorlagenService(org.mockito.Mockito.mock(
                org.example.kalkulationsprogramm.repository.EmailTextTemplateRepository.class));
        mvc = MockMvcBuilders.standaloneSetup(new EmailTextTemplateController(templates, realRenderer)).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/email-textvorlagen/einkauf-vorschau")
                .contentType("application/json").content("""
                    {"dokumentTyp":"EINKAUF_ANFRAGE","subjectTemplate":"Anfrage {{ANFRAGENUMMER}}","htmlBody":"<p>{{POSITIONEN}}</p>"}
                    """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subject").value("Anfrage PA-2026-00001"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/email-textvorlagen")
                .contentType("application/json").content("""
                    {"dokumentTyp":" einkauf_anfrage ","name":"Dummy","subjectTemplate":"{{PA_NUMMER}}","htmlBody":"Text"}
                    """))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(templates);
    }
}
