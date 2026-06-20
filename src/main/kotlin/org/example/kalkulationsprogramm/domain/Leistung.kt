package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
open class Leistung {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(nullable = false)
    open var bezeichnung: String? = null

    @Column(columnDefinition = "TEXT")
    open var beschreibung: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    open var einheit: Verrechnungseinheit? = null

    @Column(precision = 19, scale = 2)
    open var preis: BigDecimal? = null

    @ManyToOne(fetch = FetchType.LAZY)
    open var kategorie: Produktkategorie? = null

}