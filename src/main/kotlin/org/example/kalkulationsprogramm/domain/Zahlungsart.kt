package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*

@Entity
@Table(name = "zahlungsart")
open class Zahlungsart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(nullable = false, length = 60, unique = true)
    open var bezeichnung: String? = null

    @Column(nullable = false)
    open var aktiv: Boolean = true

    @Column(nullable = false)
    open var sortierung: Int = 0

    open fun isAktiv(): Boolean = aktiv

}
