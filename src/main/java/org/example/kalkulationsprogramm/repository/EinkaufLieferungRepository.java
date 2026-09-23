package org.example.kalkulationsprogramm.repository;
import java.util.*;import org.springframework.data.jpa.repository.JpaRepository;import org.example.kalkulationsprogramm.domain.einkauf.EinkaufLieferung;
public interface EinkaufLieferungRepository extends JpaRepository<EinkaufLieferung,Long>{Optional<EinkaufLieferung> findByIdempotenzKey(UUID key);List<EinkaufLieferung> findByBestellung_IdOrderByEingangAsc(Long bestellungId);}
