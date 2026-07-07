package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*
import java.util.Date

@Entity
open class Lieferanten {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(nullable = false, unique = true)
    open var lieferantenname: String? = null

    open var istAktiv: Boolean? = null

    open var startZusammenarbeit: Date? = null

    open var ort: String? = null

    open var strasse: String? = null

    open var plz: String? = null

    open var telefon: String? = null

    open var mobiltelefon: String? = null

    open var vertreter: String? = null

    open var lieferantenTyp: String? = null

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "lieferanten_rollen", joinColumns = [JoinColumn(name = "lieferant_id")])
    @Column(name = "rolle")
    @Enumerated(EnumType.STRING)
    open var rollen: MutableSet<LieferantRolle> = mutableSetOf()

    @Column(columnDefinition = "integer default 0")
    open var bestellungen: Int? = 0

    open var eigeneKundennummer: String? = null

    @ElementCollection
    @CollectionTable(name = "lieferanten_emails")
    @Column(name = "email")
    open var kundenEmails: MutableList<String> = mutableListOf()

    @OneToMany(mappedBy = "lieferant", cascade = arrayOf(CascadeType.ALL), orphanRemoval = true)
    open var artikelpreise: MutableList<LieferantenArtikelPreise> = mutableListOf()

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "standard_kostenstelle_id")
    open var standardKostenstelle: Kostenstelle? = null

}
