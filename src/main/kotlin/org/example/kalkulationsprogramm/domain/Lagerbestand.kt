package org.example.kalkulationsprogramm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "lagerbestand",
    uniqueConstraints = [UniqueConstraint(name = "uk_lagerbestand_artikel_lagerort", columnNames = ["artikel_id", "lagerort_id"])],
)
open class Lagerbestand {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artikel_id", nullable = false)
    open var artikel: Artikel? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lagerort_id", nullable = false)
    open var lagerort: Lagerort? = null

    @Column(nullable = false, precision = 19, scale = 4)
    open var menge: BigDecimal = BigDecimal.ZERO

    @Column(nullable = false, precision = 19, scale = 4)
    open var mindestbestand: BigDecimal = BigDecimal.ZERO

    @Column(length = 60)
    open var charge: String? = null

    @Column(length = 255)
    open var bemerkung: String? = null

    @Column(nullable = false)
    open var aktualisiertAm: LocalDateTime = LocalDateTime.now()
}
