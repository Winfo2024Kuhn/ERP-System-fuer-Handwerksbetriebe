package org.example.kalkulationsprogramm.repository;

import jakarta.persistence.LockModeType;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufLagerentnahme;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface EinkaufLagerentnahmeRepository extends JpaRepository<EinkaufLagerentnahme, Long> {
    Optional<EinkaufLagerentnahme> findByIdempotenzKey(UUID idempotenzKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EinkaufLagerentnahme e where e.id = :id")
    Optional<EinkaufLagerentnahme> findByIdForUpdate(@Param("id") Long id);

    @Query("select e from EinkaufLagerentnahme e where e.projektId = :projektId order by e.entnommenAm desc, e.id desc")
    Page<EinkaufLagerentnahme> sucheImProjekt(@Param("projektId") Long projektId, Pageable pageable);

    @Query("select sum(e.bewerteterBetrag) from EinkaufLagerentnahme e where e.projektId = :projektId")
    BigDecimal sumBewerteteEntnahmen(@Param("projektId") Long projektId);

    @Query("select case when count(e) > 0 then true else false end from EinkaufLagerentnahme e "
            + "where e.projektId = :projektId and e.preisJeEinheit is null")
    boolean existsUnbewerteteByProjektId(@Param("projektId") Long projektId);
}
