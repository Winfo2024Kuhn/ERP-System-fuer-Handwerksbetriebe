package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.dto.WebsiteAnalytics.VerlaufPunktDto;
import org.example.kalkulationsprogramm.service.WebsiteAnalyticsSnapshotService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebsiteAnalyticsSnapshotController.class)
@AutoConfigureMockMvc(addFilters = false)
class WebsiteAnalyticsSnapshotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebsiteAnalyticsSnapshotService service;

    @Test
    void verlaufLiefertDiePunkteAlsListe() throws Exception {
        given(service.findVerlauf(anyInt())).willReturn(List.of(
                new VerlaufPunktDto(LocalDate.of(2026, 8, 1), 12, 120, 360, 2, 4),
                new VerlaufPunktDto(LocalDate.of(2026, 8, 2), 15, 135, 400, 3, 5)));

        mockMvc.perform(get("/api/website-analytics/verlauf?tage=30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].besucherAmTag").value(12))
                .andExpect(jsonPath("$[1].snapshotDate").value("2026-08-02"));
    }

    @Test
    void ohneParameterWerden30TageAngefragt() throws Exception {
        given(service.findVerlauf(anyInt())).willReturn(List.of());

        mockMvc.perform(get("/api/website-analytics/verlauf"))
                .andExpect(status().isOk());

        verify(service).findVerlauf(30);
    }

    @Test
    void latestLiefert204WennNochNichtsDaIst() throws Exception {
        given(service.findLatest()).willReturn(Optional.empty());

        mockMvc.perform(get("/api/website-analytics/latest"))
                .andExpect(status().isNoContent());
    }
}
