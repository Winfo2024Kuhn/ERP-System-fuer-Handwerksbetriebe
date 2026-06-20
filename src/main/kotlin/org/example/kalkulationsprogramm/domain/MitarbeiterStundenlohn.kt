package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "mitarbeiter_stundenlohn")
open class MitarbeiterStundenlohn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mitarbeiter_id", nullable = false)
    open var mitarbeiter: Mitarbeiter? = null

    @Column(nullable = false, precision = 10, scale = 2)
    open var stundenlohn: BigDecimal? = null

    @Column(name = "gueltig_ab", nullable = false)
    open var gueltigAb: LocalDate? = null

    @Column(length = 500)
    open var bemerkung: String? = null

}