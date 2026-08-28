package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.WebsiteAnalyticsSnapshot;
import org.example.kalkulationsprogramm.dto.WebsiteAnalytics.VerlaufPunktDto;
import org.example.kalkulationsprogramm.repository.WebsiteAnalyticsSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebsiteAnalyticsSnapshotVerlaufTest {

    private WebsiteAnalyticsSnapshotRepository repository;
    private WebsiteAnalyticsSnapshotService service;

    @BeforeEach
    void setUp() {
        repository = mock(WebsiteAnalyticsSnapshotRepository.class);
        service = new WebsiteAnalyticsSnapshotService(repository, new ObjectMapper());
    }

    private WebsiteAnalyticsSnapshot schnappschuss(LocalDate tag, long besucherAmTag) {
        WebsiteAnalyticsSnapshot e = new WebsiteAnalyticsSnapshot();
        e.setSnapshotDate(tag);
        e.setVisitorsToday(besucherAmTag);
        e.setTotalsVisitors(besucherAmTag * 10);
        e.setTotalsPageviews(besucherAmTag * 30);
        e.setTotalsSubmissions(2);
        e.setConversion(4);
        return e;
    }

    @Test
    void verlaufUebernimmtDieWerteDesSchnappschusses() {
        when(repository.findBySnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(any()))
                .thenReturn(List.of(schnappschuss(LocalDate.of(2026, 8, 1), 12)));

        List<VerlaufPunktDto> verlauf = service.findVerlauf(30);

        assertThat(verlauf).hasSize(1);
        assertThat(verlauf.get(0).snapshotDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(verlauf.get(0).besucherAmTag()).isEqualTo(12);
        assertThat(verlauf.get(0).besucherGesamt()).isEqualTo(120);
        assertThat(verlauf.get(0).seitenaufrufeGesamt()).isEqualTo(360);
        assertThat(verlauf.get(0).anfragenGesamt()).isEqualTo(2);
        assertThat(verlauf.get(0).conversion()).isEqualTo(4);
    }

    @Test
    void zuGrosseTageszahlWirdAufEinJahrBegrenzt() {
        when(repository.findBySnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(any()))
                .thenReturn(List.of());
        ArgumentCaptor<LocalDate> ab = ArgumentCaptor.forClass(LocalDate.class);

        service.findVerlauf(Integer.MAX_VALUE);

        verify(repository).findBySnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(ab.capture());
        assertThat(ab.getValue()).isAfterOrEqualTo(LocalDate.now().minusDays(365));
    }

    @Test
    void nullUndNegativeTageWerdenAufEinenTagAngehoben() {
        when(repository.findBySnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(any()))
                .thenReturn(List.of());
        ArgumentCaptor<LocalDate> ab = ArgumentCaptor.forClass(LocalDate.class);

        service.findVerlauf(-5);

        verify(repository).findBySnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(ab.capture());
        assertThat(ab.getValue()).isEqualTo(LocalDate.now().minusDays(1));
    }

    @Test
    void leererBestandLiefertEineLeereListe() {
        when(repository.findBySnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(any()))
                .thenReturn(List.of());

        assertThat(service.findVerlauf(30)).isEmpty();
    }
}
