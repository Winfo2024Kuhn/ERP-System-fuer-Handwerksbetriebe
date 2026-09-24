package org.example.kalkulationsprogramm.repository;
import java.util.*;import org.springframework.data.jpa.repository.JpaRepository;import org.example.kalkulationsprogramm.domain.einkauf.EinkaufLieferung;
public interface EinkaufLieferungRepository extends JpaRepository<EinkaufLieferung,Long>{Optional<EinkaufLieferung> findByIdempotenzKey(UUID key);List<EinkaufLieferung> findByBestellung_IdOrderByEingangAsc(Long bestellungId);@org.springframework.data.jpa.repository.Query("select distinct d from EinkaufLieferung d left join fetch d.positionen where d.bestellung.id = :id order by d.eingang, d.id")
List<EinkaufLieferung> leseMitPositionen(@org.springframework.data.repository.query.Param("id") Long id);
}
