package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Sprachnachricht;
import org.example.kalkulationsprogramm.domain.TelefonZuordnung;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SprachnachrichtRepository extends JpaRepository<Sprachnachricht, Long> {

    boolean existsByAnrufbeantworterAndZeitpunktAndNummerRoh(int anrufbeantworter, LocalDateTime zeitpunkt, String nummerRoh);

    /** Kontakt, Anruf und "abgehört von" werden gleich mitgeladen (keine Abfrage pro Zeile). */
    @Query("""
            SELECT s FROM Sprachnachricht s
            LEFT JOIN FETCH s.kunde k
            LEFT JOIN FETCH s.lieferant l
            LEFT JOIN FETCH s.anruf
            LEFT JOIN FETCH s.abgehoertVon
            WHERE (:nurNeue = false OR s.abgehoertAm IS NULL)
              AND (:anrufbeantworter IS NULL OR s.anrufbeantworter = :anrufbeantworter)
              AND (:kundeId IS NULL OR k.id = :kundeId)
              AND (:lieferantId IS NULL OR l.id = :lieferantId)
            ORDER BY s.zeitpunkt DESC, s.id DESC
            """)
    List<Sprachnachricht> suche(@Param("nurNeue") boolean nurNeue,
                                @Param("anrufbeantworter") Integer anrufbeantworter,
                                @Param("kundeId") Long kundeId,
                                @Param("lieferantId") Long lieferantId);

    long countByAbgehoertAmIsNull();

    List<Sprachnachricht> findByAnrufId(Long anrufId);

    List<Sprachnachricht> findByAnrufIdIn(List<Long> anrufIds);

    List<Sprachnachricht> findByZuordnungAndNummerNormalisiertIsNotNullAndZeitpunktAfter(
            TelefonZuordnung zuordnung, LocalDateTime nach);

    List<Sprachnachricht> findByZuordnungAndNummerNormalisiert(TelefonZuordnung zuordnung, String nummerNormalisiert);

    List<Sprachnachricht> findByZeitpunktBefore(LocalDateTime grenze);

    long countByKundeId(Long kundeId);

    long countByLieferantId(Long lieferantId);

    @Modifying
    @Query("UPDATE Sprachnachricht s SET s.anruf = null WHERE s.anruf.id IN (SELECT a.id FROM TelefonAnruf a WHERE a.zeitpunkt < :grenze)")
    int loeseAnrufeAelterAls(@Param("grenze") LocalDateTime grenze);

    @Query("""
            SELECT s FROM Sprachnachricht s
            LEFT JOIN FETCH s.kunde
            LEFT JOIN FETCH s.lieferant
            WHERE s.abgehoertAm IS NULL
            ORDER BY s.zeitpunkt DESC
            """)
    List<Sprachnachricht> findeNeue();
}
