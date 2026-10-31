package org.example.kalkulationsprogramm.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.example.kalkulationsprogramm.domain.ids.IdsVorgang;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdsVorgangRepository extends JpaRepository<IdsVorgang, Long> {

    /**
     * Mit Schreibsperre: Schickt der Shop denselben Warenkorb zweimal gleichzeitig (Doppelklick, Wiederholung),
     * wartet der zweite Rücksprung, bis der erste fertig ist, und sieht dann „schon eingelöst“.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM IdsVorgang v WHERE v.schluesselHash = :hash")
    Optional<IdsVorgang> findBySchluesselHashGesperrt(@Param("hash") String schluesselHash);

    /** Shop-Besuch samt Lieferant und Warenkorb in einer Abfrage. */
    @Query("SELECT DISTINCT v FROM IdsVorgang v JOIN FETCH v.lieferant LEFT JOIN FETCH v.positionen WHERE v.id = :id")
    Optional<IdsVorgang> findMitWarenkorb(@Param("id") Long id);

    /** Warenkorb-Zeilen alter Shop-Besuche; vor {@link #loescheAelterAls} aufrufen. */
    @Modifying
    @Query("DELETE FROM IdsWarenkorbPosition p WHERE p.vorgang.id IN "
            + "(SELECT v.id FROM IdsVorgang v WHERE v.erstelltAm < :grenze)")
    int loeschePositionenAelterAls(@Param("grenze") LocalDateTime grenze);

    /** Alte Shop-Besuche wegräumen. */
    @Modifying
    @Query("DELETE FROM IdsVorgang v WHERE v.erstelltAm < :grenze")
    int loescheAelterAls(@Param("grenze") LocalDateTime grenze);
}
