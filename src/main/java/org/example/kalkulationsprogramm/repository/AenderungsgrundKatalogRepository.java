package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.AenderungsgrundKatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository für vordefinierte Änderungsgründe.
 */
@Repository
public interface AenderungsgrundKatalogRepository extends JpaRepository<AenderungsgrundKatalog, Long> {

    /**
     * Findet einen Änderungsgrund anhand seines Codes.
     */
    Optional<AenderungsgrundKatalog> findByCode(String code);

    /**
     * Gibt alle Gründe zurück, sortiert nach Bezeichnung.
     */
    List<AenderungsgrundKatalog> findAllByOrderByBezeichnungAsc();
}
