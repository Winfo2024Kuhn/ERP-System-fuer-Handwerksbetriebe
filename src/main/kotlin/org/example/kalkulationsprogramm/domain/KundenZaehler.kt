package org.example.kalkulationsprogramm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "kunden_zaehler")
open class KundenZaehler {
    @Id
    open var id: Int? = null

    @Column(name = "naechste_nummer", nullable = false)
    open var naechsteNummer: Long? = null

}