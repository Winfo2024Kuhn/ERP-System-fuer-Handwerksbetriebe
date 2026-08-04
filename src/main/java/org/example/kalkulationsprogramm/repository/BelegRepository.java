package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.BelegStatus;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
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

    @Query("SELECT b FROM Beleg b "
           + "LEFT JOIN FETCH b.lieferant "
           + "LEFT JOIN FETCH b.kostenstelle "
           + "WHERE b.status <> org.example.kalkulationsprogramm.domain.BelegStatus.VERWORFEN "
           + "  AND b.kostenstelle IS NULL "
           + "  AND NOT EXISTS (SELECT a.id FROM BelegKostenstellenAnteil a WHERE a.beleg = b) "
           + "  AND NOT EXISTS (SELECT d.id FROM LieferantDokument d WHERE d.beleg = b) "
           + "ORDER BY b.belegDatum DESC, b.uploadDatum DESC")
    List<Beleg> findNichtEmailImportierteOhneKostenstellenZuordnung();

    @Query("SELECT b FROM Beleg b "
           + "LEFT JOIN FETCH b.lieferant "
           + "LEFT JOIN FETCH b.kostenstelle "
           + "WHERE b.kostenstelle.id = :kostenstelleId "
           + "  AND b.status <> org.example.kalkulationsprogramm.domain.BelegStatus.VERWORFEN "
           + "  AND NOT EXISTS (SELECT a.id FROM BelegKostenstellenAnteil a WHERE a.beleg = b) "
           + "  AND NOT EXISTS (SELECT d.id FROM LieferantDokument d WHERE d.beleg = b) "
           + "ORDER BY b.belegDatum DESC, b.uploadDatum DESC")
    List<Beleg> findDirektZugeordneteByKostenstelleOhneSplits(@Param("kostenstelleId") Long kostenstelleId);

    /**
     * Liefert die zuletzt vom angegebenen Mitarbeiter hochgeladenen Belege.
     * Wird vom Mobile-Endpoint {@code GET /api/buchhaltung/mobile/belege}
     * verwendet, damit der Buchhalter am Handy nach App-Wechsel/Reload seine
     * frisch gescannten Belege wiederfindet — Limit auf 20, weil die Liste
     * nur den Wiederfindungs-Use-Case bedient (Validierung passiert am PC).
     */
    List<Beleg> findTop20ByUploadedByOrderByUploadDatumDesc(Mitarbeiter uploadedBy);

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
     *
     * Belege, zu denen ein {@code LieferantDokument} existiert, sind
     * ausgenommen: die laufen bereits ueber {@code LieferantDokumentProjektAnteil}
     * in die Gemeinkosten und wuerden sonst doppelt zaehlen (gescannter Beleg
     * + Lieferantenrechnung derselben Ausgabe). Gleiche Bedingung wie in
     * {@code BelegKostenstellenAnteilRepository#findByKostenstelleIdEager}.
     */
    @Query("SELECT b FROM Beleg b JOIN FETCH b.kostenstelle ks " +
           "WHERE b.status = org.example.kalkulationsprogramm.domain.BelegStatus.VALIDIERT " +
           "  AND ks.istFixkosten = true " +
           "  AND b.belegDatum BETWEEN :von AND :bis " +
           "  AND b.betragBrutto IS NOT NULL " +
           "  AND NOT EXISTS (SELECT d.id FROM LieferantDokument d WHERE d.beleg = b)")
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
