package org.example.kalkulationsprogramm.repository;

import java.time.Instant;
import java.util.Optional;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufKommunikationVorschau;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EinkaufKommunikationVorschauRepository extends JpaRepository<EinkaufKommunikationVorschau, String> {
    Optional<EinkaufKommunikationVorschau> findByFreigabeTokenAndAnfrageIdAndBeteiligungId(
            String token, Long anfrageId, Long beteiligungId);

    @Modifying
    @Query("delete from EinkaufKommunikationVorschau v where v.gueltigBis < :stichtag")
    int loescheAbgelaufene(@Param("stichtag") Instant stichtag);
}
