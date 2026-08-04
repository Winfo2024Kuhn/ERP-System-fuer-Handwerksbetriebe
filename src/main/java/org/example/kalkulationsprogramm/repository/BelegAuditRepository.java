package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.BelegAudit;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Zugriff auf das Kassenbuch-Protokoll: lesen und anhaengen, sonst nichts.
 *
 * <p>Bewusst {@link Repository} statt {@code JpaRepository}: Letzteres brachte
 * {@code delete}, {@code deleteById}, {@code deleteAll} und
 * {@code deleteAllInBatch} frei Haus mit. Die Verfahrensdokumentation sagt
 * dem Steuerpruefer zu, dass es in der Anwendung keinen Weg gibt, einen
 * Protokolleintrag zu loeschen -- diese Zusage muss der Code auch halten.
 * Wer hier eine Loesch-Methode ergaenzt, macht die Verfahrensdokumentation
 * unwahr.</p>
 *
 * <p>{@code saveAndFlush} bleibt, weil das Anhaengen an die Kette den
 * Eintrag zweimal beruehrt: einmal beim Insert, einmal um den erst danach
 * berechenbaren {@code entry_hash} nachzutragen. Siehe
 * {@code BelegAuditService.appendToChain}.</p>
 */
public interface BelegAuditRepository extends Repository<BelegAudit, Long> {

    /** Legt einen neuen Eintrag an bzw. traegt den entry_hash nach. */
    BelegAudit saveAndFlush(BelegAudit eintrag);

    /** Ganze Kette in Reihenfolge -- Grundlage der Verifikation. */
    List<BelegAudit> findAllByOrderByChainIndexAsc();

    /** Verlauf eines einzelnen Belegs, neueste Aktion zuerst. */
    List<BelegAudit> findByBelegIdOrderByGeaendertAmDesc(Long belegId);

    /** Ausschnitt der Kette fuer den Zeitraum-Export an den Steuerberater. */
    List<BelegAudit> findByGeaendertAmBetweenOrderByChainIndexAsc(LocalDateTime von, LocalDateTime bis);

    /**
     * Letzter Eintrag der Kette. Wird gebraucht, um den Kettenkopf nach einem
     * Neustart gegen den tatsaechlichen Tabelleninhalt gegenzupruefen.
     */
    @Query("SELECT a FROM BelegAudit a WHERE a.chainIndex = (SELECT MAX(x.chainIndex) FROM BelegAudit x)")
    BelegAudit findLetztenEintrag();

    long countByBelegId(@Param("belegId") Long belegId);
}
