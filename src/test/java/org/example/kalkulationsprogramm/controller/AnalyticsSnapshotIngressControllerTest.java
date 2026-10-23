package org.example.kalkulationsprogramm.controller;

import tools.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.WebsiteAnalyticsSnapshot;
import org.example.kalkulationsprogramm.dto.WebsiteAnalytics.AnalyticsSnapshotRequestDto;
import org.example.kalkulationsprogramm.service.WebsiteAnalyticsSnapshotService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsSnapshotIngressController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalyticsSnapshotIngressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WebsiteAnalyticsSnapshotService service;

    @Test
    void gueltigerSnapshotWirdAngenommen() throws Exception {
        WebsiteAnalyticsSnapshot saved = new WebsiteAnalyticsSnapshot();
        saved.setId(42L);
        saved.setSnapshotDate(LocalDate.of(2026, 5, 8));
        given(service.upsert(any(AnalyticsSnapshotRequestDto.class), any())).willReturn(saved);

        String json = objectMapper.writeValueAsString(validDto());

        mockMvc.perform(post("/api/internal/analytics-snapshot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.snapshotDate").value("2026-05-08"))
                .andExpect(jsonPath("$.id").value(42));
    }

    @Test
    void fehlendeSchemaVersionWirdAbgelehnt() throws Exception {
        AnalyticsSnapshotRequestDto dto = validDto();
        dto.setSchemaVersion(null);
        String json = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("/api/internal/analytics-snapshot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conversionAusserhalb0Bis100WirdAbgelehnt() throws Exception {
        AnalyticsSnapshotRequestDto dto = validDto();
        dto.setConversion(150);
        String json = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("/api/internal/analytics-snapshot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void negativeTotalsWerdenAbgelehnt() throws Exception {
        AnalyticsSnapshotRequestDto dto = validDto();
        dto.getTotals().setVisitors(-5);
        String json = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("/api/internal/analytics-snapshot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void zuGrosserBodyWirdMit413Abgelehnt() throws Exception {
        StringBuilder bigJson = new StringBuilder("{\"junk\":\"");
        bigJson.append("x".repeat(1_000_001));
        bigJson.append("\"}");

        mockMvc.perform(post("/api/internal/analytics-snapshot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bigJson.toString()))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void leererBodyWirdAbgelehnt() throws Exception {
        mockMvc.perform(post("/api/internal/analytics-snapshot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private AnalyticsSnapshotRequestDto validDto() {
        AnalyticsSnapshotRequestDto dto = new AnalyticsSnapshotRequestDto();
        dto.setSchemaVersion(1);
        dto.setSnapshotDate(LocalDate.of(2026, 5, 8));
        dto.setGeneratedAt(OffsetDateTime.of(2026, 5, 9, 1, 0, 0, 0, ZoneOffset.UTC));
        AnalyticsSnapshotRequestDto.Totals totals = new AnalyticsSnapshotRequestDto.Totals();
        totals.setVisitors(100);
        totals.setPageviews(250);
        totals.setLeadsPhone(3);
        totals.setLeadsMail(1);
        totals.setSubmissions(2);
        dto.setTotals(totals);
        dto.setVisitorsToday(5);
        dto.setVisitorsYesterday(12);
        dto.setConversion(2);
        AnalyticsSnapshotRequestDto.FunnelStep step = new AnalyticsSnapshotRequestDto.FunnelStep();
        step.setName("Pageview");
        step.setLabel("Website besucht");
        step.setCount(100);
        dto.setFunnel(List.of(step));
        return dto;
    }
}
