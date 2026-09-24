package org.example.kalkulationsprogramm.repository;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.jpa.repository.Query; import org.springframework.data.repository.query.Param; import org.example.kalkulationsprogramm.domain.einkauf.BestellungRevision;
public interface BestellungRevisionRepository extends JpaRepository<BestellungRevision,Long> { List<BestellungRevision> findByBestellung_IdOrderByNummerAsc(Long bestellungId); Optional<BestellungRevision> findFirstByBestellung_IdOrderByNummerDesc(Long bestellungId);
 /** Bestellungen, in denen der Bedarf als Herkunft steht – sie sperren das Löschen des Bedarfs. */
 @Query("select distinct b.nummer from BestellungHerkunft h join h.position p join p.revision r join r.bestellung b "
   + "where h.bedarfId = :bedarfId order by b.nummer")
 List<String> bestellNummernMitBedarf(@Param("bedarfId") Long bedarfId);
}
