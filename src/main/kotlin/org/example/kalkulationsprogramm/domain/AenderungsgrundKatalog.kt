package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*

@Entity
@Table(name = "aenderungsgrund_katalog")
open class AenderungsgrundKatalog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(nullable = false, unique = true, length = 50)
    open var code: String? = null

    @Column(nullable = false)
    open var bezeichnung: String? = null

    @Column(name = "erfordert_freitext")
    open var erfordertFreitext: Boolean = false

}