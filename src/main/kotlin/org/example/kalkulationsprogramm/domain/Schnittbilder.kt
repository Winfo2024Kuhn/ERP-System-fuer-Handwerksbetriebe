package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*

@Entity
open class Schnittbilder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long = 0L

    @Column(nullable = false, unique = true)
    open var bildUrlSchnittbild: String? = null

    @Column(nullable = false, unique = true)
    open var form: String? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    open var kategorie: Kategorie? = null

}