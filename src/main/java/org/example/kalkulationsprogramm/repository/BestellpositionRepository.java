package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Bestellposition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BestellpositionRepository extends JpaRepository<Bestellposition, Long> {

    List<Bestellposition> findByBestellung_IdOrderByPositionsnummerAsc(Long bestellungId);

    Optional<Bestellposition> findByAusArtikelInProjekt_Id(Long artikelInProjektId);
}
