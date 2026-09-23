package org.example.kalkulationsprogramm.repository;
import java.util.*; import jakarta.persistence.LockModeType; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBestellung;
public interface EinkaufBestellungRepository extends JpaRepository<EinkaufBestellung,Long> {
 Optional<EinkaufBestellung> findByIdempotenzKey(UUID idempotenzKey);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select b from EinkaufBestellung b where b.id=:id") Optional<EinkaufBestellung> findeFuerUpdate(@Param("id") Long id);
 @Query("select b from EinkaufBestellung b order by b.id desc") org.springframework.data.domain.Page<EinkaufBestellung> suche(org.springframework.data.domain.Pageable pageable);
}
