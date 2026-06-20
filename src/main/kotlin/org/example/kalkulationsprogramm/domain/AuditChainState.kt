package org.example.kalkulationsprogramm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "audit_chain_state")
open class AuditChainState {
    @Id
    @Column(name = "id")
    open var id: Int? = null

    @Column(name = "last_chain_index", nullable = false)
    open var lastChainIndex: Long? = null

    @Column(name = "last_entry_hash", columnDefinition = "CHAR(64)")
    open var lastEntryHash: String? = null

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: LocalDateTime? = null

}