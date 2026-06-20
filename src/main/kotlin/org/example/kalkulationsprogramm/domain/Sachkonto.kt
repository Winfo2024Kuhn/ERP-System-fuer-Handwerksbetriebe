package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*

@Entity
@Table(name = "sachkonto")
open class Sachkonto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(length = 20)
    open var nummer: String? = null

    @Column(nullable = false, length = 120, unique = true)
    open var bezeichnung: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "konto_typ", nullable = false, length = 20)
    open var kontoTyp: SachkontoTyp? = null

    @Column(length = 500)
    open var beschreibung: String? = null

    @Column(nullable = false)
    open var aktiv: Boolean = true

    @Column(nullable = false)
    open var sortierung: Int = 0

    open fun isAktiv(): Boolean = aktiv

}
