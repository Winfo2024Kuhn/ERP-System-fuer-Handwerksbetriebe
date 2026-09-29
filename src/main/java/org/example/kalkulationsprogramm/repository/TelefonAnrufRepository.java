package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.TelefonAnruf;
import org.example.kalkulationsprogramm.domain.TelefonAnrufArt;
import org.example.kalkulationsprogramm.domain.TelefonZuordnung;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TelefonAnrufRepository extends JpaRepository<TelefonAnruf, Long> {

    boolean existsByZeitpunktAndArtAndEigeneNummerAndNummerRoh(
            LocalDateTime zeitpunkt, TelefonAnrufArt art, String eigeneNummer, String nummerRoh);

    /**
     * Anrufliste mit optionalen Filtern. {@code suche} ist bereits klein geschrieben
     * und mit Platzhaltern versehen (oder null).
     */
    @Query(value = """
            SELECT a FROM TelefonAnruf a
            LEFT JOIN FETCH a.kunde k
            LEFT JOIN FETCH a.lieferant l
            WHERE (:art IS NULL OR a.art = :art)
              AND (:nurUnbekannt = false OR a.zuordnung = org.example.kalkulationsprogramm.domain.TelefonZuordnung.KEINE)
              AND (:kundeId IS NULL OR k.id = :kundeId)
              AND (:lieferantId IS NULL OR l.id = :lieferantId)
              AND (:suche IS NULL
                   OR LOWER(a.nummerRoh) LIKE :suche
                   OR LOWER(a.nummerNormalisiert) LIKE :suche
                   OR LOWER(a.nameFritzbox) LIKE :suche
                   OR LOWER(k.name) LIKE :suche
                   OR LOWER(l.lieferantenname) LIKE :suche)
            ORDER BY a.zeitpunkt DESC, a.id DESC
            """, countQuery = """
            SELECT COUNT(a) FROM TelefonAnruf a
            LEFT JOIN a.kunde k
            LEFT JOIN a.lieferant l
            WHERE (:art IS NULL OR a.art = :art)
              AND (:nurUnbekannt = false OR a.zuordnung = org.example.kalkulationsprogramm.domain.TelefonZuordnung.KEINE)
              AND (:kundeId IS NULL OR k.id = :kundeId)
              AND (:lieferantId IS NULL OR l.id = :lieferantId)
              AND (:suche IS NULL
                   OR LOWER(a.nummerRoh) LIKE :suche
                   OR LOWER(a.nummerNormalisiert) LIKE :suche
                   OR LOWER(a.nameFritzbox) LIKE :suche
                   OR LOWER(k.name) LIKE :suche
                   OR LOWER(l.lieferantenname) LIKE :suche)
            """)
    Page<TelefonAnruf> suche(@Param("art") TelefonAnrufArt art,
                             @Param("nurUnbekannt") boolean nurUnbekannt,
                             @Param("kundeId") Long kundeId,
                             @Param("lieferantId") Long lieferantId,
                             @Param("suche") String suche,
                             Pageable pageable);

    List<TelefonAnruf> findByZuordnungAndNummerNormalisiertIsNotNullAndZeitpunktAfter(
            TelefonZuordnung zuordnung, LocalDateTime nach);

    List<TelefonAnruf> findByZuordnungAndNummerNormalisiert(TelefonZuordnung zuordnung, String nummerNormalisiert);

    /** Kandidaten für die Verknüpfung einer Sprachnachricht mit ihrem Anruf. */
    List<TelefonAnruf> findByNummerRohAndZeitpunktBetween(String nummerRoh, LocalDateTime von, LocalDateTime bis);

    long countByKundeId(Long kundeId);

    long countByLieferantId(Long lieferantId);

    @Modifying
    @Query("DELETE FROM TelefonAnruf a WHERE a.zeitpunkt < :grenze")
    int loescheAelterAls(@Param("grenze") LocalDateTime grenze);

    /**
     * Verpasste Anrufe, um die sich noch keiner gekümmert hat: verpasst oder vom
     * Anrufbeantworter angenommen ohne Nachricht – und seitdem weder zurückgerufen
     * noch ein angenommener Anruf derselben Nummer.
     */
    @Query("""
            SELECT a FROM TelefonAnruf a
            LEFT JOIN FETCH a.kunde
            LEFT JOIN FETCH a.lieferant
            WHERE a.zeitpunkt >= :seit
              AND (a.art = org.example.kalkulationsprogramm.domain.TelefonAnrufArt.VERPASST
                   OR (a.art = org.example.kalkulationsprogramm.domain.TelefonAnrufArt.ANRUFBEANTWORTER
                       AND NOT EXISTS (SELECT s.id FROM Sprachnachricht s WHERE s.anruf = a)))
              AND NOT EXISTS (
                   SELECT b.id FROM TelefonAnruf b
                   WHERE b.nummerNormalisiert = a.nummerNormalisiert
                     AND b.zeitpunkt > a.zeitpunkt
                     AND b.art IN (org.example.kalkulationsprogramm.domain.TelefonAnrufArt.ANGENOMMEN,
                                   org.example.kalkulationsprogramm.domain.TelefonAnrufArt.AUSGEHEND))
            ORDER BY a.zeitpunkt DESC
            """)
    List<TelefonAnruf> findeOffeneVerpasste(@Param("seit") LocalDateTime seit);
}
