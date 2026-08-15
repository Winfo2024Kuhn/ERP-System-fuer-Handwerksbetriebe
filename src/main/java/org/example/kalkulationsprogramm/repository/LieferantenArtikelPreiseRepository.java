package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Zugriff auf die Preishistorie.
 *
 * <p>Die Tabelle enthaelt seit Migration V338 auch vergangene Preisstaende. Alle
 * Methoden, die "den Preis" meinen, filtern deshalb auf {@code aktuell = true}.
 * Fuer die Verlaufsanzeige stehen die {@code ...Verlauf}-Methoden bereit.
 */
public interface LieferantenArtikelPreiseRepository
        extends JpaRepository<LieferantenArtikelPreise, Long>,
        JpaSpecificationExecutor<LieferantenArtikelPreise> {

    /** Der derzeit gueltige Preis eines Lieferanten fuer einen Artikel. */
    Optional<LieferantenArtikelPreise> findByArtikel_IdAndLieferant_IdAndAktuellTrue(Long artikelId, Long lieferantId);

    /** Der derzeit gueltige Eintrag zu einer Lieferanten-Artikelnummer. */
    Optional<LieferantenArtikelPreise> findByExterneArtikelnummerIgnoreCaseAndLieferant_IdAndAktuellTrue(
            String externeArtikelnummer, Long lieferantId);

    /**
     * Die derzeit gueltigen Eintraege zu mehreren Lieferanten-Artikelnummern in
     * einem Rutsch - fuer die Preisuebernahme aus einer Rechnung mit vielen
     * Positionen, die sonst eine Anfrage je Position braeuchte.
     *
     * <p>Spring Data unterstuetzt {@code IgnoreCase} nicht zusammen mit {@code In},
     * deshalb hier {@code UPPER(...)} im JPQL statt einer abgeleiteten
     * Methodensignatur. Der Aufrufer muss {@code externeArtikelnummern} bereits in
     * Grossschreibung uebergeben.
     */
    @Query("""
            SELECT p FROM LieferantenArtikelPreise p
            WHERE p.lieferant.id = :lieferantId AND p.aktuell = true
            AND UPPER(p.externeArtikelnummer) IN :externeArtikelnummern
            """)
    List<LieferantenArtikelPreise> findByLieferant_IdAndAktuellTrueAndExterneArtikelnummerIn(
            @Param("lieferantId") Long lieferantId,
            @Param("externeArtikelnummern") Collection<String> externeArtikelnummern);

    /** Alle derzeit gueltigen Preise eines Artikels, guenstigster zuerst. */
    @Query("""
            SELECT p FROM LieferantenArtikelPreise p
            LEFT JOIN FETCH p.lieferant
            WHERE p.artikel.id = :artikelId AND p.aktuell = true AND p.preis IS NOT NULL
            ORDER BY p.preis ASC
            """)
    List<LieferantenArtikelPreise> findeAktuellePreise(@Param("artikelId") Long artikelId);

    /** Vollstaendiger Preisverlauf eines Artikels, juengster Stand zuerst. */
    @Query("""
            SELECT p FROM LieferantenArtikelPreise p
            LEFT JOIN FETCH p.lieferant
            WHERE p.artikel.id = :artikelId AND p.preis IS NOT NULL
            ORDER BY p.preisAenderungsdatum DESC, p.id DESC
            """)
    List<LieferantenArtikelPreise> findeVerlauf(@Param("artikelId") Long artikelId);

    /**
     * Setzt bisherige Preisstaende eines Lieferanten auf "nicht mehr aktuell".
     * Wird aufgerufen, bevor ein neuer Stand geschrieben wird, damit immer genau
     * ein Eintrag je Artikel und Lieferant als aktuell gilt.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE LieferantenArtikelPreise p SET p.aktuell = false
            WHERE p.artikel.id = :artikelId AND p.lieferant.id = :lieferantId AND p.aktuell = true
            """)
    int markiereBisherigeAlsVeraltet(@Param("artikelId") Long artikelId, @Param("lieferantId") Long lieferantId);
}
