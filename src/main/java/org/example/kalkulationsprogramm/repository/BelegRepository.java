package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.BelegStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface BelegRepository extends JpaRepository<Beleg, Long> {

    List<Beleg> findByStatusOrderByUploadDatumDesc(BelegStatus status);

    List<Beleg> findByStatusAndBelegKategorieOrderByBelegDatumDesc(BelegStatus status, BelegKategorie kategorie);

    List<Beleg> findAllByOrderByUploadDatumDesc();

    @Query("SELECT b FROM Beleg b WHERE b.status = :status AND b.belegKategorie IN :kategorien ORDER BY b.belegDatum DESC, b.uploadDatum DESC")
    List<Beleg> findValidierteByKategorien(@Param("status") BelegStatus status,
                                           @Param("kategorien") List<BelegKategorie> kategorien);

    /**
     * Summe brutto über alle validierten Belege einer Kategorie, optional gefiltert
     * nach Belegdatum. Wird für den Kassen-Saldo verwendet.
     */
    @Query("SELECT COALESCE(SUM(b.betragBrutto), 0) FROM Beleg b " +
           "WHERE b.status = org.example.kalkulationsprogramm.domain.BelegStatus.VALIDIERT " +
           "  AND b.belegKategorie = :kategorie " +
           "  AND (:von IS NULL OR b.belegDatum >= :von) " +
           "  AND (:bis IS NULL OR b.belegDatum <= :bis)")
    BigDecimal summeBruttoByKategorie(@Param("kategorie") BelegKategorie kategorie,
                                      @Param("von") LocalDate von,
                                      @Param("bis") LocalDate bis);

    long countByKiAnalyseStatus(org.example.kalkulationsprogramm.domain.BelegKiAnalyseStatus status);

    /**
     * Liefert alle validierten Belege im angegebenen Datumsbereich, die einer
     * Fixkosten-Kostenstelle zugeordnet sind. Wird vom Verrechnungslohn-Rechner
     * verwendet, um Belege wie Telefonrechnungen, Strom oder Bueromaterial in
     * den Gemeinkosten-Topf einzurechnen. Kostenstelle wird per JOIN FETCH
     * mitgeladen, damit der Service ohne LazyInitializationException auf
     * istFixkosten/bezeichnung zugreifen kann.
     */
    @Query("SELECT b FROM Beleg b JOIN FETCH b.kostenstelle ks " +
           "WHERE b.status = org.example.kalkulationsprogramm.domain.BelegStatus.VALIDIERT " +
           "  AND ks.istFixkosten = true " +
           "  AND b.belegDatum BETWEEN :von AND :bis " +
           "  AND b.betragBrutto IS NOT NULL")
    List<Beleg> findValidierteFixkostenBelegeImZeitraum(@Param("von") LocalDate von,
                                                       @Param("bis") LocalDate bis);

    /**
     * Liefert die letzten Belege desselben Lieferanten, die schon eine
     * Kostenstellen-Zuordnung haben — vom KI-Agent als "aehnliche_belege"-Tool
     * verwendet, um aus historischen Zuordnungen zu lernen.
     */
    @Query("SELECT b FROM Beleg b LEFT JOIN FETCH b.kostenstelle LEFT JOIN FETCH b.sachkonto " +
           "WHERE b.lieferant.id = :lieferantId " +
           "  AND b.kostenstelle IS NOT NULL " +
           "ORDER BY b.belegDatum DESC, b.id DESC")
    List<Beleg> findAehnlicheBelegeByLieferant(@Param("lieferantId") Long lieferantId,
                                               org.springframework.data.domain.Pageable pageable);

    /**
     * Liefert validierte Belege im Datumsbereich (chronologisch) fuer den
     * Steuerberater-Beleg-Export (Issue #58). Lieferant + Sachkonto werden per
     * LEFT JOIN FETCH mitgeladen, damit der Service die Anzeigedaten ohne
     * LazyInitializationException ablesen kann. Positionen werden bewusst NICHT
     * mit geJOINt — die laed der Service nur fuer TEILWEISE-Belege per
     * Folge-Query nach (vermeidet Kartesisches Produkt mit n*m Zeilen).
     */
    @Query("SELECT b FROM Beleg b " +
           "LEFT JOIN FETCH b.lieferant " +
           "LEFT JOIN FETCH b.sachkonto " +
           "WHERE b.status = org.example.kalkulationsprogramm.domain.BelegStatus.VALIDIERT " +
           "  AND (:von IS NULL OR b.belegDatum >= :von) " +
           "  AND (:bis IS NULL OR b.belegDatum <= :bis) " +
           "ORDER BY b.belegDatum ASC, b.id ASC")
    List<Beleg> findValidierteImZeitraumFuerExport(@Param("von") LocalDate von,
                                                   @Param("bis") LocalDate bis);
}
