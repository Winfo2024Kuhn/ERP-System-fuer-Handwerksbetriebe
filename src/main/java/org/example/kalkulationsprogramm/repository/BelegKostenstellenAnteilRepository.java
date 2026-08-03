package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.BelegKostenstellenAnteil;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BelegKostenstellenAnteilRepository extends JpaRepository<BelegKostenstellenAnteil, Long> {

    @Query("SELECT a FROM BelegKostenstellenAnteil a WHERE a.beleg.id = :belegId ORDER BY a.id ASC")
    List<BelegKostenstellenAnteil> findByBelegId(@Param("belegId") Long belegId);

    @Query("SELECT a FROM BelegKostenstellenAnteil a "
            + "JOIN FETCH a.beleg b "
            + "JOIN FETCH a.kostenstelle ks "
            + "LEFT JOIN FETCH a.zugeordnetVon "
            + "WHERE ks.id = :kostenstelleId "
            + "AND b.status <> org.example.kalkulationsprogramm.domain.BelegStatus.VERWORFEN "
            + "AND NOT EXISTS (SELECT d.id FROM LieferantDokument d WHERE d.beleg = b) "
            + "ORDER BY b.belegDatum DESC, b.uploadDatum DESC, a.id DESC")
    List<BelegKostenstellenAnteil> findByKostenstelleIdEager(@Param("kostenstelleId") Long kostenstelleId);

    @Modifying
    @Query("DELETE FROM BelegKostenstellenAnteil a WHERE a.beleg.id = :belegId")
    void deleteByBelegId(@Param("belegId") Long belegId);

    /**
     * Je Beleg die Summe der bereits auf Kostenstellen aufgeteilten Betraege
     * (ohne Streckung, also der volle zugeordnete Betrag).
     *
     * Der Verrechnungslohn-Rechner braucht das, um einen Beleg nicht doppelt zu
     * zaehlen: die PC-Oberflaeche laesst eine direkte {@code Beleg.kostenstelle}
     * UND Anteile gleichzeitig zu. Gezaehlt wird ueber die direkte Kostenstelle
     * nur noch der Rest, der nicht aufgeteilt ist -- bei einer Aufteilung von
     * z.B. 60 % bleiben so die restlichen 40 % erhalten, statt ersatzlos zu
     * verschwinden.
     *
     * @return Zeilen mit [0] = Beleg-ID (Long), [1] = Summe (BigDecimal, kann null sein)
     */
    @Query("SELECT a.beleg.id, SUM(a.berechneterBetrag) FROM BelegKostenstellenAnteil a "
            + "GROUP BY a.beleg.id")
    List<Object[]> summiereAufgeteilteBetraegeProBeleg();

    /**
     * Alle Anteile auf einer bestimmten Kostenstelle, deren Streckungs-Zeitraum
     * das gegebene Jahr enthaelt. Wird vom Verrechnungslohn-Rechner verwendet,
     * um auch gestreckte Bar-Belege in die Gemeinkosten einzubeziehen.
     */
    @Query("SELECT a FROM BelegKostenstellenAnteil a "
            + "WHERE a.kostenstelle.id = :kostenstelleId "
            + "AND (a.streckungStartJahr IS NULL OR a.streckungStartJahr <= :jahr) "
            + "AND (a.streckungStartJahr IS NULL OR a.streckungJahre IS NULL "
            + "     OR a.streckungJahre <= 1 OR :jahr < a.streckungStartJahr + a.streckungJahre)")
    List<BelegKostenstellenAnteil> findAktiveByKostenstelleUndJahr(
            @Param("kostenstelleId") Long kostenstelleId,
            @Param("jahr") int jahr);

    /**
     * Alle Anteile, die im gegebenen Jahr in den Gemeinkostentopf einfliessen:
     * Beleg muss VALIDIERT sein, Kostenstelle muss als Fixkosten markiert sein,
     * und der Streckungs-Zeitraum muss das Jahr abdecken. Wird vom
     * Verrechnungslohn-Rechner verwendet — ersetzt findAll() + Java-Filter,
     * das mit wachsender Beleg-Tabelle nicht skaliert.
     *
     * <p>Semantik exakt wie {@link BelegKostenstellenAnteil#isStreckungAktivFuerJahr(int)}:</p>
     * <ul>
     *   <li>Nicht gestreckt (streckungJahre &le; 1): matched <strong>nur</strong> wenn
     *       {@code streckungStartJahr == :jahr} oder {@code streckungStartJahr IS NULL}
     *       (NULL = "kein konkretes Jahr gesetzt" = unbeschraenkt, Altdaten-Fall).</li>
     *   <li>Gestreckt (streckungJahre &gt; 1): matched fuer Jahre im Intervall
     *       {@code [startJahr, startJahr+jahre)}.</li>
     * </ul>
     * <p>Wichtig: ohne den Year-Match wuerde ein 2024er Bar-Beleg auch in 2025
     * und 2026 wieder in den Gemeinkostentopf einfliessen — Verrechnungslohn-
     * Verfaelschung.</p>
     */
    @Query("SELECT a FROM BelegKostenstellenAnteil a "
            + "JOIN FETCH a.kostenstelle ks "
            + "JOIN FETCH a.beleg b "
            + "WHERE b.status = org.example.kalkulationsprogramm.domain.BelegStatus.VALIDIERT "
            + "AND ks.istFixkosten = true "
            + "AND ("
            + "  ((a.streckungJahre IS NULL OR a.streckungJahre <= 1) "
            + "   AND (a.streckungStartJahr IS NULL OR a.streckungStartJahr = :jahr))"
            + "  OR "
            + "  (a.streckungJahre > 1 AND a.streckungStartJahr IS NOT NULL "
            + "   AND :jahr >= a.streckungStartJahr "
            + "   AND :jahr < a.streckungStartJahr + a.streckungJahre)"
            + ")")
    List<BelegKostenstellenAnteil> findAktiveFixkostenAnteileImJahr(@Param("jahr") int jahr);
}
