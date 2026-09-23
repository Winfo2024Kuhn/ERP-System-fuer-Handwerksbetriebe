package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.einkauf.LieferantEinkaufKontakt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface LieferantEinkaufKontaktRepository extends JpaRepository<LieferantEinkaufKontakt, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lieferanten l where l.id = :id")
    Optional<Lieferanten> findLieferantByIdForUpdate(@Param("id") Long id);

    @Query("select l from Lieferanten l where l.id = :id")
    Optional<Lieferanten> findLieferantById(@Param("id") Long id);

    List<LieferantEinkaufKontakt> findByLieferantIdAndAktivTrueOrderById(Long lieferantId);

    List<LieferantEinkaufKontakt> findByLieferantIdOrderById(Long lieferantId);

    Optional<LieferantEinkaufKontakt> findByIdAndLieferantId(Long id, Long lieferantId);
}
