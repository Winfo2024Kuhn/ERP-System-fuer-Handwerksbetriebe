package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufAnalyseJob;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufAnalyseVorschlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EinkaufAnalyseJobRepository extends JpaRepository<EinkaufAnalyseJob, Long> {
    Optional<EinkaufAnalyseJob> findByEmailIdAndAnlagenHashAndParserVersion(Long emailId, String anlagenHash, String parserVersion);
    @Query("select v from EinkaufAnalyseVorschlag v where v.job.id = :jobId order by v.id asc")
    List<EinkaufAnalyseVorschlag> findVorschlaege(@Param("jobId") Long jobId);
    List<EinkaufAnalyseJob> findByEmailIdOrderByErstelltAmDesc(Long emailId);
}
