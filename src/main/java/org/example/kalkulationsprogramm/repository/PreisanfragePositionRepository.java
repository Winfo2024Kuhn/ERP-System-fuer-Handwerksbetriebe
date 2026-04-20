package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.PreisanfragePosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PreisanfragePositionRepository extends JpaRepository<PreisanfragePosition, Long> {

    List<PreisanfragePosition> findByPreisanfrageIdOrderByReihenfolgeAsc(Long preisanfrageId);
}
