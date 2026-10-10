package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.MonatsSaldo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository für MonatsSaldo-Cache-Einträge.
 */
@Repository
public interface MonatsSaldoRepository extends JpaRepository<MonatsSaldo, Long>, MonatsSaldoRepositoryErweiterung {

    /**
     * Findet den Cache-Eintrag für einen bestimmten Mitarbeiter/Monat.
     */
    Optional<MonatsSaldo> findByMitarbeiterIdAndJahrAndMonat(Long mitarbeiterId, Integer jahr, Integer monat);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ms FROM MonatsSaldo ms WHERE ms.mitarbeiter.id = :id AND ms.jahr = :jahr AND ms.monat = :monat")
    Optional<MonatsSaldo> findGesperrt(@Param("id") Long id, @Param("jahr") int jahr, @Param("monat") int monat);

    /**
     * Findet alle gültigen Cache-Einträge für einen Mitarbeiter in einem Jahr.
     */
    List<MonatsSaldo> findByMitarbeiterIdAndJahrAndGueltigTrue(Long mitarbeiterId, Integer jahr);

    /**
     * Findet alle Cache-Einträge für einen Mitarbeiter (alle Jahre).
     */
    List<MonatsSaldo> findByMitarbeiterIdAndGueltigTrue(Long mitarbeiterId);

    /**
     * Findet alle gültigen Cache-Einträge für einen Mitarbeiter im Bereich von/bis (Jahr).
     */
    @Query("SELECT ms FROM MonatsSaldo ms WHERE ms.mitarbeiter.id = :mitarbeiterId " +
           "AND ms.gueltig = true " +
           "AND (ms.jahr * 100 + ms.monat) >= :vonJahrMonat " +
           "AND (ms.jahr * 100 + ms.monat) <= :bisJahrMonat " +
           "ORDER BY ms.jahr, ms.monat")
    List<MonatsSaldo> findGueltigeImZeitraum(
            @Param("mitarbeiterId") Long mitarbeiterId,
            @Param("vonJahrMonat") int vonJahrMonat,
            @Param("bisJahrMonat") int bisJahrMonat);

    /**
     * Invalidiert den Cache für einen bestimmten Monat (setzt gueltig=false).
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE MonatsSaldo ms SET ms.gueltig = false, ms.version = ms.version + 1 WHERE ms.festgeschrieben = false AND ms.mitarbeiter.id = :mitarbeiterId AND ms.jahr = :jahr AND ms.monat = :monat")
    void invalidiere(@Param("mitarbeiterId") Long mitarbeiterId, @Param("jahr") int jahr, @Param("monat") int monat);

    /**
     * Invalidiert den Cache für ein ganzes Jahr (z.B. bei Zeitkonto-Korrekturen).
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE MonatsSaldo ms SET ms.gueltig = false, ms.version = ms.version + 1 WHERE ms.festgeschrieben = false AND ms.mitarbeiter.id = :mitarbeiterId AND ms.jahr = :jahr")
    void invalidiereJahr(@Param("mitarbeiterId") Long mitarbeiterId, @Param("jahr") int jahr);

    /**
     * Invalidiert ALLE Cache-Einträge für einen Mitarbeiter (z.B. bei Zeitkonto-Änderung).
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE MonatsSaldo ms SET ms.gueltig = false, ms.version = ms.version + 1 WHERE ms.festgeschrieben = false AND ms.mitarbeiter.id = :mitarbeiterId")
    void invalidiereAlle(@Param("mitarbeiterId") Long mitarbeiterId);

    interface OffenerMonat {
        Integer getJahr();
        Integer getMonat();
        Integer getAnzahl();
    }

}
