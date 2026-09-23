package org.example.kalkulationsprogramm.repository;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository; import org.example.kalkulationsprogramm.domain.einkauf.BestellungRevision;
public interface BestellungRevisionRepository extends JpaRepository<BestellungRevision,Long> { List<BestellungRevision> findByBestellung_IdOrderByNummerAsc(Long bestellungId); Optional<BestellungRevision> findFirstByBestellung_IdOrderByNummerDesc(Long bestellungId); }
