package org.example.kalkulationsprogramm.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository für Zeitbuchungen (Arbeitszeit auf Projekte).
 */
@Repository
public interface ZeitbuchungRepository extends JpaRepository<Zeitbuchung, Long> {

        // ==================== Mobile Zeiterfassung ====================

        /**
         * Findet alle aktiven Buchungen (ohne Endzeit) für einen Mitarbeiter.
         */
        List<Zeitbuchung> findByMitarbeiterIdAndEndeZeitIsNull(Long mitarbeiterId);

        /**
         * Findet die neueste aktive Buchung für einen Mitarbeiter.
         */
        Optional<Zeitbuchung> findFirstByMitarbeiterIdAndEndeZeitIsNullOrderByStartZeitDesc(Long mitarbeiterId);

        /**
         * Findet Buchungen eines Mitarbeiters ab einem bestimmten Zeitpunkt.
         */
        @Query("SELECT z FROM Zeitbuchung z WHERE z.mitarbeiter.id = :mitarbeiterId AND z.startZeit >= :startTime ORDER BY z.startZeit ASC")
        List<Zeitbuchung> findByMitarbeiterIdAndStartZeitAfter(
                        @Param("mitarbeiterId") Long mitarbeiterId,
                        @Param("startTime") LocalDateTime startTime);

        /**
         * Findet Buchungen eines Mitarbeiters zwischen zwei Zeitpunkten (inklusiv).
         */
        @Query("SELECT z FROM Zeitbuchung z WHERE z.mitarbeiter.id = :mitarbeiterId AND z.startZeit >= :startTime AND z.startZeit <= :endTime ORDER BY z.startZeit ASC")
        List<Zeitbuchung> findByMitarbeiterIdAndStartZeitBetween(
                        @Param("mitarbeiterId") Long mitarbeiterId,
                        @Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime);

        /**
         * Findet die allererste Buchung eines Mitarbeiters (für Gesamtsaldo).
         */
        Optional<Zeitbuchung> findFirstByMitarbeiterIdOrderByStartZeitAsc(Long mitarbeiterId);

        // ==================== Projekt-Auswertungen ====================

        /**
         * Findet alle Buchungen für ein Projekt.
         */
        List<Zeitbuchung> findByProjektId(Long projektId);

        /**
         * Findet Buchungen für ein Projekt in einem Zeitraum.
         */
        @Query("SELECT z FROM Zeitbuchung z WHERE z.projekt.id = :projektId AND z.startZeit >= :von AND z.startZeit < :bis ORDER BY z.startZeit ASC")
        List<Zeitbuchung> findByProjektIdAndZeitraum(
                        @Param("projektId") Long projektId,
                        @Param("von") LocalDateTime von,
                        @Param("bis") LocalDateTime bis);

        /**
         * Zählt Buchungen für einen Arbeitsgang (für Lösch-Prüfung).
         */
        long countByArbeitsgangId(Long arbeitsgangId);

        /**
         * Löscht alle Buchungen für ein Projekt.
         */
        void deleteByProjektId(Long projektId);

        /**
         * Findet eine Buchung anhand der Kombination Projekt/Arbeitsgang/Kategorie.
         */
        Optional<Zeitbuchung> findByProjektIdAndArbeitsgangIdAndProjektProduktkategorieId(
                        Long projektId,
                        Long arbeitsgangId,
                        Long projektProduktkategorieId);

        /**
         * Findet eine Buchung anhand des Idempotency-Keys (UUID vom Client).
         * Wird für Offline-Sync verwendet, um Duplikate zu verhindern.
         */
        Optional<Zeitbuchung> findByIdempotencyKey(String idempotencyKey);

        /**
         * Findet eine Buchung anhand des Stop-Idempotency-Keys.
         */
        Optional<Zeitbuchung> findByStopIdempotencyKey(String stopIdempotencyKey);

        /**
         * Prüft ob Zeitbuchungen für eine bestimmte ProjektProduktkategorie existieren.
         */
        boolean existsByProjektProduktkategorieId(Long projektProduktkategorieId);
}
