package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.PreisanfrageAngebot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PreisanfrageAngebotRepository extends JpaRepository<PreisanfrageAngebot, Long> {

    List<PreisanfrageAngebot> findByPreisanfrageLieferantId(Long preisanfrageLieferantId);

    List<PreisanfrageAngebot> findByPreisanfragePositionId(Long preisanfragePositionId);

    /**
     * Alle Angebote einer kompletten Preisanfrage (fuer die Vergleichs-Matrix).
     */
    @Query("SELECT a FROM PreisanfrageAngebot a "
            + "WHERE a.preisanfragePosition.preisanfrage.id = :preisanfrageId")
    List<PreisanfrageAngebot> findAllByPreisanfrageId(@Param("preisanfrageId") Long preisanfrageId);
}
