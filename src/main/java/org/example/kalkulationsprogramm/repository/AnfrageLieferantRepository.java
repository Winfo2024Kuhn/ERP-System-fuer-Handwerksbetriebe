package org.example.kalkulationsprogramm.repository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.example.kalkulationsprogramm.domain.einkauf.AnfrageLieferant;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AnfrageLieferantRepository extends JpaRepository<AnfrageLieferant, Long> {
    List<AnfrageLieferant> findByRevisionIdOrderByIdAsc(Long revisionId);
    @Query("select l from AnfrageLieferant l where l.id = :id and l.revision.anfrage.id = :anfrageId")
    Optional<AnfrageLieferant> findByIdAndRevisionAnfrageId(@Param("id") Long id, @Param("anfrageId") Long anfrageId);
}
