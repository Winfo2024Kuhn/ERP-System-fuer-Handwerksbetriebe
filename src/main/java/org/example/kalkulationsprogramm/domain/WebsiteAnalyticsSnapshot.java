package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Tagesschluss-Snapshot der Website-Analytics (bauschlosserei-kuhn.de).
 * Wird einmal taeglich vom Website-Server an
 * {@code POST /api/internal/analytics-snapshot} gepusht.
 * <p>
 * {@link #snapshotDate} ist eindeutig pro Tag - ein erneuter Push ueberschreibt
 * den bestehenden Datensatz. Listenfelder kommen als JSON-Strings rein, damit
 * additive Schema-Erweiterungen ohne Migration auskommen.
 */
@Entity
@Table(name = "website_analytics_snapshot",
        uniqueConstraints = @UniqueConstraint(name = "uk_website_analytics_snapshot_date",
                columnNames = "snapshot_date"))
@Getter
@Setter
public class WebsiteAnalyticsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "totals_visitors", nullable = false)
    private long totalsVisitors;

    @Column(name = "totals_pageviews", nullable = false)
    private long totalsPageviews;

    @Column(name = "totals_leads_phone", nullable = false)
    private long totalsLeadsPhone;

    @Column(name = "totals_leads_mail", nullable = false)
    private long totalsLeadsMail;

    @Column(name = "totals_submissions", nullable = false)
    private long totalsSubmissions;

    @Column(name = "visitors_today", nullable = false)
    private long visitorsToday;

    @Column(name = "visitors_yesterday", nullable = false)
    private long visitorsYesterday;

    @Column(name = "conversion", nullable = false)
    private int conversion;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "funnel_json")
    private String funnelJson;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "top_pages_json")
    private String topPagesJson;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "devices_json")
    private String devicesJson;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "browsers_json")
    private String browsersJson;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "cities_json")
    private String citiesJson;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "raw_payload")
    private String rawPayload;

    @PrePersist
    void onPersist() {
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }
}
