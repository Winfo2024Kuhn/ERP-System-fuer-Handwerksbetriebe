package org.example.kalkulationsprogramm.service.einkauf;

import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.EinkaufVersandAngenommen;

import java.util.Set;

/**
 * Applies the local business transition for an accepted purchasing email. Implementations must be idempotent by
 * {@code ereignisSchluessel} and perform database changes in the caller's transaction; external I/O is not allowed.
 */
public interface EinkaufAnnahmeereignisConsumer {
    /** Purchasing document types owned by this consumer; unrelated pending events are never claimed or acknowledged. */
    Set<String> unterstuetzteVorgangstypen();

    /** Runs in the outbox transaction. Persist business writes idempotently using {@code ereignisSchluessel}; do not use REQUIRES_NEW or external I/O. */
    void verarbeite(EinkaufVersandAngenommen ereignis);
}
