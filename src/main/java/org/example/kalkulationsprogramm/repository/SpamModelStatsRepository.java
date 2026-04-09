package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.SpamModelStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpamModelStatsRepository extends JpaRepository<SpamModelStats, Long> {

    Optional<SpamModelStats> findByStatKey(String statKey);

    @Modifying
    @Query("UPDATE SpamModelStats s SET s.statValue = s.statValue + 1 WHERE s.statKey = :key")
    int incrementStat(@Param("key") String key);
}
