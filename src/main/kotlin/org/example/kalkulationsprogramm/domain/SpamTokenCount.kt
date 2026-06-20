package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*

@Entity
@Table
open class SpamTokenCount() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(nullable = false, unique = true, length = 100)
    open var token: String? = null

    @Column(nullable = false)
    open var spamCount: Int = 0

    @Column(nullable = false)
    open var hamCount: Int = 0

    constructor(token: String?) : this() {
        this.token = token
    }

}