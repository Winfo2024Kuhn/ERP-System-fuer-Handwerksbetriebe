package org.example.kalkulationsprogramm.repository;

import java.util.List;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufAnforderungsVorlage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EinkaufAnforderungsVorlageRepository extends JpaRepository<EinkaufAnforderungsVorlage, Long> {
    @Query("select v from EinkaufAnforderungsVorlage v where v.aktiv = true and "
            + "((:artikelId is not null and v.artikelId = :artikelId) or (:projektId is not null and v.projektId = :projektId)) "
            + "order by v.versionsnummer desc")
    List<EinkaufAnforderungsVorlage> findAktuelleBestaetigteFuerArtikelOderProjekt(
            @Param("artikelId") Long artikelId, @Param("projektId") Long projektId);
}
