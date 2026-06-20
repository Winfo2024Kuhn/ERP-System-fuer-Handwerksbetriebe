package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*

@Entity
@Table(name = "spam_model_stats")
open class SpamModelStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(nullable = false, unique = true, length = 50)
    open var statKey: String? = null

    @Column(nullable = false)
    open var statValue: Long = 0

}