package org.example.kalkulationsprogramm.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Betriebsmittel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BetriebsmittelRepository extends JpaRepository<Betriebsmittel, Long> {

    Optional<Betriebsmittel> findByBarcode(String barcode);

    Optional<Betriebsmittel> findBySeriennummer(String seriennummer);

    @Query("SELECT b FROM Betriebsmittel b WHERE b.ausserBetrieb = false AND (b.naechstesPruefDatum IS NULL OR b.naechstesPruefDatum <= :bis)")
    List<Betriebsmittel> findFaelligBis(LocalDate bis);
}
