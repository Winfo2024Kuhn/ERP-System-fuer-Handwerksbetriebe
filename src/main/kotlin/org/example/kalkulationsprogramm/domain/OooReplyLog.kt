package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table
open class OooReplyLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(name = "schedule_id", nullable = false)
    open var scheduleId: Long? = null

    @Column(name = "sender_address", nullable = false, length = 320)
    open var senderAddress: String? = null

    @Column(name = "replied_at", nullable = false)
    open var repliedAt: LocalDateTime? = null

}