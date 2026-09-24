package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;
import org.example.kalkulationsprogramm.domain.einkauf.AngebotVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.example.kalkulationsprogramm.domain.einkauf.AnfrageRevision;
import org.example.kalkulationsprogramm.domain.einkauf.AnfragePosition;
import jakarta.persistence.LockModeType;

public interface AngebotVersionRepository extends JpaRepository<AngebotVersion, Long> {
    List<AngebotVersion> findByAngebotIdOrderByNummerAsc(Long angebotId);
    Optional<AngebotVersion> findFirstByAngebotIdOrderByNummerDesc(Long angebotId);
    @Query("select distinct v from AngebotVersion v join fetch v.angebot a join fetch a.beteiligung b "
            + "join fetch b.revision br join fetch v.anfrageRevision r "
            + "left join fetch v.positionen p left join fetch p.anfragePosition "
            + "where br.anfrage.id = :anfrageId and v.nummer = "
            + "(select max(v2.nummer) from AngebotVersion v2 where v2.angebot.id = a.id) order by a.id")
    List<AngebotVersion> findeAktuelleVergleichsversionen(@Param("anfrageId") Long anfrageId);

    @Query("select distinct v from AngebotVersion v join fetch v.angebot a join fetch a.beteiligung b "
            + "join fetch b.revision br join fetch v.anfrageRevision r "
            + "left join fetch v.positionen p left join fetch p.anfragePosition "
            + "where br.anfrage.id = :anfrageId order by a.id, v.nummer")
    List<AngebotVersion> leseAnfrageversionen(@Param("anfrageId") Long anfrageId);

    // Separate collection fetches avoid a Cartesian product / Hibernate multiple-bag fetch.
    @Query("select distinct v from AngebotVersion v left join fetch v.kosten where v.id in :ids")
    List<AngebotVersion> ladeVergleichskosten(@Param("ids") List<Long> ids);

    @Query("select distinct r from AnfrageRevision r left join fetch r.positionen where r.id in :ids")
    List<AnfrageRevision> ladeVergleichsAnfragepositionen(@Param("ids") List<Long> ids);

    @Query("select distinct p from AnfragePosition p left join fetch p.herkuenfte h "
            + "left join fetch h.bedarf where p.revision.id in :ids")
    List<AnfragePosition> ladeVergleichsHerkuenfte(@Param("ids") List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AngebotVersion> findById(Long id);
}
