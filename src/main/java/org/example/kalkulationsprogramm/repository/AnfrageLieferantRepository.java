package org.example.kalkulationsprogramm.repository;
import java.util.List;
import org.example.kalkulationsprogramm.domain.einkauf.AnfrageLieferant;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AnfrageLieferantRepository extends JpaRepository<AnfrageLieferant, Long> {
    List<AnfrageLieferant> findByRevisionIdOrderByIdAsc(Long revisionId);
}
