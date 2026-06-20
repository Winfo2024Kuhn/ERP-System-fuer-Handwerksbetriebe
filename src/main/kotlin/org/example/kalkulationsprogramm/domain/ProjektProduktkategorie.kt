package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
open class ProjektProduktkategorie {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projekt_id")
    open var projekt: Projekt? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produktkategorie_id")
    open var produktkategorie: Produktkategorie? = null

    @Column(nullable = false, precision = 19, scale = 2)
    open var menge: BigDecimal = BigDecimal.ZERO

}