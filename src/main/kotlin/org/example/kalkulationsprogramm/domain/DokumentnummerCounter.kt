package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*

@Entity
@Table
open class DokumentnummerCounter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(name = "month_key", nullable = false, length = 10)
    open var monthKey: String? = null

    @Column(name = "counter", nullable = false)
    open var counter: Long? = null

}