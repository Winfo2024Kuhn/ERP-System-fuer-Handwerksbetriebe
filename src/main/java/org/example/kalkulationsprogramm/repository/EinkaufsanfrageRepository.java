package org.example.kalkulationsprogramm.repository;
import java.util.Optional;
import java.util.UUID;
import org.example.kalkulationsprogramm.domain.einkauf.Einkaufsanfrage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface EinkaufsanfrageRepository extends JpaRepository<Einkaufsanfrage, Long> {
    Optional<Einkaufsanfrage> findByIdempotenzKey(UUID key);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Einkaufsanfrage a where a.id = :id")
    Optional<Einkaufsanfrage> findByIdForUpdate(@Param("id") Long id);
    Page<Einkaufsanfrage> findAllByGeloeschtAmIsNullOrderByAngelegtAmDesc(Pageable pageable);
}
