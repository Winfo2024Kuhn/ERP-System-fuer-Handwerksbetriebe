package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.PreisanfrageLieferant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PreisanfrageLieferantRepository extends JpaRepository<PreisanfrageLieferant, Long> {

    Optional<PreisanfrageLieferant> findByToken(String token);

    Optional<PreisanfrageLieferant> findByOutgoingMessageId(String outgoingMessageId);

    List<PreisanfrageLieferant> findByPreisanfrageIdOrderByLieferant_LieferantennameAsc(Long preisanfrageId);

    boolean existsByToken(String token);
}
