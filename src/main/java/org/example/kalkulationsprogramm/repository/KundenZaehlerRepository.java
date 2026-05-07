package org.example.kalkulationsprogramm.repository;

import jakarta.persistence.LockModeType;
import org.example.kalkulationsprogramm.domain.KundenZaehler;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface KundenZaehlerRepository extends JpaRepository<KundenZaehler, Integer> {

    /**
     * Liefert den Singleton-Counter (id=1) mit row-level PESSIMISTIC_WRITE-Lock.
     * Konkurrierende Aufrufer warten, bis der lockende Aufruf seine Transaktion
     * abschließt (Commit oder Rollback). Bei Rollback wird die Inkrementierung
     * mit zurückgenommen → keine Lücken, keine Doppelung.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT k FROM KundenZaehler k WHERE k.id = 1")
    KundenZaehler lockAndGet();
}
