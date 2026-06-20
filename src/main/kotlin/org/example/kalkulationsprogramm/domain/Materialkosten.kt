package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
open class Materialkosten {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projekt_id")
    open var projekt: Projekt? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id")
    open var lieferant: Lieferanten? = null

    @Column
    open var beschreibung: String? = null

    @Column
    open var externeArtikelnummer: String? = null

    @Column
    open var monat: Int? = null

    @Column(nullable = false, precision = 19, scale = 2)
    open var betrag: BigDecimal = BigDecimal.ZERO

    @Column
    open var rechnungsnummer: String? = null

}