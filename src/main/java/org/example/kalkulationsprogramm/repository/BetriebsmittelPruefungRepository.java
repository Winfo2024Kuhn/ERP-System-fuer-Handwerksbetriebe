package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.BetriebsmittelPruefung;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BetriebsmittelPruefungRepository extends JpaRepository<BetriebsmittelPruefung, Long> {

    List<BetriebsmittelPruefung> findByBetriebsmittelIdOrderByPruefDatumDesc(Long betriebsmittelId);

    Optional<BetriebsmittelPruefung> findFirstByBetriebsmittelIdOrderByPruefDatumDesc(Long betriebsmittelId);

    List<BetriebsmittelPruefung> findByVonElektrikerVerifiziertFalse();
}
