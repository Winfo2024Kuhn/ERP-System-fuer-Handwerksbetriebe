package org.example.kalkulationsprogramm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "lagerbewegung")
open class Lagerbewegung {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artikel_id", nullable = false)
    open var artikel: Artikel? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "von_lagerort_id")
    open var vonLagerort: Lagerort? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nach_lagerort_id")
    open var nachLagerort: Lagerort? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    open var typ: LagerbewegungTyp = LagerbewegungTyp.KORREKTUR

    @Column(nullable = false, precision = 19, scale = 4)
    open var menge: BigDecimal = BigDecimal.ZERO

    @Column(length = 255)
    open var grund: String? = null

    @Column(length = 120)
    open var referenz: String? = null

    @Column(length = 120)
    open var verantwortlicher: String? = null

    @Column(nullable = false)
    open var erstelltAm: LocalDateTime = LocalDateTime.now()
}
