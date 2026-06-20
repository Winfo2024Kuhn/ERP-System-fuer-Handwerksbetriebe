package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table
open class EmailBlacklistEntry() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(nullable = false, unique = true)
    open var emailAddress: String? = null

    open var blockedAt: LocalDateTime? = null

    open var blockedBy: String? = null

    constructor(emailAddress: String?) : this() {
        this.emailAddress = emailAddress?.lowercase()
        this.blockedAt = LocalDateTime.now()
    }

}