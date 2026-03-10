package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KundeRepository extends JpaRepository<Kunde, Long>, JpaSpecificationExecutor<Kunde>
{
    Optional<Kunde> findByKundennummerIgnoreCase(String kundennummer);

    @Query(value = "SELECT kundennummer FROM kunde ORDER BY CAST(kundennummer AS UNSIGNED) DESC LIMIT 1", nativeQuery = true)
    Optional<String> findMaxKundennummer();

    /**
     * Sucht Kunden nach Name, Ansprechpartner, Kundennummer oder E-Mail.
     */
    @Query("SELECT DISTINCT k FROM Kunde k LEFT JOIN k.kundenEmails e " +
           "WHERE LOWER(k.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(k.ansprechspartner) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(k.kundennummer) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(e) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Kunde> searchByNameOrAnsprechpartnerOrEmail(@Param("query") String query);
}
