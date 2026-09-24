package org.example.kalkulationsprogramm.repository;

import jakarta.persistence.LockModeType;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBedarf;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EinkaufBedarfRepository extends JpaRepository<EinkaufBedarf, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from EinkaufBedarf b where b.id = :id")
    Optional<EinkaufBedarf> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from EinkaufBedarf b where b.id in :ids order by b.id")
    List<EinkaufBedarf> findeAlleFuerUpdate(@Param("ids") Collection<Long> ids);

    @Query("""
            select b from EinkaufBedarf b
            where (:projektId is null or b.projektId = :projektId)
              and (:q is null or :q = '' or lower(b.bezeichnung) like lower(concat('%', :q, '%'))
                   or lower(b.interneKennung) like lower(concat('%', :q, '%')))
            """)
    Page<EinkaufBedarf> suche(@Param("q") String q, @Param("projektId") Long projektId, Pageable pageable);

    @Query("""
            select b from EinkaufBedarf b where b.projektId is null
              and (:q is null or lower(b.bezeichnung) like lower(concat('%', :q, '%'))
                   or lower(b.interneKennung) like lower(concat('%', :q, '%')))
            """)
    Page<EinkaufBedarf> sucheOhneProjekt(@Param("q") String q, Pageable pageable);

    boolean existsByProjektIdAndInterneKennung(Long projektId, String interneKennung);
    boolean existsByProjektIdAndInterneKennungAndIdNot(Long projektId, String interneKennung, Long id);
    Optional<EinkaufBedarf> findByArtikelInProjektId(Long artikelInProjektId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from EinkaufBedarf b where b.artikelInProjektId = :artikelInProjektId")
    Optional<EinkaufBedarf> findByArtikelInProjektIdForUpdate(@Param("artikelInProjektId") Long artikelInProjektId);
}
