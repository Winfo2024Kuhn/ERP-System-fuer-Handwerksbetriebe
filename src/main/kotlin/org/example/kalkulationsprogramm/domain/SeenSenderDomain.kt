package org.example.kalkulationsprogramm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "seen_sender_domain")
open class SeenSenderDomain {
    @Id
    @Column(nullable = false, length = 255)
    open var domain: String? = null

    @Column(name = "first_seen", nullable = false)
    open var firstSeen: LocalDateTime? = null

    @Column(name = "email_count", nullable = false)
    open var emailCount: Int = 1

}