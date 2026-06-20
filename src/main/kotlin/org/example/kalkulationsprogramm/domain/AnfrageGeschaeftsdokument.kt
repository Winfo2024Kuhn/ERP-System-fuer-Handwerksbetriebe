package org.example.kalkulationsprogramm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "anfrage_geschaeftsdokument")
open class AnfrageGeschaeftsdokument : AnfrageDokument() {
    @Column(nullable = false)
    open var dokumentid: String? = null

    @Column(nullable = false)
    open var geschaeftsdokumentart: String? = null

    open var bruttoBetrag: BigDecimal? = null

}