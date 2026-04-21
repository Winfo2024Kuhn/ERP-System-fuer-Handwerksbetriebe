package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.BestellStatus;
import org.example.kalkulationsprogramm.domain.Bestellung;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BestellungRepository extends JpaRepository<Bestellung, Long> {

    Optional<Bestellung> findByBestellnummer(String bestellnummer);

    List<Bestellung> findByStatusOrderByErstelltAmDesc(BestellStatus status);

    List<Bestellung> findByLieferant_IdOrderByErstelltAmDesc(Long lieferantId);

    List<Bestellung> findByProjekt_IdOrderByErstelltAmDesc(Long projektId);

    /**
     * Liefert die hoechste bisher vergebene interne Bestellnummer fuer das
     * gegebene Jahr — Basis fuer den Nummernkreis {@code B-YYYY-NNNN}.
     */
    @Query("""
           SELECT MAX(b.bestellnummer) FROM Bestellung b
           WHERE b.bestellnummer LIKE :prefix
           """)
    Optional<String> findMaxBestellnummerForPrefix(@Param("prefix") String prefix);
}
