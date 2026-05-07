package org.example.kalkulationsprogramm.repository;

import jakarta.persistence.LockModeType;
import org.example.kalkulationsprogramm.domain.AuditChainState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository für den Singleton-State der Audit-Hash-Kette.
 * Atomares Anhängen erfolgt ausschließlich über {@link #lockState()}, das einen
 * pessimistischen Row-Lock (SELECT ... FOR UPDATE) auf id=1 nimmt.
 */
@Repository
public interface AuditChainStateRepository extends JpaRepository<AuditChainState, Integer> {

    /**
     * Holt den Singleton-State unter pessimistischem Lock. Muss innerhalb einer
     * Transaktion aufgerufen werden — der Lock wird beim Commit/Rollback freigegeben.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AuditChainState s WHERE s.id = 1")
    AuditChainState lockState();
}
