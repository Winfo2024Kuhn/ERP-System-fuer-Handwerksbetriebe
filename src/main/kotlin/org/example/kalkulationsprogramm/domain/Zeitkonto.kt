package org.example.kalkulationsprogramm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import java.math.BigDecimal
import java.time.LocalTime

@Entity
open class Zeitkonto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @OneToOne
    @JoinColumn(name = "mitarbeiter_id", nullable = false, unique = true)
    open var mitarbeiter: Mitarbeiter? = null

    @Column(precision = 4, scale = 2)
    open var montagStunden: BigDecimal? = BigDecimal("8.00")

    @Column(precision = 4, scale = 2)
    open var dienstagStunden: BigDecimal? = BigDecimal("8.00")

    @Column(precision = 4, scale = 2)
    open var mittwochStunden: BigDecimal? = BigDecimal("8.00")

    @Column(precision = 4, scale = 2)
    open var donnerstagStunden: BigDecimal? = BigDecimal("8.00")

    @Column(precision = 4, scale = 2)
    open var freitagStunden: BigDecimal? = BigDecimal("8.00")

    @Column(precision = 4, scale = 2)
    open var samstagStunden: BigDecimal? = BigDecimal("0.00")

    @Column(precision = 4, scale = 2)
    open var sonntagStunden: BigDecimal? = BigDecimal("0.00")

    @Column
    open var buchungStartZeit: LocalTime? = LocalTime.of(5, 0)

    @Column
    open var buchungEndeZeit: LocalTime? = LocalTime.of(20, 0)

    constructor()

    constructor(mitarbeiter: Mitarbeiter?) {
        this.mitarbeiter = mitarbeiter
    }

    open fun getWochenstunden(): BigDecimal =
        (montagStunden ?: BigDecimal.ZERO)
            .add(dienstagStunden ?: BigDecimal.ZERO)
            .add(mittwochStunden ?: BigDecimal.ZERO)
            .add(donnerstagStunden ?: BigDecimal.ZERO)
            .add(freitagStunden ?: BigDecimal.ZERO)
            .add(samstagStunden ?: BigDecimal.ZERO)
            .add(sonntagStunden ?: BigDecimal.ZERO)

    open fun getSollstundenFuerTag(dayOfWeek: Int): BigDecimal =
        when (dayOfWeek) {
            1 -> montagStunden
            2 -> dienstagStunden
            3 -> mittwochStunden
            4 -> donnerstagStunden
            5 -> freitagStunden
            6 -> samstagStunden
            7 -> sonntagStunden
            else -> BigDecimal.ZERO
        } ?: BigDecimal.ZERO
}
