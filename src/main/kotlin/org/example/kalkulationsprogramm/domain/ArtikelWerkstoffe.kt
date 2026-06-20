package org.example.kalkulationsprogramm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import java.math.BigDecimal

@Entity
open class ArtikelWerkstoffe : Artikel() {
    @Column(name = "masse_pro_meter")
    open var masse: BigDecimal? = null

    open var mantelflaeche: BigDecimal? = null

    open var geschliffen: Boolean = false

    open var hoehe: Int = 0

    open var breite: Int = 0

}