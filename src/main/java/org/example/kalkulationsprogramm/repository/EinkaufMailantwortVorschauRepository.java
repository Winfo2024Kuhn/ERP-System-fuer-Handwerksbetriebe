package org.example.kalkulationsprogramm.repository;

import java.time.Instant;
import java.util.Optional;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufMailantwortVorschau;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

public interface EinkaufMailantwortVorschauRepository extends JpaRepository<EinkaufMailantwortVorschau, String> {
    Optional<EinkaufMailantwortVorschau> findByFreigabeTokenAndEmailId(String token, Long emailId);
    Optional<EinkaufMailantwortVorschau> findByVersandIdAndEmailId(Long versandId, Long emailId);
    Optional<EinkaufMailantwortVorschau> findFirstByEmailIdOrderByErstelltAmDesc(Long emailId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from EinkaufMailantwortVorschau v where v.versandId = :versandId and v.emailId = :emailId")
    Optional<EinkaufMailantwortVorschau> findByVersandIdAndEmailIdForUpdate(Long versandId, Long emailId);

    @Modifying
    @Query("delete from EinkaufMailantwortVorschau v where v.gueltigBis < :stichtag")
    int loescheAbgelaufene(@Param("stichtag") Instant stichtag);
}
