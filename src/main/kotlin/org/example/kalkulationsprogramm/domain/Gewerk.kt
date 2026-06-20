package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "gewerk")
open class Gewerk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(nullable = false, unique = true)
    open var name: String? = null

    @Column(name = "bg_name", nullable = false)
    open var bgName: String? = null

    @Column(name = "bg_satz_prozent", nullable = false, precision = 5, scale = 2)
    open var bgSatzProzent: BigDecimal? = null

    @Column(nullable = false)
    open var aktiv: Boolean = true

    @Column(length = 500)
    open var bemerkung: String? = null

}