package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.TelefonAnruf;
import org.example.kalkulationsprogramm.domain.TelefonAnrufArt;
import org.example.kalkulationsprogramm.domain.TelefonZuordnung;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/** Die Anrufliste mit Filtern und Suche baut {@code service.telefon.AnrufSuche} als Specification. */
public interface TelefonAnrufRepository extends JpaRepository<TelefonAnruf, Long>, JpaSpecificationExecutor<TelefonAnruf> {

    boolean existsByZeitpunktAndArtAndEigeneNummerAndNummerRoh(
            LocalDateTime zeitpunkt, TelefonAnrufArt art, String eigeneNummer, String nummerRoh);

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
    @Query(value = """
            SELECT a FROM TelefonAnruf a
            LEFT JOIN FETCH a.kunde
            LEFT JOIN FETCH a.lieferant
            LEFT JOIN FETCH a.steuerberater
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
            """, countQuery = """
            SELECT COUNT(a) FROM TelefonAnruf a
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
            """)
    Page<TelefonAnruf> findeOffeneVerpasste(@Param("seit") LocalDateTime seit, Pageable pageable);

    /**
     * Wurde der zugeordnete Kunde, Lieferant oder Steuerberater gelöscht, setzt die Datenbank nur
     * die Verknüpfung auf NULL. Solche Einträge gelten wieder als unbekannt, damit
     * der nächste Abgleich sie neu zuordnen kann.
     */
    @Modifying
    @Query("""
            UPDATE TelefonAnruf a SET a.zuordnung = org.example.kalkulationsprogramm.domain.TelefonZuordnung.KEINE
            WHERE a.zuordnung <> org.example.kalkulationsprogramm.domain.TelefonZuordnung.KEINE
              AND a.kunde IS NULL AND a.lieferant IS NULL AND a.steuerberater IS NULL
            """)
    int setzeVerwaisteZuordnungenZurueck();
}
