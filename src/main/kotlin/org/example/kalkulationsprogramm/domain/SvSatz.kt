package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "sv_satz")
open class SvSatz {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "satz_typ", nullable = false)
    open var satzTyp: SvSatzTyp? = null

    @Column(nullable = false, precision = 5, scale = 2)
    open var prozent: BigDecimal? = null

    @Column(name = "gueltig_ab", nullable = false)
    open var gueltigAb: LocalDate? = null

    @Column(length = 500)
    open var beschreibung: String? = null

}