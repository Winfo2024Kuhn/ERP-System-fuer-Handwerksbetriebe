package org.example.kalkulationsprogramm.repository;
import java.util.List;
import java.util.Optional;
import org.example.kalkulationsprogramm.domain.einkauf.AnfrageRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface AnfrageRevisionRepository extends JpaRepository<AnfrageRevision, Long> {
    Optional<AnfrageRevision> findFirstByAnfrageIdOrderByNummerDesc(Long anfrageId);
    Optional<AnfrageRevision> findByIdAndAnfrageId(Long id, Long anfrageId);
    Optional<AnfrageRevision> findByIdempotenzKey(java.util.UUID idempotenzKey);
    List<AnfrageRevision> findByAnfrageIdOrderByNummerAsc(Long anfrageId);
    @Query("select distinct h.position.revision.id, h.bedarf.projektId from AnfrageHerkunft h "
            + "where h.position.revision.id in :ids and h.bedarf.projektId is not null")
    List<Object[]> projekteFuerRevisionen(@Param("ids") List<Long> ids);
    @Query("select l.revision.id, count(l), sum(case when l.antwortAm is not null then 1 else 0 end) "
            + "from AnfrageLieferant l where l.revision.id in :ids group by l.revision.id")
    List<Object[]> antwortenFuerRevisionen(@Param("ids") List<Long> ids);
    @Query("select h.bedarf.id, sum(h.menge) from AnfrageHerkunft h " +
            "where h.bedarf.id in :ids and h.position.revision.anfrage.geloeschtAm is null and h.position.revision.anfrage.aktuelleRevision.id = h.position.revision.id " +
            "group by h.bedarf.id")
    List<Object[]> summenAktuelleAnfragen(@Param("ids") List<Long> bedarfIds);
}
