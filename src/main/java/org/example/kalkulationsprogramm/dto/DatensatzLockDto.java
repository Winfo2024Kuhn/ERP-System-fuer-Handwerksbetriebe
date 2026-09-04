package org.example.kalkulationsprogramm.dto;

import java.time.LocalDateTime;

/**
 * Antwort-DTO fuer den Datensatz-Lock-Endpoint.
 *
 * status:
 *   ACQUIRED        - Der Caller haelt jetzt das Lock und darf bearbeiten.
 *   LOCKED_BY_OTHER - Ein anderer User haelt das Lock noch.
 */
public record DatensatzLockDto(
        String status,
        Long holderUserId,
        String holderDisplayName,
        LocalDateTime acquiredAt,
        LocalDateTime lastHeartbeatAt
) {
    public static final String ACQUIRED = "ACQUIRED";
    public static final String LOCKED_BY_OTHER = "LOCKED_BY_OTHER";
}
