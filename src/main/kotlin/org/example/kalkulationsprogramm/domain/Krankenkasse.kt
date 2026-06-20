package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "krankenkasse")
open class Krankenkasse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(nullable = false, unique = true)
    open var name: String? = null

    @Column(length = 32)
    open var kuerzel: String? = null

    @Column(name = "zusatzbeitrag_prozent", nullable = false, precision = 5, scale = 2)
    open var zusatzbeitragProzent: BigDecimal? = null

    @Column(nullable = false)
    open var aktiv: Boolean = true

    @Column(name = "gueltig_ab")
    open var gueltigAb: LocalDate? = null

    @Column(length = 500)
    open var bemerkung: String? = null

}