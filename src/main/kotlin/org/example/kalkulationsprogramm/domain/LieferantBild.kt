package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
open class LieferantBild {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id", nullable = false)
    open var lieferant: Lieferanten? = null

    @Column(nullable = false)
    open var originalDateiname: String? = null

    @Column(nullable = false)
    open var gespeicherterDateiname: String? = null

    open var beschreibung: String? = null

    @Column(name = "erstellt_am")
    open var erstelltAm: LocalDateTime? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mitarbeiter_id")
    open var hochgeladenVon: Mitarbeiter? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reklamation_id")
    open var reklamation: LieferantReklamation? = null

}