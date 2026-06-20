package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*

@Entity
open class Arbeitsgang {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(nullable = false, unique = true)
    open var beschreibung: String? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "abteilung_id", nullable = false)
    open var abteilung: Abteilung? = null

    @OneToMany(mappedBy = "arbeitsgang", cascade = [CascadeType.ALL], orphanRemoval = true)
    open var ZeitbuchungList: MutableList<Zeitbuchung> = ArrayList()

    @OneToMany(mappedBy = "arbeitsgang", cascade = [CascadeType.ALL], orphanRemoval = true)
    open var stundensaetze: MutableList<ArbeitsgangStundensatz> = ArrayList()

}