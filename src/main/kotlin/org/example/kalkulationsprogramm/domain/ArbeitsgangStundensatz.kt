package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
open class ArbeitsgangStundensatz {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "arbeitsgang_id")
    open var arbeitsgang: Arbeitsgang? = null

    open var jahr: Int = 0

    @Column(precision = 10, scale = 2, nullable = false)
    open var satz: BigDecimal? = null

}