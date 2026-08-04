package org.example.kalkulationsprogramm.repository;

import jakarta.persistence.LockModeType;
import org.example.kalkulationsprogramm.domain.BelegAuditChainState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
// Kein Insert-only-Fall: der Kettenkopf ist genau eine Zeile, die bei jedem
// Anhaengen fortgeschrieben wird. Die Unveraenderbarkeit liegt in
// beleg_audit, nicht hier.

/**
 * Repository fuer den Kettenkopf der Beleg-Hash-Kette.
 *
 * <p>Angehaengt wird ausschliesslich ueber {@link #lockState()}: das nimmt
 * einen pessimistischen Row-Lock (SELECT ... FOR UPDATE) auf id=1 und muss
 * innerhalb einer Transaktion laufen. Ohne den Lock koennten zwei
 * gleichzeitige Buchungen denselben Kettenplatz oder dieselbe laufende
 * Nummer ziehen.</p>
 */
@Repository
public interface BelegAuditChainStateRepository extends JpaRepository<BelegAuditChainState, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM BelegAuditChainState s WHERE s.id = 1")
    BelegAuditChainState lockState();
}
