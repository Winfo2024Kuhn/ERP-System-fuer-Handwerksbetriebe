package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.WebsiteAnalyticsSnapshot;
import org.example.kalkulationsprogramm.dto.WebsiteAnalytics.AnalyticsSnapshotRequestDto;
import org.example.kalkulationsprogramm.dto.WebsiteAnalytics.AnalyticsSnapshotResponseDto;
import org.example.kalkulationsprogramm.dto.WebsiteAnalytics.VerlaufPunktDto;
import org.example.kalkulationsprogramm.repository.WebsiteAnalyticsSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Persistiert Tagesschluss-Snapshots der Marketing-Website und liefert den
 * juengsten Snapshot fuer das ERP-Frontend.
 * <p>
 * Upsert-Logik: {@link WebsiteAnalyticsSnapshot#snapshotDate} ist eindeutig.
 * Bei einem erneuten Push fuer denselben Tag wird der bestehende Datensatz
 * ueberschrieben - es sei denn, der eingehende Snapshot wurde nachweislich
 * frueher generiert (dann wird der Push verworfen, vgl. Section 4 der Spec).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebsiteAnalyticsSnapshotService {

    private static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final WebsiteAnalyticsSnapshotRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public WebsiteAnalyticsSnapshot upsert(AnalyticsSnapshotRequestDto dto, String rawPayload) {
        if (dto.getSchemaVersion() == null || dto.getSnapshotDate() == null
                || dto.getGeneratedAt() == null || dto.getTotals() == null) {
            throw new IllegalArgumentException(
                    "schemaVersion, snapshotDate, generatedAt und totals sind Pflichtfelder.");
        }
        if (dto.getSchemaVersion() > SUPPORTED_SCHEMA_VERSION) {
            log.warn("Analytics-Snapshot mit unbekannter schemaVersion {} empfangen (unterstuetzt: {}).",
                    dto.getSchemaVersion(), SUPPORTED_SCHEMA_VERSION);
        }

        LocalDateTime generatedAtLocal = dto.getGeneratedAt().atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();

        WebsiteAnalyticsSnapshot entity = repository.findBySnapshotDate(dto.getSnapshotDate())
                .orElseGet(WebsiteAnalyticsSnapshot::new);

        if (entity.getId() != null && entity.getGeneratedAt() != null
                && entity.getGeneratedAt().isAfter(generatedAtLocal)) {
            log.info("Aelterer Snapshot fuer {} verworfen (vorhandener generatedAt={} > eingehend={}).",
                    dto.getSnapshotDate(), entity.getGeneratedAt(), generatedAtLocal);
            return entity;
        }

        entity.setSnapshotDate(dto.getSnapshotDate());
        entity.setSchemaVersion(dto.getSchemaVersion());
        entity.setGeneratedAt(generatedAtLocal);
        entity.setReceivedAt(LocalDateTime.now(ZoneOffset.UTC));

        AnalyticsSnapshotRequestDto.Totals totals = dto.getTotals();
        entity.setTotalsVisitors(totals.getVisitors());
        entity.setTotalsPageviews(totals.getPageviews());
        entity.setTotalsLeadsPhone(totals.getLeadsPhone());
        entity.setTotalsLeadsMail(totals.getLeadsMail());
        entity.setTotalsSubmissions(totals.getSubmissions());

        entity.setVisitorsToday(dto.getVisitorsToday());
        entity.setVisitorsYesterday(dto.getVisitorsYesterday());
        entity.setConversion(dto.getConversion());

        entity.setFunnelJson(writeJson(dto.getFunnel()));
        entity.setTopPagesJson(writeJson(dto.getTopPages()));
        entity.setDevicesJson(writeJson(dto.getDevices()));
        entity.setBrowsersJson(writeJson(dto.getBrowsers()));
        entity.setCitiesJson(writeJson(dto.getCities()));

        entity.setRawPayload(rawPayload);

        return repository.save(entity);
    }

    @Transactional(readOnly = true)
    public Optional<AnalyticsSnapshotResponseDto> findLatest() {
        return repository.findFirstByOrderBySnapshotDateDesc().map(this::toResponse);
    }

    /** Untere und obere Schranke fuer den angefragten Zeitraum. */
    private static final int MIN_TAGE = 1;
    private static final int MAX_TAGE = 365;

    /**
     * Liefert den Besucherverlauf der letzten {@code tage} Tage.
     * Der Parameter wird hart auf 1 bis 365 begrenzt: ohne Schranke wuerde
     * ein grosser Wert die gesamte Tabelle laden, ein negativer ein Datum
     * in der Zukunft ergeben und damit immer eine leere Liste.
     */
    @Transactional(readOnly = true)
    public List<VerlaufPunktDto> findVerlauf(int tage) {
        int begrenzt = Math.min(Math.max(tage, MIN_TAGE), MAX_TAGE);
        LocalDate ab = LocalDate.now().minusDays(begrenzt);
        return repository.findBySnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(ab).stream()
                .map(e -> new VerlaufPunktDto(
                        e.getSnapshotDate(),
                        e.getVisitorsToday(),
                        e.getTotalsVisitors(),
                        e.getTotalsPageviews(),
                        e.getTotalsSubmissions(),
                        e.getConversion()))
                .toList();
    }

    private String writeJson(Object value) {
        if (value == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Analytics-Snapshot Liste konnte nicht serialisiert werden.", e);
            return "[]";
        }
    }

    private AnalyticsSnapshotResponseDto toResponse(WebsiteAnalyticsSnapshot e) {
        return AnalyticsSnapshotResponseDto.builder()
                .schemaVersion(e.getSchemaVersion())
                .snapshotDate(e.getSnapshotDate())
                .generatedAt(e.getGeneratedAt())
                .receivedAt(e.getReceivedAt())
                .totals(AnalyticsSnapshotResponseDto.Totals.builder()
                        .visitors(e.getTotalsVisitors())
                        .pageviews(e.getTotalsPageviews())
                        .leadsPhone(e.getTotalsLeadsPhone())
                        .leadsMail(e.getTotalsLeadsMail())
                        .submissions(e.getTotalsSubmissions())
                        .build())
                .visitorsToday(e.getVisitorsToday())
                .visitorsYesterday(e.getVisitorsYesterday())
                .conversion(e.getConversion())
                .funnel(parseList(e.getFunnelJson(), AnalyticsSnapshotResponseDto.FunnelStep.class,
                        this::mapFunnel))
                .topPages(parseList(e.getTopPagesJson(), AnalyticsSnapshotResponseDto.TopPage.class,
                        this::mapTopPage))
                .devices(parseList(e.getDevicesJson(), AnalyticsSnapshotResponseDto.DeviceCount.class,
                        this::mapDevice))
                .browsers(parseList(e.getBrowsersJson(), AnalyticsSnapshotResponseDto.BrowserCount.class,
                        this::mapBrowser))
                .cities(parseList(e.getCitiesJson(), AnalyticsSnapshotResponseDto.CityCount.class,
                        this::mapCity))
                .build();
    }

    private <T> List<T> parseList(String json, Class<T> targetType, java.util.function.Function<java.util.Map<String, Object>, T> mapper) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<java.util.Map<String, Object>> raw = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, java.util.Map.class));
            return raw.stream().map(mapper).toList();
        } catch (JsonProcessingException e) {
            log.warn("Analytics-Snapshot Liste konnte nicht gelesen werden ({}).", targetType.getSimpleName(), e);
            return Collections.emptyList();
        }
    }

    private AnalyticsSnapshotResponseDto.FunnelStep mapFunnel(java.util.Map<String, Object> m) {
        return AnalyticsSnapshotResponseDto.FunnelStep.builder()
                .name(asString(m.get("name")))
                .label(asString(m.get("label")))
                .count(asLong(m.get("count")))
                .build();
    }

    private AnalyticsSnapshotResponseDto.TopPage mapTopPage(java.util.Map<String, Object> m) {
        return AnalyticsSnapshotResponseDto.TopPage.builder()
                .path(asString(m.get("path")))
                .count(asLong(m.get("count")))
                .build();
    }

    private AnalyticsSnapshotResponseDto.DeviceCount mapDevice(java.util.Map<String, Object> m) {
        return AnalyticsSnapshotResponseDto.DeviceCount.builder()
                .device(asString(m.get("device")))
                .count(asLong(m.get("count")))
                .build();
    }

    private AnalyticsSnapshotResponseDto.BrowserCount mapBrowser(java.util.Map<String, Object> m) {
        return AnalyticsSnapshotResponseDto.BrowserCount.builder()
                .browser(asString(m.get("browser")))
                .count(asLong(m.get("count")))
                .build();
    }

    private AnalyticsSnapshotResponseDto.CityCount mapCity(java.util.Map<String, Object> m) {
        return AnalyticsSnapshotResponseDto.CityCount.builder()
                .city(asString(m.get("city")))
                .country(asString(m.get("country")))
                .count(asLong(m.get("count")))
                .build();
    }

    private String asString(Object v) {
        return v == null ? null : v.toString();
    }

    private long asLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v == null) {
            return 0L;
        }
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
