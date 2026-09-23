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
    @Query("select h.bedarf.id, sum(h.menge) from AnfrageHerkunft h " +
            "where h.bedarf.id in :ids and h.position.revision.anfrage.geloeschtAm is null and h.position.revision.anfrage.aktuelleRevision.id = h.position.revision.id " +
            "group by h.bedarf.id")
    List<Object[]> summenAktuelleAnfragen(@Param("ids") List<Long> bedarfIds);
}
