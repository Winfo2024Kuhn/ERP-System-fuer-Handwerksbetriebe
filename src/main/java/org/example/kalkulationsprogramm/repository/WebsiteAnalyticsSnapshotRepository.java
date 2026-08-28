package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.WebsiteAnalyticsSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WebsiteAnalyticsSnapshotRepository extends JpaRepository<WebsiteAnalyticsSnapshot, Long> {

    Optional<WebsiteAnalyticsSnapshot> findBySnapshotDate(LocalDate snapshotDate);

    Optional<WebsiteAnalyticsSnapshot> findFirstByOrderBySnapshotDateDesc();

    /**
     * Alle Snapshots ab dem uebergebenen Tag, aeltester zuerst. Aufsteigend,
     * damit das Frontend die Liste ohne Umsortieren als Zeitachse zeichnen kann.
     */
    List<WebsiteAnalyticsSnapshot> findBySnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(LocalDate ab);
}
