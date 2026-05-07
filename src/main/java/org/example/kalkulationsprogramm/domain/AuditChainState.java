package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Singleton-Tabelle (id = 1), die den Kopf der Audit-Hash-Kette hält.
 *
 * <p>Wird beim Anhängen eines neuen Audit-Eintrags per {@code SELECT ... FOR UPDATE}
 * gelockt, damit zwei parallele Aktionen niemals den gleichen previousHash bekommen
 * (sonst wäre die Kette ein Baum und nicht eine Linie).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "audit_chain_state")
public class AuditChainState {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "last_chain_index", nullable = false)
    private Long lastChainIndex;

    @Column(name = "last_entry_hash", columnDefinition = "CHAR(64)")
    private String lastEntryHash;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
