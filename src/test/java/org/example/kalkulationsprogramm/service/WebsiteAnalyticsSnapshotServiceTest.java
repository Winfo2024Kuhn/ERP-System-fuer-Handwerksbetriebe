package org.example.kalkulationsprogramm.service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.example.kalkulationsprogramm.domain.WebsiteAnalyticsSnapshot;
import org.example.kalkulationsprogramm.dto.WebsiteAnalytics.AnalyticsSnapshotRequestDto;
import org.example.kalkulationsprogramm.dto.WebsiteAnalytics.AnalyticsSnapshotResponseDto;
import org.example.kalkulationsprogramm.repository.WebsiteAnalyticsSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebsiteAnalyticsSnapshotServiceTest {

    @Mock
    private WebsiteAnalyticsSnapshotRepository repository;

    @Spy
    private ObjectMapper objectMapper = JsonMapper.builderWithJackson2Defaults().build();

    @InjectMocks
    private WebsiteAnalyticsSnapshotService service;

    private AnalyticsSnapshotRequestDto dto;

    @BeforeEach
    void setUp() {
        dto = new AnalyticsSnapshotRequestDto();
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

        AnalyticsSnapshotRequestDto.TopPage page = new AnalyticsSnapshotRequestDto.TopPage();
        page.setPath("/");
        page.setCount(80);
        dto.setTopPages(List.of(page));
    }

    @Test
    void neuerSnapshotWirdGespeichert() {
        given(repository.findBySnapshotDate(dto.getSnapshotDate())).willReturn(Optional.empty());
        given(repository.save(any(WebsiteAnalyticsSnapshot.class)))
                .willAnswer(inv -> inv.getArgument(0));

        WebsiteAnalyticsSnapshot saved = service.upsert(dto, "{\"raw\":true}");

        assertThat(saved.getSnapshotDate()).isEqualTo(LocalDate.of(2026, 5, 8));
        assertThat(saved.getTotalsVisitors()).isEqualTo(100);
        assertThat(saved.getConversion()).isEqualTo(2);
        assertThat(saved.getFunnelJson()).contains("Pageview").contains("Website besucht");
        assertThat(saved.getTopPagesJson()).contains("/").contains("80");
        assertThat(saved.getRawPayload()).isEqualTo("{\"raw\":true}");
    }

    @Test
    void erneuterPushFuerSelbenTagUeberschreibtBestehendenDatensatz() {
        WebsiteAnalyticsSnapshot bestehend = new WebsiteAnalyticsSnapshot();
        bestehend.setId(1L);
        bestehend.setSnapshotDate(dto.getSnapshotDate());
        bestehend.setGeneratedAt(LocalDateTime.of(2026, 5, 9, 0, 30));
        bestehend.setTotalsVisitors(50);
        given(repository.findBySnapshotDate(dto.getSnapshotDate())).willReturn(Optional.of(bestehend));
        given(repository.save(any(WebsiteAnalyticsSnapshot.class)))
                .willAnswer(inv -> inv.getArgument(0));

        WebsiteAnalyticsSnapshot saved = service.upsert(dto, "{}");

        ArgumentCaptor<WebsiteAnalyticsSnapshot> captor = ArgumentCaptor.forClass(WebsiteAnalyticsSnapshot.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L);
        assertThat(saved.getTotalsVisitors()).isEqualTo(100);
    }

    @Test
    void aelterePushAnfrageWirdVerworfen() {
        WebsiteAnalyticsSnapshot bestehend = new WebsiteAnalyticsSnapshot();
        bestehend.setId(1L);
        bestehend.setSnapshotDate(dto.getSnapshotDate());
        bestehend.setGeneratedAt(LocalDateTime.of(2026, 5, 9, 2, 0));
        bestehend.setTotalsVisitors(999);
        given(repository.findBySnapshotDate(dto.getSnapshotDate())).willReturn(Optional.of(bestehend));

        WebsiteAnalyticsSnapshot result = service.upsert(dto, "{}");

        verify(repository, never()).save(any());
        assertThat(result.getTotalsVisitors()).isEqualTo(999);
    }

    @Test
    void fehlendePflichtfelderWerdenAbgelehnt() {
        AnalyticsSnapshotRequestDto invalid = new AnalyticsSnapshotRequestDto();
        invalid.setSchemaVersion(1);
        // snapshotDate, generatedAt, totals fehlen
        try {
            service.upsert(invalid, "{}");
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage()).contains("Pflichtfelder");
            return;
        }
        throw new AssertionError("IllegalArgumentException erwartet");
    }

    @Test
    void zukuenftigeSchemaVersionWirdGespeichertAberGeloggt() {
        dto.setSchemaVersion(99);
        given(repository.findBySnapshotDate(dto.getSnapshotDate())).willReturn(Optional.empty());
        given(repository.save(any(WebsiteAnalyticsSnapshot.class)))
                .willAnswer(inv -> inv.getArgument(0));

        WebsiteAnalyticsSnapshot saved = service.upsert(dto, "{}");

        assertThat(saved.getSchemaVersion()).isEqualTo(99);
        assertThat(saved.getTotalsVisitors()).isEqualTo(100);
    }

    @Test
    void kaputtesFunnelJsonFuehrtZuLeererListeStattCrash() {
        WebsiteAnalyticsSnapshot entity = new WebsiteAnalyticsSnapshot();
        entity.setSnapshotDate(LocalDate.of(2026, 5, 8));
        entity.setSchemaVersion(1);
        entity.setGeneratedAt(LocalDateTime.of(2026, 5, 9, 1, 0));
        entity.setReceivedAt(LocalDateTime.of(2026, 5, 9, 1, 0));
        entity.setFunnelJson("{not-an-array");
        entity.setTopPagesJson(null);
        entity.setDevicesJson("garbage");
        entity.setBrowsersJson(null);
        entity.setCitiesJson(null);
        given(repository.findFirstByOrderBySnapshotDateDesc()).willReturn(Optional.of(entity));

        AnalyticsSnapshotResponseDto response = service.findLatest().orElseThrow();

        assertThat(response.getFunnel()).isEmpty();
        assertThat(response.getDevices()).isEmpty();
    }

    @Test
    void findLatestLiefertResponseDtoMitGeparstenListen() {
        WebsiteAnalyticsSnapshot entity = new WebsiteAnalyticsSnapshot();
        entity.setId(1L);
        entity.setSnapshotDate(LocalDate.of(2026, 5, 8));
        entity.setSchemaVersion(1);
        entity.setGeneratedAt(LocalDateTime.of(2026, 5, 9, 1, 0));
        entity.setReceivedAt(LocalDateTime.of(2026, 5, 9, 1, 0, 5));
        entity.setTotalsVisitors(100);
        entity.setTotalsPageviews(250);
        entity.setTotalsLeadsPhone(3);
        entity.setTotalsLeadsMail(1);
        entity.setTotalsSubmissions(2);
        entity.setVisitorsToday(5);
        entity.setVisitorsYesterday(12);
        entity.setConversion(2);
        entity.setFunnelJson("[{\"name\":\"Pageview\",\"label\":\"Website besucht\",\"count\":100}]");
        entity.setTopPagesJson("[{\"path\":\"/\",\"count\":80}]");
        entity.setDevicesJson("[]");
        entity.setBrowsersJson(null);
        entity.setCitiesJson("");
        given(repository.findFirstByOrderBySnapshotDateDesc()).willReturn(Optional.of(entity));

        AnalyticsSnapshotResponseDto response = service.findLatest().orElseThrow();

        assertThat(response.getTotals().getVisitors()).isEqualTo(100);
        assertThat(response.getFunnel()).hasSize(1);
        assertThat(response.getFunnel().get(0).getName()).isEqualTo("Pageview");
        assertThat(response.getFunnel().get(0).getCount()).isEqualTo(100);
        assertThat(response.getTopPages()).hasSize(1);
        assertThat(response.getTopPages().get(0).getPath()).isEqualTo("/");
        assertThat(response.getDevices()).isEmpty();
        assertThat(response.getBrowsers()).isEmpty();
        assertThat(response.getCities()).isEmpty();
    }
}
